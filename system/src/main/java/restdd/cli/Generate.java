package restdd.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.exec.TargetConfig;
import restdd.gen.GenerationRun;
import restdd.gen.GpuMonitor;
import restdd.gen.OperationSelector;
import restdd.llm.GenerationOptions;
import restdd.llm.OllamaClient;
import restdd.prompt.PromptBuilder;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@code generate}: one generation run for one project (T6 model test; later the held-out runs).
 *
 * <pre>
 * generate --project dolibarr --spec FILE --target FILE --work DIR [--cap 20] [--arms A] [--seeds 1,2] [--s 3]
 *          [--model qwen3:4b-instruct-2507-q4_K_M] [--num-ctx 16384] [--num-predict 2048]
 *          [--temperature 0.7 --top-p 0.8 --top-k 20 --repeat-penalty 1.0] [--compact D41-rule]
 *          [--pause-temp 85 --resume-temp 75] [--shapes FILE] [--list-only]
 * </pre>
 * Defaults are the D44 configuration and the D48 decoding settings. {@code --list-only} prints the D47
 * selection (keys and SHA-256) and writes it to the work folder without calling the model.
 */
final class Generate {

    static final String D41_RULE = "cap=200,drop-examples,drop-schema-descriptions,resp-depth=2";

    private Generate() {}

    static void run(Map<String, String> a) throws Exception {
        String project = Main.need(a, "project");
        Path specFile = Path.of(Main.need(a, "spec"));
        Path work = Path.of(Main.need(a, "work"));
        Files.createDirectories(work);
        a.putIfAbsent("compact", D41_RULE);
        NormalisedSpec full = SpecNormaliser.normalise(specFile);
        NormalisedSpec promptSpec = SpecNormaliser.normalise(specFile, Main.compaction(a));
        int cap = Integer.parseInt(a.getOrDefault("cap", "0"));
        List<OperationSlice> ops = OperationSelector.select(full.operations(), cap);

        String listText = String.join("\n", ops.stream().map(OperationSlice::key).toList()) + "\n";
        Files.writeString(work.resolve("operations.txt"), listText);
        System.out.printf("%s: %d of %d operations selected (cap %s), list SHA-256 %s%n", project, ops.size(),
                full.operations().size(), cap <= 0 ? "none" : cap, OperationSelector.listSha256(ops));
        if (a.containsKey("list-only")) {
            System.out.print(listText);
            return;
        }

        List<String> fixtures = new ArrayList<>();
        if (a.containsKey("target")) {
            TargetConfig t = TargetConfig.load(Path.of(a.get("target")), name -> "unused-for-fixture-names");
            fixtures.addAll(t.exec().fixtures().keySet());
        }
        fixtures.add("uid");
        String shapes = a.containsKey("shapes") ? Files.readString(Path.of(a.get("shapes"))) : "";

        int s = Integer.parseInt(a.getOrDefault("s", "3"));
        List<String> arms = Arrays.asList(a.getOrDefault("arms", "A").split(","));
        for (String arm : arms) {
            if (!arm.equals("A")) {
                throw new IllegalArgumentException("arm " + arm + " needs the strategy catalogues (T8); only A is available");
            }
        }
        List<Long> seeds = Arrays.stream(a.getOrDefault("seeds", "1,2").split(",")).map(x -> Long.parseLong(x.trim())).toList();
        GenerationOptions o = new GenerationOptions(a.getOrDefault("model", "qwen3:4b-instruct-2507-q4_K_M"),
                Integer.parseInt(a.getOrDefault("num-ctx", "16384")), Integer.parseInt(a.getOrDefault("num-predict", "2048")),
                Double.parseDouble(a.getOrDefault("temperature", "0.7")), Double.parseDouble(a.getOrDefault("top-p", "0.8")),
                Integer.parseInt(a.getOrDefault("top-k", "20")), Double.parseDouble(a.getOrDefault("repeat-penalty", "1.0")),
                null, "30m");

        GenerationRun.TaskProvider tasks = (arm, op) -> PromptBuilder.armATask(s);
        GenerationRun.Config config = new GenerationRun.Config(project, promptSpec, full, ops, arms, seeds, s,
                List.copyOf(fixtures), shapes, o, tasks);
        OllamaClient client = new OllamaClient(URI.create(a.getOrDefault("ollama", "http://127.0.0.1:11434")),
                work.resolve("cache"), work.resolve("ollama-calls.jsonl"), Duration.ofMinutes(15));
        PromptBuilder prompts = new PromptBuilder();
        GpuMonitor gpu = new GpuMonitor(Integer.parseInt(a.getOrDefault("pause-temp", "85")),
                Integer.parseInt(a.getOrDefault("resume-temp", "75")));
        GenerationRun runner = new GenerationRun(config, client, prompts, gpu, work);

        ObjectNode manifest = runner.manifest(client.modelDigest(o.model()), client.version());
        manifest.put("specFile", specFile.getFileName().toString());
        manifest.put("compaction", a.get("compact"));
        manifest.put("cap", cap);
        manifest.put("gitCommit", sh("git", "rev-parse", "HEAD"));
        manifest.put("gitDirty", !sh("git", "status", "--porcelain").isBlank());
        manifest.put("ollamaServerEnvironment", sh("systemctl", "--no-pager", "show", "ollama", "-p", "Environment"));
        manifest.put("gpu", sh("nvidia-smi", "--query-gpu=name,driver_version", "--format=csv,noheader"));
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("pauseTempC", Integer.parseInt(a.getOrDefault("pause-temp", "85")));
        manifest.put("resumeTempC", Integer.parseInt(a.getOrDefault("resume-temp", "75")));
        Files.writeString(work.resolve("manifest.json"), manifest.toPrettyString());

        long t0 = System.nanoTime();
        GenerationRun.Summary sum = runner.run();
        long wallMs = (System.nanoTime() - t0) / 1_000_000;
        manifest.put("finishedAt", Instant.now().toString());
        manifest.put("wallMs", wallMs);
        manifest.set("summary", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(sum));
        Files.writeString(work.resolve("manifest.json"), manifest.toPrettyString());

        int fresh = sum.calls() - sum.fromCache();
        System.out.printf("%ncalls %d (cached %d) | schema-valid %d | prompt too long %d | output cut at cap %d | thinking output %d%n",
                sum.calls(), sum.fromCache(), sum.schemaValid(), sum.promptTooLong(), sum.outputTruncated(), sum.thinkingDetected());
        System.out.println("thinking mode: " + GenerationRun.thinkingMode(o));
        System.out.printf("model time %.1f min (%.1f s per fresh call) | GPU pauses %d (%.1f min) | wall %.1f min%n",
                sum.modelMs() / 60000.0, fresh == 0 ? 0 : sum.modelMs() / 1000.0 / fresh, sum.pauses(),
                sum.pausedMs() / 60000.0, wallMs / 60000.0);
        System.out.println("work folder: " + work.toAbsolutePath());
    }

    /** First line of a command's output, or "unavailable". */
    static String sh(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return "unavailable";
            }
            return out;
        } catch (IOException e) {
            return "unavailable";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unavailable";
        }
    }
}
