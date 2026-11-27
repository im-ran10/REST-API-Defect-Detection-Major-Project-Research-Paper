package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * {@code model-test-report}: the G2 model-quality gate (EXPERIMENT_PROTOCOL §4, D43) over one or more work folders.
 *
 * <ul>
 *   <li>Gate 0: no call failed because its prompt did not fit (truncation is never silent).</li>
 *   <li>T1 = schema-valid calls ÷ calls ≥ 95%.</li>
 *   <li>T2 = spec-valid tests (structurally valid: operations in the spec, references defined, well-formed
 *       assertions) ÷ tests in schema-valid calls ≥ 80%.</li>
 *   <li>T3 = executable tests (outcome PASS or FAIL: no ERROR-TEST = INVALID/SETUP_ERROR, no ERROR-INFRA) ÷
 *       spec-valid tests ≥ 70%.</li>
 *   <li>T4 = executable tests with an assertion beyond the status code (automatic class MEANINGFUL) ÷
 *       executable tests ≥ 60%; 20 random executable tests are written out for the manual check.</li>
 * </ul>
 *
 * <pre>model-test-report --work DIR1,DIR2 [--label modeltest] [--out FILE]</pre>
 */
final class ModelTestReport {

    private ModelTestReport() {}

    static void run(Map<String, String> a) throws Exception {
        ObjectMapper json = new ObjectMapper();
        String label = a.getOrDefault("label", "modeltest");
        int calls = 0, schemaValid = 0, tooLong = 0, cutAtCap = 0, testsInValid = 0, specValid = 0, executable = 0,
                meaningful = 0, infra = 0, errorTest = 0, rescored = 0;
        List<Long> ms = new ArrayList<>();
        List<ObjectNode> executableTests = new ArrayList<>();
        Map<String, Integer> conformance = new HashMap<>();
        Map<String, Integer> classCounts = new java.util.TreeMap<>();
        Map<String, int[]> breakdown = new java.util.TreeMap<>();
        int[] grounding = new int[2]; // [string expected values, of which found in the prompt or the test's own request]

        for (String dir : Main.need(a, "work").split(",")) {
            Path work = Path.of(dir.trim());
            Map<String, JsonNode> exec = new HashMap<>();
            Path execFile = work.resolve("exec-" + label + ".jsonl");
            if (Files.exists(execFile)) {
                for (String line : Files.readAllLines(execFile)) {
                    if (!line.isBlank()) {
                        JsonNode n = json.readTree(line);
                        exec.put(n.path("callId").asText(), n.path("record"));
                    }
                }
            }
            for (String line : Files.readAllLines(work.resolve("calls.jsonl"))) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode c = json.readTree(line);
                calls++;
                if (c.path("error").asText("").startsWith("PROMPT_TOO_LONG")) {
                    tooLong++;
                }
                if ("length".equals(c.path("doneReason").asText())) {
                    cutAtCap++;
                }
                if (c.has("totalMs") && !c.path("fromCache").asBoolean()) {
                    ms.add(c.path("totalMs").asLong());
                }
                if (!c.at("/validation/schemaValid").asBoolean()) {
                    continue;
                }
                schemaValid++;
                JsonNode run = exec.get(c.path("callId").asText());
                Map<Integer, JsonNode> outcomeByIndex = new HashMap<>();
                if (run != null) {
                    for (JsonNode t : run.path("tests")) {
                        outcomeByIndex.put(t.path("index").asInt(), t);
                    }
                }
                JsonNode suite = json.readTree(c.path("response").asText()).path("tests");
                for (JsonNode t : c.at("/validation/tests")) {
                    testsInValid++;
                    t.path("conformanceIssues").forEach(i -> conformance.merge(kind(i.asText()), 1, Integer::sum));
                    if (!t.path("structurallyValid").asBoolean()) {
                        continue;
                    }
                    specValid++;
                    JsonNode e = outcomeByIndex.get(t.path("index").asInt());
                    String outcome = e == null ? "NOT_RUN" : e.path("outcome").asText();
                    switch (outcome) {
                        case "PASS", "FAIL" -> {
                            executable++;
                            String current = restdd.dsl.StaticValidator.classify(suite.get(t.path("index").asInt())).name();
                            if (!current.equals(t.path("assertionClass").asText())) {
                                rescored++;
                            }
                            boolean m = "MEANINGFUL".equals(current);
                            if (m) {
                                meaningful++;
                            }
                            JsonNode test = suite.get(t.path("index").asInt());
                            classCounts.merge(current, 1, Integer::sum);
                            String[] id = c.path("callId").asText().split("\\|");
                            String method = id.length > 3 ? id[3].split(" ")[0] : "?";
                            String kind = t.path("expectsClientError").asBoolean() ? "expects 4xx" : "expects success";
                            for (String key : List.of("method " + method, kind)) {
                                int[] mt = breakdown.computeIfAbsent(key, k -> new int[2]);
                                mt[0] += m ? 1 : 0;
                                mt[1]++;
                            }
                            groundingCount(test, c.path("userPrompt").asText(""), grounding);
                            ObjectNode sample = json.createObjectNode();
                            sample.put("callId", c.path("callId").asText());
                            sample.put("index", t.path("index").asInt());
                            sample.put("automaticClass", current);
                            sample.put("archivedClass", t.path("assertionClass").asText());
                            sample.put("outcome", outcome);
                            sample.set("test", suite.get(t.path("index").asInt()));
                            executableTests.add(sample);
                        }
                        case "INFRA_ERROR" -> infra++;
                        case "NOT_RUN" -> { }
                        default -> errorTest++;
                    }
                }
            }
        }

