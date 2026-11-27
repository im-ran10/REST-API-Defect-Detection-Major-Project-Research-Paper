package restdd.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.dsl.StaticValidator;
import restdd.dsl.StaticValidator.SuiteReport;
import restdd.dsl.StaticValidator.TestReport;
import restdd.dsl.SuiteSchema;
import restdd.llm.ChatResult;
import restdd.llm.GenerationOptions;
import restdd.llm.LlmException;
import restdd.llm.OllamaClient;
import restdd.prompt.PromptBuilder;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * One generation run (T6; later T14): one model call per (operation, seed, arm), no retries of a model answer
 * (D26). Every call is written to {@code calls.jsonl} with the exact user prompt, the raw response, the token
 * counts, timings, the GPU reading and the full static-validation result (D44 archiving); the Ollama client's
 * cache keeps the exact request. Re-running the same command resumes: finished calls come from the cache.
 *
 * <p>Prompts use the compacted spec (D41); validation uses the uncompacted spec. Infrastructure failures
 * (Ollama unreachable) are retried after 30 s, at most 3 times, then the run stops (resume later).
 */
public final class GenerationRun {

    /** The task section for one call, with the strategies of its slots (empty for arm A) and why they were chosen. */
    public record Task(String text, List<String> strategies, List<String> reasons) {
        public static Task plain(String text) {
            return new Task(text, List.of(), List.of());
        }
    }

    /** (arm, seed, operation) → task. Arm A: {@link PromptBuilder#armATask}; B/C: shared selector + catalogue (T8). */
    @FunctionalInterface
    public interface TaskProvider {
        Task task(String arm, long seed, OperationSlice op);
    }

    public record Config(String project, NormalisedSpec promptSpec, NormalisedSpec fullSpec, List<OperationSlice> ops,
                         List<String> arms, List<Long> seeds, int s, List<String> fixtureNames,
                         java.util.function.Function<OperationSlice, String> shapes, String shapesSha256,
                         GenerationOptions options, TaskProvider tasks) {}

    public record Summary(int calls, int fromCache, int schemaValid, int promptTooLong, int outputTruncated,
                          int thinkingDetected, long modelMs, long pausedMs, int pauses) {}

