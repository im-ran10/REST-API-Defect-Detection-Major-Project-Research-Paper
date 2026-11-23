package restdd.cli;

import restdd.dsl.StaticValidator;
import restdd.dsl.SuiteSchema;
import restdd.llm.ChatResult;
import restdd.llm.GenerationOptions;
import restdd.llm.LlmException;
import restdd.llm.OllamaClient;
import restdd.prompt.PromptBuilder;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * G1 (T4, D41/D43): with the real prompt skeleton, find the largest operation prompt over the given specs,
 * pad the task section to the expected longest B/C task size, and for each candidate context window check
 * (1) the model loads fully on the GPU, (2) the largest prompt is processed without truncation, (3) speed.
 * A median-sized prompt is timed as the typical call. Prints aggregates and timings only, never operation names.
 */
final class G1 {

    private G1() {}

    static void run(Map<String, String> a) throws Exception {
        String model = Main.need(a, "model");
        SpecNormaliser.Compaction comp = Main.compaction(a);
        int s = Integer.parseInt(a.getOrDefault("s", "3"));
        int taskTokens = Integer.parseInt(a.getOrDefault("task-tokens", "1200"));
        int numPredict = Integer.parseInt(a.getOrDefault("num-predict", "2048"));
        long projectedCalls = Long.parseLong(a.getOrDefault("projected-calls", "4365"));
        PromptBuilder pb = new PromptBuilder();

        List<NormalisedSpec> specs = new ArrayList<>();
        List<OperationSlice[]> pairs = new ArrayList<>();
        for (String f : Main.need(a, "spec").split(",")) {
            specs.add(SpecNormaliser.normalise(Path.of(f), comp));
        }
        // largest and median operation over all specs
        record Cand(NormalisedSpec spec, OperationSlice op) {}
        List<Cand> all = new ArrayList<>();
        specs.forEach(sp -> sp.operations().forEach(o -> all.add(new Cand(sp, o))));
        all.sort(Comparator.comparingInt(c -> c.op().estimatedTokens()));
        Cand largest = all.get(all.size() - 1);
        Cand median = all.get(all.size() / 2);
        String task = padTask(PromptBuilder.armATask(s), taskTokens);
        String shapes = "{\"placeholder\": \"exploration shapes, about 300 tokens\"}".repeat(1);
        String bigUser = pb.user(largest.spec(), largest.op(), shapes + " ".repeat(1000), List.of(), task);
        String medUser = pb.user(median.spec(), median.op(), shapes, List.of(), task);
        System.out.printf("compaction %s | s %d | task padded to ~%d tokens | system prompt %d chars%n", comp, s,
                taskTokens, pb.system().length());
        System.out.printf("largest prompt: %d chars (slice est. %d tokens) | median prompt: %d chars%n",
                pb.system().length() + bigUser.length(), largest.op().estimatedTokens(),
                pb.system().length() + medUser.length());

        Path work = Path.of(a.getOrDefault("work", "g1-" + Instant.now().getEpochSecond()));
        URI ollama = URI.create(a.getOrDefault("ollama", "http://127.0.0.1:11434"));
        for (String ctxText : Main.need(a, "num-ctx").split(",")) {
            int ctx = Integer.parseInt(ctxText.trim());
            GenerationOptions o = new GenerationOptions(model, ctx, numPredict, 0.7, 0.8, 20, 1.0, null, "30m");
            OllamaClient c = new OllamaClient(ollama, work.resolve("ctx" + ctx + "/cache"),
                    work.resolve("ctx" + ctx + "/calls.jsonl"), Duration.ofMinutes(15));
            OllamaClient again = new OllamaClient(ollama, work.resolve("ctx" + ctx + "/cache-repeat"),
                    work.resolve("ctx" + ctx + "/calls.jsonl"), Duration.ofMinutes(15));
            String digest = c.modelDigest(model);
            System.out.printf("%n== num_ctx %d ==%n", ctx);
            try {
                var ps = c.preflight(o);
                System.out.printf("GPU preflight OK: %.2f GB, all in VRAM%n", ps.path("size").asLong() / 1e9);
            } catch (LlmException e) {
                System.out.println("GPU preflight FAILED: " + e.getMessage());
                continue;
            }
            try {
                report("largest, seed 1", c.chat(pb.system(), bigUser, SuiteSchema.build(s), o, 1, digest, Map.of("g1", "largest")),
                        largest.spec(), s, pb.system().length() + bigUser.length());
                ChatResult r2 = again.chat(pb.system(), bigUser, SuiteSchema.build(s), o, 1, digest, Map.of("g1", "largest-repeat"));
                ChatResult r1 = c.chat(pb.system(), bigUser, SuiteSchema.build(s), o, 1, digest, Map.of("g1", "largest"));
                System.out.println("same seed, same output: " + r1.content().equals(r2.content()));
            } catch (LlmException e) {
                System.out.println("largest prompt FAILED: " + e.getMessage());
            }
            double sumMs = 0;
            int n = 0;
            for (long seed = 1; seed <= 3; seed++) {
                try {
                    ChatResult r = c.chat(pb.system(), medUser, SuiteSchema.build(s), o, seed, digest, Map.of("g1", "median"));
                    report("median, seed " + seed, r, median.spec(), s, pb.system().length() + medUser.length());
                    sumMs += r.totalNs() / 1e6;
                    n++;
                } catch (LlmException e) {
                    System.out.println("median prompt FAILED: " + e.getMessage());
                }
            }
            if (n > 0) {
                double hours = projectedCalls * (sumMs / n) / 3.6e6;
                System.out.printf("projection: %d calls x %.1f s (median prompt) = %.1f h, +15%% = %.1f h (40-hour rule)%n",
                        projectedCalls, sumMs / n / 1000, hours, hours * 1.15);
            }
        }
        System.out.println("\nwork folder (cache, call log): " + work.toAbsolutePath());
    }

    private static void report(String label, ChatResult r, NormalisedSpec spec, int s, int promptChars) {
        var v = new StaticValidator(spec, Set.of()).validate(r.content(), s);
        long valid = v.tests().stream().filter(StaticValidator.TestReport::structurallyValid).count();
        long meaningful = v.tests().stream().filter(t -> t.assertionClass() == StaticValidator.AssertionClass.MEANINGFUL).count();
        double pp = r.promptEvalNs() > 0 ? r.promptEvalCount() / (r.promptEvalNs() / 1e9) : 0;
        double gen = r.evalNs() > 0 ? r.evalCount() / (r.evalNs() / 1e9) : 0;
        System.out.printf("%-16s %6.1f s | prompt %5d tok (%.2f chars/tok) at %6.0f tok/s | output %4d tok at %4.1f tok/s | "
                        + "done %s | schema-valid %s | runnable %d/%d | meaningful %d/%d%n",
                label, r.totalNs() / 1e9, r.promptEvalCount(), r.promptEvalCount() > 0 ? (double) promptChars / r.promptEvalCount() : 0,
                pp, r.evalCount(), gen, r.doneReason(), v.schemaValid(), valid, v.tests().size(), meaningful, v.tests().size());
    }

    /** Pads the task section with neutral placeholder strategy text to approximate the longest B/C task. */
    static String padTask(String task, int targetTokens) {
        StringBuilder sb = new StringBuilder(task).append("\n");
        String filler = "Placeholder strategy text for the G1 size test: check that the operation behaves as documented "
                + "and assert on the response content. ";
        int i = 1;
        while (sb.length() < targetTokens * SpecNormaliser.CHARS_PER_TOKEN) {
            sb.append("Note ").append(i++).append(": ").append(filler);
        }
        return sb.toString();
    }
}