        double t1 = pct(schemaValid, calls), t2 = pct(specValid, testsInValid), t3 = pct(executable, specValid),
                t4 = pct(meaningful, executable);
        System.out.printf("calls %d | prompt too long %d | output cut at the cap %d%n", calls, tooLong, cutAtCap);
        System.out.printf("Gate 0  no truncated prompt               %s%n", tooLong == 0 ? "PASS" : "FAIL");
        line("T1", "schema-valid calls", schemaValid, calls, t1, 95);
        line("T2", "spec-valid tests", specValid, testsInValid, t2, 80);
        line("T3", "executable tests", executable, specValid, t3, 70);
        line("T4", "non-trivial assertions", meaningful, executable, t4, 60);
        System.out.printf("not executable: ERROR-TEST %d, ERROR-INFRA %d, not run %d%n", errorTest, infra,
                specValid - executable - errorTest - infra);
        System.out.println("conformance notes (reported only): " + conformance);
        System.out.println("T4 uses the current rubric R-T4; tests whose archived class differed: " + rescored);
        System.out.println("T4 classes of executable tests: " + classCounts);
        breakdown.forEach((k, v) -> System.out.printf("  %-18s non-trivial %3d / %-3d = %5.1f%%%n", k, v[0], v[1], pct(v[0], v[1])));
        System.out.printf("expected string values in body/header checks: %d, found in the prompt or the test's own request: %d, "
                + "not found: %d (reported only)%n", grounding[0], grounding[1], grounding[0] - grounding[1]);
        if (!ms.isEmpty()) {
            Collections.sort(ms);
            System.out.printf("fresh-call time: median %.1f s, p95 %.1f s, mean %.1f s (n=%d)%n", ms.get(ms.size() / 2) / 1000.0,
                    ms.get((int) Math.min(ms.size() - 1, Math.ceil(0.95 * ms.size()) - 1)) / 1000.0,
                    ms.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0, ms.size());
        }
        boolean pass = tooLong == 0 && t1 >= 95 && t2 >= 80 && t3 >= 70 && t4 >= 60;
        System.out.println("G2 (automatic part): " + (pass ? "PASS" : "FAIL") + " — T4 still needs the manual check of 20 tests");

        Collections.shuffle(executableTests, new Random(2026));
        ArrayNode sample = json.createArrayNode();
        executableTests.stream().limit(20).forEach(sample::add);
        Path out = Path.of(a.getOrDefault("out", "t4-sample.json"));
        Files.writeString(out, sample.toPrettyString());
        System.out.println("T4 manual-check sample (20 random executable tests): " + out.toAbsolutePath());
    }

    private static String kind(String issue) {
        int i = issue.indexOf(':');
        return i > 0 && i < 40 ? issue.substring(0, i) : issue.length() > 40 ? issue.substring(0, 40) : issue;
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0 : 100.0 * a / b;
    }

    private static void line(String id, String what, int a, int b, double p, int threshold) {
        System.out.printf("%s      %-30s %4d / %-4d = %5.1f%%  (≥ %d%%)  %s%n", id, what, a, b, p, threshold,
                p >= threshold ? "PASS" : "FAIL");
    }

    private static final List<String> VALUE_OPS = List.of("equals", "notEquals", "contains", "notContains", "in", "notIn", "matches");

    /** Counts string expected values and how many occur in the user prompt or in the test's own requests. */
    static void groundingCount(JsonNode test, String userPrompt, int[] g) {
        if (test == null) {
            return;
        }
        String prompt = userPrompt.toLowerCase();
        StringBuilder req = new StringBuilder();
        for (JsonNode st : test.path("steps")) {
            for (String f : List.of("path", "pathParams", "query", "headers", "body", "multipart")) {
                req.append(st.path(f).toString().toLowerCase()).append(' ');
            }
        }
        for (JsonNode st : test.path("steps")) {
            for (JsonNode a : st.path("assertions")) {
                String type = a.path("type").asText();
                if (!(type.equals("body") || type.equals("header")) || !VALUE_OPS.contains(a.path("op").asText())) {
                    continue;
                }
                List<JsonNode> vals = new ArrayList<>();
                if (a.path("value").isArray()) {
                    a.path("value").forEach(vals::add);
                } else {
                    vals.add(a.path("value"));
                }
                for (JsonNode v : vals) {
                    if (!v.isTextual() || v.asText().isBlank()) {
                        continue;
                    }
                    String x = v.asText().toLowerCase();
                    g[0]++;
                    if (prompt.contains(x) || req.toString().contains(x)) {
                        g[1]++;
                    }
                }
            }
        }
    }
}
