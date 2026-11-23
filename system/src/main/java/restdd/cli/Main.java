package restdd.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import restdd.dsl.StaticValidator;
import restdd.dsl.SuiteSchema;
import restdd.llm.ChatResult;
import restdd.llm.GenerationOptions;
import restdd.llm.OllamaClient;
import restdd.probe.ProbeMain;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;
import restdd.spec.SpecSanitiser;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command-line entry point.
 * <pre>
 *   normalise --spec FILE [--out DIR]     slices + aggregate statistics (no operation names printed)
 *   sanitise  --spec FILE                 D5.1 keyword hits for the owner to review
 *   llm-check --spec FILE --model NAME [--num-ctx N] [--ollama URL]
 *                                         GPU preflight + one structured call on the first operation
 *   probe ...                             the Phase 1 probe (unchanged)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: normalise | sanitise | llm-check | probe  (see class javadoc)");
            System.exit(2);
        }
        String cmd = args[0];
        Map<String, String> a = parse(Arrays.copyOfRange(args, 1, args.length));
        switch (cmd) {
            case "normalise" -> normalise(a);
            case "sanitise" -> sanitise(a);
            case "llm-check" -> llmCheck(a);
            case "probe" -> ProbeMain.main(Arrays.copyOfRange(args, 1, args.length));
            default -> {
                System.err.println("unknown command " + cmd);
                System.exit(2);
            }
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                m.put(args[i].substring(2), i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true");
            }
        }
        return m;
    }

    private static String need(Map<String, String> a, String k) {
        String v = a.get(k);
        if (v == null) {
            System.err.println("missing --" + k);
            System.exit(2);
        }
        return v;
    }

    private static void normalise(Map<String, String> a) throws Exception {
        NormalisedSpec s = SpecNormaliser.normalise(Path.of(need(a, "spec")));
        int[] t = s.operations().stream().mapToInt(OperationSlice::estimatedTokens).sorted().toArray();
        System.out.printf("%s | sha256 %s | operations %d | defaultValue mapped %d | recursive cuts %d | unresolved refs %d%n",
                s.declaredVersion(), s.sourceSha256().substring(0, 16), t.length, s.defaultValueMapped(),
                s.recursiveCuts(), s.unresolvedRefs());
        if (t.length > 0) {
            System.out.printf("estimated slice tokens: median %d | p95 %d | max %d | over 2000: %d%n", t[t.length / 2],
                    t[(int) (0.95 * (t.length - 1))], t[t.length - 1], Arrays.stream(t).filter(x -> x > 2000).count());
        }
        s.messages().forEach(m -> System.out.println("message: " + m));
        if (a.containsKey("out")) {
            Path out = Path.of(a.get("out"));
            Files.createDirectories(out);
            StringBuilder sb = new StringBuilder();
            for (OperationSlice o : s.operations()) {
                sb.append(o.text()).append('\n');
            }
            Files.writeString(out.resolve("slices.jsonl"), sb.toString());
            System.out.println("wrote " + out.resolve("slices.jsonl"));
        }
    }

    private static void sanitise(Map<String, String> a) throws Exception {
        List<SpecSanitiser.Hit> hits = SpecSanitiser.scan(Path.of(need(a, "spec")));
        hits.forEach(h -> System.out.println(h.pointer() + " | " + h.term() + " | " + h.snippet()));
        System.out.println(hits.size() + " hit(s)");
    }

    private static void llmCheck(Map<String, String> a) throws Exception {
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(need(a, "spec")));
        String model = need(a, "model");
        int numCtx = Integer.parseInt(a.getOrDefault("num-ctx", "8192"));
        GenerationOptions o = new GenerationOptions(model, numCtx, 2048, 0.7, 0.8, 20, 1.0, null, "30m");
        Path work = Path.of(a.getOrDefault("work", "llm-check"));
        OllamaClient c = new OllamaClient(URI.create(a.getOrDefault("ollama", "http://127.0.0.1:11434")),
                work.resolve("cache"), work.resolve("calls.jsonl"), Duration.ofMinutes(10));
        System.out.println("Ollama " + c.version() + " | " + model + " digest " + c.modelDigest(model));
        var ps = c.preflight(o);
        System.out.println("preflight OK: size " + ps.path("size").asLong() + " bytes, all in VRAM, context "
                + ps.path("context_length").asText("n/a"));
        OperationSlice op = spec.operations().get(0);
        String system = "You write REST API tests as JSON in the given format. Output only JSON.";
        String user = "Operation:\n" + op.text() + "\n\nWrite exactly 1 test for this operation.";
        long t0 = System.nanoTime();
        ChatResult r = c.chat(system, user, SuiteSchema.build(1), o, 1, c.modelDigest(model), Map.of("purpose", "llm-check"));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        var report = new StaticValidator(spec, Set.of()).validate(r.content(), 1);
        System.out.printf("call %d ms | prompt %d tokens | output %d tokens | %.1f tok/s | done %s | schema-valid %s%n",
                ms, r.promptEvalCount(), r.evalCount(),
                r.evalNs() > 0 ? r.evalCount() / (r.evalNs() / 1e9) : 0.0, r.doneReason(), report.schemaValid());
        if (!report.tests().isEmpty()) {
            var t = report.tests().get(0);
            System.out.println("structural errors " + t.structuralErrors().size() + " | assertion class " + t.assertionClass());
        }
        System.out.println(new ObjectMapper().readTree(r.content()).toPrettyString());
    }
}
