package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.dsl.StaticValidator;
import restdd.dsl.StaticValidator.SuiteReport;
import restdd.dsl.StaticValidator.TestReport;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one generated suite (one model response) against one deployment: reset to the snapshot, then every
 * structurally valid test in order. A schema-invalid response runs nothing (zero tests for that call, D26);
 * a structurally invalid test is recorded as INVALID and not sent. A failed reset makes every test INFRA_ERROR.
 * The returned record holds the validation result and every exchange (D44 archiving).
 */
public final class SuiteRunner {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StaticValidator validator;
    private final TestExecutor executor;
    private final ResetAdapter reset;

    public SuiteRunner(StaticValidator validator, TestExecutor executor, ResetAdapter reset) {
        this.validator = validator;
        this.executor = executor;
        this.reset = reset;
    }

    /**
     * @param responseText the model's raw response (the suite)
     * @param s            tests expected in the suite
     * @param meta         run labels (project, version, run index, ...); stored as given
     * @param indices      tests to run (null = all); isolated re-runs pass a single index
     */
    public ObjectNode run(String responseText, int s, Map<String, String> meta, List<Integer> indices) {
        ObjectNode rec = JSON.createObjectNode();
        ObjectNode m = rec.putObject("meta");
        meta.forEach(m::put);
        rec.put("startedAt", Instant.now().toString());
        rec.put("reset", reset.name());
        long t0 = System.nanoTime();

        SuiteReport report = validator.validate(responseText, s);
        ObjectNode v = rec.putObject("validation");
        v.put("parsed", report.parsed());
        v.put("schemaValid", report.schemaValid());
        ArrayNode se = v.putArray("schemaErrors");
        report.schemaErrors().forEach(se::add);

        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        ArrayNode tests = rec.putArray("tests");
        if (!report.schemaValid()) {
            rec.put("executed", false);
            rec.put("note", "schema-invalid response: zero tests for this call (D26)");
            finish(rec, counts, t0);
            return rec;
        }

        ObjectNode resetRec = rec.putObject("resetRecord");
        String resetFailure = null;
        try {
            reset.restore(resetRec);
        } catch (ResetAdapter.ResetException e) {
            resetFailure = e.getMessage();
            resetRec.put("error", e.getMessage());
        }

        JsonNode suite;
        try {
            suite = JSON.readTree(responseText).path("tests");
        } catch (Exception e) {
            throw new IllegalStateException("validated suite no longer parses", e);
        }
        for (TestReport tr : report.tests()) {
            if (indices != null && !indices.contains(tr.index())) {
                continue;
            }
            ObjectNode t = tests.addObject();
            t.put("index", tr.index());
            t.put("title", tr.title());
            t.put("assertionClass", tr.assertionClass().name());
            t.put("trivialAssertions", tr.trivialAssertions());
            t.put("expectsClientError", tr.expectsClientError());
            ArrayNode ops = t.putArray("operations");
            tr.operations().forEach(ops::add);
            ArrayNode st = t.putArray("structuralErrors");
            tr.structuralErrors().forEach(st::add);
            ArrayNode ci = t.putArray("conformanceIssues");
            tr.conformanceIssues().forEach(ci::add);

            Outcome outcome;
            String reason;
            if (!tr.structurallyValid()) {
                outcome = Outcome.INVALID;
                reason = "structurally invalid: " + tr.structuralErrors().get(0);
            } else if (resetFailure != null) {
                outcome = Outcome.INFRA_ERROR;
                reason = "reset failed: " + resetFailure;
            } else {
                TestExecutor.TestRun run = executor.run(suite.get(tr.index()));
                outcome = run.outcome();
                reason = run.reason();
                t.set("run", run.record());
            }
            t.put("outcome", outcome.name());
            t.put("reason", reason);
            counts.merge(outcome, 1, Integer::sum);
        }
        rec.put("executed", true);
        finish(rec, counts, t0);
        return rec;
    }

    private static void finish(ObjectNode rec, Map<Outcome, Integer> counts, long t0) {
        ObjectNode sum = rec.putObject("summary");
        for (Outcome o : Outcome.values()) {
            sum.put(o.name(), counts.getOrDefault(o, 0));
        }
        rec.put("durationMs", (System.nanoTime() - t0) / 1_000_000);
    }
}