    /**
     * The thinking mode is fixed per run and recorded (owner, D48): {@code think} omitted = the model's own mode;
     * for Qwen3 4B Instruct-2507, a non-thinking model, that is non-thinking. Every response is checked.
     */
    public static String thinkingMode(GenerationOptions o) {
        return o.think() == null ? "non-thinking (think field omitted; non-thinking model)" : (o.think() ? "thinking on" : "thinking off (think=false)");
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Config c;
    private final OllamaClient client;
    private final PromptBuilder prompts;
    private final GpuMonitor gpu;
    private final Path work;

    public GenerationRun(Config c, OllamaClient client, PromptBuilder prompts, GpuMonitor gpu, Path work) {
        this.c = c;
        this.client = client;
        this.prompts = prompts;
        this.gpu = gpu;
        this.work = work;
    }

    public Summary run() throws IOException, InterruptedException {
        Files.createDirectories(work);
        String digest = client.modelDigest(c.options().model());
        client.preflight(c.options()); // NOT_ON_GPU stops the run before any call
        StaticValidator validator = new StaticValidator(c.fullSpec(), new java.util.HashSet<>(c.fixtureNames()));
        var schema = SuiteSchema.build(c.s());

        int total = c.ops().size() * c.seeds().size() * c.arms().size();
        int n = 0;
        int cached = 0;
        int valid = 0;
        int tooLong = 0;
        int truncated = 0;
        int thinking = 0;
        long modelMs = 0;
        try (BufferedWriter out = Files.newBufferedWriter(work.resolve("calls.jsonl"), StandardCharsets.UTF_8)) {
            for (OperationSlice op : c.ops()) {
                OperationSlice promptOp = c.promptSpec().find(op.method(), op.path());
                for (long seed : c.seeds()) {
                    for (String arm : c.arms()) {
                        n++;
                        Task task = c.tasks().task(arm, seed, op);
                        String user = prompts.user(c.promptSpec(), promptOp, c.shapes().apply(promptOp), c.fixtureNames(),
                                task.text());
                        ObjectNode rec = JSON.createObjectNode();
                        rec.put("callId", c.project() + "|" + arm + "|" + seed + "|" + op.key());
                        rec.put("project", c.project());
                        rec.put("arm", arm);
                        rec.put("seed", seed);
                        rec.put("operation", op.key());
                        rec.put("s", c.s());
                        ArrayNode strategies = rec.putArray("strategies");
                        task.strategies().forEach(strategies::add);
                        ArrayNode reasons = rec.putArray("selectionReasons");
                        task.reasons().forEach(reasons::add);
                        rec.put("userPrompt", user);

                        ChatResult r = null;
                        String error = null;
                        GpuMonitor.Reading reading = null;
                        for (int attempt = 1; ; attempt++) {
                            try {
                                if (reading == null && !client.isCached(prompts.system(), user, schema, c.options(), seed, digest)) {
                                    reading = gpu.waitUntilCool();
                                }
                                r = client.chat(prompts.system(), user, schema, c.options(), seed, digest,
                                        Map.of("project", c.project(), "arm", arm, "seed", String.valueOf(seed),
                                                "operation", op.key()));
                                break;
                            } catch (LlmException e) {
                                if (e.kind() == LlmException.Kind.INFRASTRUCTURE && attempt <= 3) {
                                    System.out.printf("  Ollama unreachable (%s); retry %d/3 in 30 s%n", e.getMessage(), attempt);
                                    Thread.sleep(30_000);
                                    continue;
                                }
                                if (e.kind() == LlmException.Kind.PROMPT_TOO_LONG) {
                                    error = "PROMPT_TOO_LONG: " + e.getMessage();
                                    tooLong++;
                                    break;
                                }
                                throw e; // infrastructure after retries, bad request, not on GPU: stop, resume later
                            }
                        }
                        if (reading != null) {
                            rec.set("gpu", reading.toJson());
                        }

                        SuiteReport report;
                        if (r != null) {
                            rec.put("cacheKey", r.cacheKey());
                            rec.put("fromCache", r.fromCache());
                            rec.put("promptTokens", r.promptEvalCount());
                            rec.put("outputTokens", r.evalCount());
                            rec.put("totalMs", r.totalNs() / 1_000_000);
                            rec.put("doneReason", r.doneReason());
                            rec.put("thinkingMode", thinkingMode(c.options()));
                            rec.put("thinkingDetected", r.thinkingDetected());
                            if (r.thinkingDetected()) {
                                thinking++;
                            }
                            rec.put("response", r.content());
                            if (r.fromCache()) {
                                cached++;
                            } else {
                                modelMs += r.totalNs() / 1_000_000;
                            }
                            if (r.outputTruncated()) {
                                truncated++;
                            }
                            report = validator.validate(r.content(), c.s());
                        } else {
                            rec.put("error", error);
                            report = validator.validate("", c.s());
                        }
                        rec.set("validation", validation(report));
                        if (report.schemaValid()) {
                            valid++;
                        }
                        out.write(rec.toString());
                        out.newLine();
                        out.flush();

                        long structural = report.tests().stream().filter(TestReport::structurallyValid).count();
                        System.out.printf("[%d/%d] %s seed %d %-45s %s%n", n, total, arm, seed, shorten(op.key()),
                                r == null ? error : String.format("%5.1f s  out %4d tok  %s  schema-valid %s  valid tests %d/%d",
                                        r.totalNs() / 1e9, r.evalCount(), r.fromCache() ? "cached" : "      ",
                                        report.schemaValid(), structural, report.tests().size()));
                    }
                }
            }
        }
        return new Summary(n, cached, valid, tooLong, truncated, thinking, modelMs, gpu.pausedMs(), gpu.pauses());
    }

    static ObjectNode validation(SuiteReport report) {
        ObjectNode v = JSON.createObjectNode();
        v.put("parsed", report.parsed());
        v.put("schemaValid", report.schemaValid());
        ArrayNode se = v.putArray("schemaErrors");
        report.schemaErrors().forEach(se::add);
        ArrayNode tests = v.putArray("tests");
        for (TestReport t : report.tests()) {
            ObjectNode o = tests.addObject();
            o.put("index", t.index());
            o.put("title", t.title());
            o.put("structurallyValid", t.structurallyValid());
            ArrayNode st = o.putArray("structuralErrors");
            t.structuralErrors().forEach(st::add);
            ArrayNode ci = o.putArray("conformanceIssues");
            t.conformanceIssues().forEach(ci::add);
            ArrayNode ops = o.putArray("operations");
            t.operations().forEach(ops::add);
            o.put("assertionClass", t.assertionClass().name());
            o.put("trivialAssertions", t.trivialAssertions());
            o.put("expectsClientError", t.expectsClientError());
        }
        return v;
    }

    private static String shorten(String key) {
        return key.length() > 45 ? key.substring(0, 42) + "..." : key;
    }

    /** Manifest fields known before the run; the caller adds provenance it collects. */
    public ObjectNode manifest(String digest, String ollamaVersion) {
        ObjectNode m = JSON.createObjectNode();
        m.put("createdAt", Instant.now().toString());
        m.put("project", c.project());
        m.put("specSha256", c.fullSpec().sourceSha256());
        m.put("s", c.s());
        ArrayNode arms = m.putArray("arms");
        c.arms().forEach(arms::add);
        ArrayNode seeds = m.putArray("seeds");
        c.seeds().forEach(seeds::add);
        ArrayNode ops = m.putArray("operations");
        c.ops().forEach(o -> ops.add(o.key()));
        m.put("operationCount", c.ops().size());
        m.put("operationListSha256", OperationSelector.listSha256(c.ops()));
        ArrayNode fx = m.putArray("fixtureNames");
        c.fixtureNames().forEach(fx::add);
        m.put("featureRecordSha256", c.shapesSha256() == null ? "none" : c.shapesSha256());
        m.put("systemPromptSha256", OperationSelector.sha256(prompts.system()));
        ObjectNode model = m.putObject("model");
        model.put("name", c.options().model());
        model.put("digest", digest);
        model.put("ollamaVersion", ollamaVersion);
        m.set("options", JSON.valueToTree(c.options()));
        m.put("thinkingMode", thinkingMode(c.options()));
        m.put("armsShareOneConfiguration", true); // one GenerationOptions object for every arm (D12)
        return m;
    }
}
