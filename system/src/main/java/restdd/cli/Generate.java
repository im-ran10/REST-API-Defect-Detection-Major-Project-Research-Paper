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
 *          [--temperature 0.7 --top-p 0.8 --top-k 20 --repeat-penalty 1.0] [--presence-penalty P] [--compact D41-rule]
 *          [--pause-temp 85 --resume-temp 75] [--shapes FEATURE-RECORD.json] [--list-only]
 * </pre>
 * Defaults are the D44 configuration and the model family's D48/D52 settings ({@link restdd.llm.ModelDefaults});
 * {@code --presence-penalty none} sends none. {@code --list-only} prints the D47
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
        boolean secondRole = false;
        if (a.containsKey("target")) {
            TargetConfig t = TargetConfig.load(Path.of(a.get("target")), name -> "unused-for-fixture-names");
            fixtures.addAll(t.exec().fixtures().keySet());
            secondRole = t.exec().secondaryAuth() != null;
        }
        fixtures.add("uid");
        restdd.explore.FeatureRecord record = a.containsKey("shapes") ? restdd.explore.FeatureRecord.load(Path.of(a.get("shapes"))) : null;
        java.util.function.Function<OperationSlice, String> shapes = record == null ? op -> ""
                : op -> record.forPrompt(op, PromptBuilder.related(promptSpec, op));

        int s = Integer.parseInt(a.getOrDefault("s", "3"));
        List<String> arms = Arrays.asList(a.getOrDefault("arms", "A").split(","));
        for (String arm : arms) {
            if (!arm.matches("A|B|C")) {
                throw new IllegalArgumentException("unknown arm " + arm + " (A, B or C)");
            }
        }
        boolean ablation = a.containsKey("ignore-predicates");
        restdd.select.Selector.Start startRule = restdd.select.Selector.Start.valueOf(a.getOrDefault("start", "TILE").toUpperCase()); // D56(5)
        if (ablation && !SelectReport.DEVELOPMENT.contains(project)) {
            throw new IllegalArgumentException("--ignore-predicates (C∅ ablation, D37) is allowed on development projects only");
        }
        List<Long> seeds = Arrays.stream(a.getOrDefault("seeds", "1,2").split(",")).map(x -> Long.parseLong(x.trim())).toList();
        String model = a.getOrDefault("model", "qwen3:4b-instruct-2507-q4_K_M");
        restdd.llm.ModelDefaults d = restdd.llm.ModelDefaults.forModel(model); // D48/D52 per model family
        String pp = a.get("presence-penalty");
        Double presence = pp == null ? d.presencePenalty() : pp.equals("none") ? null : Double.valueOf(pp);
        GenerationOptions o = new GenerationOptions(model,
                Integer.parseInt(a.getOrDefault("num-ctx", "16384")), Integer.parseInt(a.getOrDefault("num-predict", "2048")),
                a.containsKey("temperature") ? Double.parseDouble(a.get("temperature")) : d.temperature(),
                a.containsKey("top-p") ? Double.parseDouble(a.get("top-p")) : d.topP(),
                a.containsKey("top-k") ? Integer.parseInt(a.get("top-k")) : d.topK(),
                a.containsKey("repeat-penalty") ? Double.parseDouble(a.get("repeat-penalty")) : d.repeatPenalty(),
                null, "30m", presence);
        System.out.printf("model %s | temperature %s, top-p %s, top-k %d, repeat penalty %s, presence penalty %s%n",
                model, o.temperature(), o.topP(), o.topK(), o.repeatPenalty(),
                o.presencePenalty() == null ? "not sent" : o.presencePenalty());

        // B/C: shared selector over the project's features (spec + exploration record + second role); every choice
        // is logged to selection.jsonl before any model call (EXPERIMENT_PROTOCOL §3)
        Map<String, restdd.select.Catalogue> cats = new java.util.HashMap<>();
        for (String arm : arms) {
            if (!arm.equals("A")) {
                restdd.select.Catalogue cat = restdd.select.Catalogue.load(arm);
                cats.put(arm, cat);
                System.out.printf("catalogue %s %s (SHA-256 %s), start rule %s%s%n", arm, cat.version(), cat.sha256(), startRule,
                        ablation ? " — C∅ ablation: predicates ignored" : "");
            }
        }
        Map<String, restdd.select.OperationFeatures> features = cats.isEmpty() ? Map.of()
                : restdd.select.FeatureExtractor.extract(full, record, secondRole);
        Map<String, GenerationRun.Task> taskByCall = new java.util.HashMap<>();
        StringBuilder selLog = new StringBuilder();
        com.fasterxml.jackson.databind.ObjectMapper jm = new com.fasterxml.jackson.databind.ObjectMapper();
        for (OperationSlice op : ops) {
            for (long seed : seeds) {
                for (var ce : cats.entrySet()) {
                    restdd.select.OperationFeatures f = features.get(op.key());
                    List<restdd.select.Selector.Slot> slots = restdd.select.Selector.assign(ce.getValue(), f, seed, s, ablation, startRule);
                    taskByCall.put(ce.getKey() + "|" + seed + "|" + op.key(), new GenerationRun.Task(
                            restdd.select.Selector.task(ce.getValue(), slots),
                            slots.stream().map(restdd.select.Selector.Slot::entryId).toList(),
                            slots.stream().map(restdd.select.Selector.Slot::reason).toList()));
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("arm", ce.getKey());
                    row.put("seed", seed);
                    row.put("operation", op.key());
                    row.put("slots", slots);
                    row.put("features", f.asMap());
                    selLog.append(jm.writeValueAsString(row)).append('\n');
                }
            }
        }
        if (!cats.isEmpty()) {
            Files.writeString(work.resolve("selection.jsonl"), selLog.toString());
        }
        GenerationRun.TaskProvider tasks = (arm, seed, op) -> arm.equals("A")
                ? GenerationRun.Task.plain(PromptBuilder.armATask(s))
                : taskByCall.get(arm + "|" + seed + "|" + op.key());
        GenerationRun.Config config = new GenerationRun.Config(project, promptSpec, full, ops, arms, seeds, s,
                List.copyOf(fixtures), shapes, record == null ? null : record.sha256(), o, tasks);
        if (record != null) {
            System.out.println("exploration feature record " + a.get("shapes") + " (SHA-256 " + record.sha256() + ")");
        }
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
