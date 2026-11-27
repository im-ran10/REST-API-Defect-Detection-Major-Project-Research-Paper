package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictsFlowTest {

    private static final ObjectMapper J = new ObjectMapper();
    private static final String C1 = "shelf|C|1|GET /books";
    private static final String C2 = "shelf|B|1|GET /books";

    /** One exec line: call with tests (index → outcome), with a failing assertion recorded for FAILs. */
    private static String line(String call, Map<Integer, String> outcomes) {
        ObjectNode n = J.createObjectNode();
        n.put("callId", call);
        ArrayNode tests = n.putObject("record").putArray("tests");
        new java.util.TreeMap<>(outcomes).forEach((i, o) -> {
            ObjectNode t = tests.addObject();
            t.put("index", i);
            t.put("outcome", o);
            t.put("assertionClass", "MEANINGFUL");
            ObjectNode step = t.putObject("run").putArray("steps").addObject();
            step.put("step", 1);
            step.putObject("request").put("url", "http://x/books");
            step.putObject("response").put("status", 200);
            step.putArray("assertions").addObject().put("passed", !o.equals("FAIL")).put("detail", "$.title equals \"Emma\"");
        });
        return n.toString();
    }

    private static void write(Path w, String label, String... lines) throws Exception {
        Files.writeString(w.resolve("exec-" + label + ".jsonl"), String.join("\n", lines) + "\n");
    }

    private static Map<String, String> args(Path w) {
        Map<String, String> a = new HashMap<>();
        a.put("work", w.toString());
        a.put("defect", "shelf#1");
        a.put("buggy", "bug");
        a.put("patched", "fix");
        return a;
    }

    @Test
    void screeningThenFullThenIsolatedThenBlindedExport() throws Exception {
        Path w = Files.createTempDirectory("verdicts");
        String suite = "{\"tests\":[{\"title\":\"Persistence round-trip of title\",\"steps\":[]},{\"title\":\"b\",\"steps\":[]},{\"title\":\"c\",\"steps\":[]}]}";
        ObjectNode c1 = J.createObjectNode().put("callId", C1).put("arm", "C").put("seed", 1).put("operation", "GET /books")
                .put("response", suite);
        c1.putArray("strategies").add("C3").add("C4").add("C5");
        ObjectNode c2 = J.createObjectNode().put("callId", C2).put("arm", "B").put("seed", 1).put("operation", "GET /books")
                .put("response", suite);
        Files.writeString(w.resolve("calls.jsonl"), c1 + "\n" + c2 + "\n");

        // screening: C1#0 FAIL/PASS (candidate), C1#1 FAIL/FAIL, C2 all PASS
        write(w, "bug-s1", line(C1, Map.of(0, "FAIL", 1, "FAIL", 2, "PASS")), line(C2, Map.of(0, "PASS", 1, "PASS", 2, "PASS")));
        write(w, "fix-s1", line(C1, Map.of(0, "PASS", 1, "FAIL", 2, "PASS")), line(C2, Map.of(0, "PASS", 1, "PASS", 2, "PASS")));
        Verdicts.run(args(w));
        assertEquals(List.of(C1), Files.readAllLines(w.resolve("plan-full-shelf_1.txt")), "only the suite with a candidate");

        // full re-runs of C1: #0 stays FAIL/PASS; #2 turns flaky on patched
        for (String l : List.of("f2", "f3")) {
            write(w, "bug-" + l, line(C1, Map.of(0, "FAIL", 1, "FAIL", 2, "PASS")));
            write(w, "fix-" + l, line(C1, Map.of(0, "PASS", 1, "FAIL", 2, l.equals("f2") ? "FAIL" : "PASS")));
        }
        Verdicts.run(args(w));
        assertEquals(List.of(C1 + "#0"), Files.readAllLines(w.resolve("plan-isolated-shelf_1.txt")));

        // isolated: reproduced
        for (String l : List.of("i1", "i2", "i3")) {
            write(w, "bug-" + l, line(C1, Map.of(0, "FAIL")));
            write(w, "fix-" + l, line(C1, Map.of(0, "PASS")));
        }
        Verdicts.run(args(w));
        List<String> cand = Files.readAllLines(w.resolve("candidates-shelf_1.jsonl"));
        assertEquals(1, cand.size());
        JsonNode c = J.readTree(cand.get(0));
        assertEquals("C", c.path("arm").asText());
        assertEquals("C3", c.path("strategy").asText());
        String blinded = Files.readString(w.resolve("blinded-shelf_1.jsonl"));
        JsonNode b = J.readTree(blinded.strip());
        assertEquals(c.path("candidateId").asText(), b.path("candidateId").asText());
        for (String hidden : List.of("\"arm\"", "\"seed\"", "strategy", "callId", "shelf|C", "Persistence")) {
            assertFalse(blinded.contains(hidden), "blinded export leaks " + hidden);
        }
        assertEquals(1, b.path("failingAssertions").size());
        assertTrue(b.path("buggyExchange").isArray() && b.path("patchedExchange").isArray());
        assertTrue(Files.readString(w.resolve("mapping-shelf_1.jsonl")).contains(C1));
    }

    @Test
    void notReproducedInIsolationIsNotACandidate() throws Exception {
        Path w = Files.createTempDirectory("verdicts2");
        String suite = "{\"tests\":[{\"title\":\"a\",\"steps\":[]}]}";
        Files.writeString(w.resolve("calls.jsonl"), J.createObjectNode().put("callId", C1).put("arm", "A").put("seed", 1)
                .put("response", suite) + "\n");
        for (String l : List.of("s1", "f2", "f3")) {
            write(w, "bug-" + l, line(C1, Map.of(0, "FAIL")));
            write(w, "fix-" + l, line(C1, Map.of(0, "PASS")));
        }
        for (String l : List.of("i1", "i2", "i3")) {
            write(w, "bug-" + l, line(C1, Map.of(0, l.equals("i2") ? "PASS" : "FAIL")));
            write(w, "fix-" + l, line(C1, Map.of(0, "PASS")));
        }
        Verdicts.run(args(w));
        assertEquals("", Files.readString(w.resolve("candidates-shelf_1.jsonl")), "order-dependent failure is dropped");
    }
}
