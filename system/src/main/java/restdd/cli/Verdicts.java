package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.exec.Outcome;
import restdd.verdict.Verdict;
import restdd.verdict.VerdictEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * verdicts --work DIR --defect ID --buggy LABEL --patched LABEL [--r 3]
 *
 * <p>T10 verdict engine over the run records of one generation run (EXPERIMENT_PROTOCOL §8–§9). Run labels are
 * {@code <LABEL>-s1} (screening), {@code <LABEL>-f2}, {@code <LABEL>-f3} (full re-runs of suites with a screening
 * candidate, each from a fresh snapshot) and {@code <LABEL>-i1..i<r>} (isolated re-runs of each candidate). The
 * stage is chosen by which labels exist; each stage writes the plan for the next:
 * <ol>
 *   <li>screening → {@code plan-full-<defect>.txt} (call IDs);</li>
 *   <li>full (s1, f2, f3) → classes over r runs, flaky rate, {@code plan-isolated-<defect>.txt} (callId#index);</li>
 *   <li>isolated → {@code candidates-<defect>.jsonl} (with arm, seed, strategy: for the analysis) and the blinded
 *       export {@code blinded-<defect>.jsonl} (random ID, test hash, both exchanges, failing assertion; no arm, seed,
 *       strategy or call ID) plus {@code mapping-<defect>.jsonl} (random ID → call), kept closed until verdicts are
 *       committed (D30/D46).</li>
 * </ol>
 * Attribution to the fix and the Confirmed/Related/Unrelated/Spurious verdicts are researcher-side (post-freeze).
 */
final class Verdicts {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Verdicts() {}

    /** One test's result in one labelled run. */
    record Result(Outcome outcome, JsonNode test, JsonNode callMeta) {}

    static void run(Map<String, String> a) throws Exception {
        Path work = Path.of(Main.need(a, "work"));
        String defect = Main.need(a, "defect");
        String buggy = Main.need(a, "buggy");
        String patched = Main.need(a, "patched");
        int r = Integer.parseInt(a.getOrDefault("r", "3"));
        String safe = defect.replaceAll("[^A-Za-z0-9._-]", "_");
        Map<String, JsonNode> calls = calls(work);

        List<String> fullLabels = new ArrayList<>(List.of("s1"));
        for (int i = 2; i <= r; i++) {
            fullLabels.add("f" + i);
        }
        List<String> isoLabels = new ArrayList<>();
        for (int i = 1; i <= r; i++) {
            isoLabels.add("i" + i);
        }
        Map<String, Map<String, Result>> runs = new TreeMap<>();
        for (String v : List.of(buggy, patched)) {
            for (String l : concat(fullLabels, isoLabels)) {
                Path f = work.resolve("exec-" + v + "-" + l + ".jsonl");
                if (Files.exists(f)) {
                    runs.put(v + "-" + l, load(f));
                }
            }
        }
        if (!runs.containsKey(buggy + "-s1") || !runs.containsKey(patched + "-s1")) {
            throw new IllegalArgumentException("screening runs missing: need exec-" + buggy + "-s1.jsonl and exec-" + patched + "-s1.jsonl");
        }
        boolean full = fullLabels.stream().allMatch(l -> runs.containsKey(buggy + "-" + l) && runs.containsKey(patched + "-" + l));
        boolean iso = isoLabels.stream().allMatch(l -> runs.containsKey(buggy + "-" + l) && runs.containsKey(patched + "-" + l));

        // ---- screening
        Map<String, Result> sb = runs.get(buggy + "-s1");
        Map<String, Result> sp = runs.get(patched + "-s1");
        Map<String, Integer> screenCounts = new TreeMap<>();
        TreeSet<String> rerun = new TreeSet<>();
        for (String key : sb.keySet()) {
            Result p = sp.get(key);
            List<Outcome> bo = List.of(sb.get(key).outcome());
            List<Outcome> po = p == null ? List.of() : List.of(p.outcome());
            Verdict[] v = VerdictEngine.resolve(VerdictEngine.classify(bo), VerdictEngine.classify(po));
            screenCounts.merge(v[0] + "/" + v[1], 1, Integer::sum);
            if (VerdictEngine.candidate(bo, po)) {
                rerun.add(key.substring(0, key.lastIndexOf('#')));
            }
        }
        System.out.printf("%s — screening (buggy %s, patched %s): %d tests; buggy/patched classes %s%n", defect, buggy,
                patched, sb.size(), screenCounts);
        System.out.printf("  suites with a screening candidate: %d%n", rerun.size());
        if (!full) {
            Path plan = work.resolve("plan-full-" + safe + ".txt");
            Files.writeString(plan, String.join("\n", rerun) + (rerun.isEmpty() ? "" : "\n"));
            System.out.println("  next: run-suites --only " + plan + " with labels " + buggy + "-f2.." + buggy + "-f" + r
                    + " and " + patched + "-f2.." + patched + "-f" + r + " (fresh snapshot before each)");
            return;
        }

        // ---- full runs (s1, f2..fr) on the re-run suites
        Map<String, Integer> fullCounts = new TreeMap<>();
        TreeSet<String> isolated = new TreeSet<>();
        int testsInRerun = 0;
        int flaky = 0;
        for (String key : sb.keySet()) {
            String call = key.substring(0, key.lastIndexOf('#'));
            if (!rerun.contains(call)) {
                continue;
            }
            testsInRerun++;
            List<Outcome> bo = outcomes(runs, buggy, fullLabels, key);
            List<Outcome> po = outcomes(runs, patched, fullLabels, key);
            Verdict[] v = VerdictEngine.resolve(VerdictEngine.classify(bo), VerdictEngine.classify(po));
            fullCounts.merge(v[0] + "/" + v[1], 1, Integer::sum);
            flaky += (v[0] == Verdict.FLAKY || v[1] == Verdict.FLAKY) ? 1 : 0;
            if (VerdictEngine.candidate(bo, po)) {
                isolated.add(key);
            }
        }
        System.out.printf("  full runs (%d per version) on %d tests of the re-run suites: classes %s; flaky %d (%.1f%%)%n",
                fullLabels.size(), testsInRerun, fullCounts, flaky, testsInRerun == 0 ? 0.0 : 100.0 * flaky / testsInRerun);
        System.out.printf("  candidates after full runs: %d%n", isolated.size());
        if (!iso) {
            Path plan = work.resolve("plan-isolated-" + safe + ".txt");
            Files.writeString(plan, String.join("\n", isolated) + (isolated.isEmpty() ? "" : "\n"));
            System.out.println("  next: run-suites --only " + plan + " with labels " + buggy + "-i1.." + buggy + "-i" + r
                    + " and " + patched + "-i1.." + patched + "-i" + r + " (restore before each single test)");
            return;
        }

        // ---- isolated re-runs → final candidates and the blinded export
        SecureRandom rnd = new SecureRandom();
        StringBuilder cand = new StringBuilder();
        StringBuilder blind = new StringBuilder();
        StringBuilder map = new StringBuilder();
        int confirmed = 0;
        int notReproduced = 0;
        List<String> keys = new ArrayList<>(isolated);
        java.util.Collections.shuffle(keys, rnd); // export order carries no arm/seed information
        for (String key : keys) {
            List<Outcome> bo = outcomes(runs, buggy, isoLabels, key);
            List<Outcome> po = outcomes(runs, patched, isoLabels, key);
            if (!VerdictEngine.candidate(bo, po)) {
                notReproduced++;
                continue;
            }
            confirmed++;
            String call = key.substring(0, key.lastIndexOf('#'));
            int index = Integer.parseInt(key.substring(key.lastIndexOf('#') + 1));
            JsonNode callRec = calls.get(call);
            Result rb = runs.get(buggy + "-i1").get(key);
            Result rp = runs.get(patched + "-i1").get(key);
            JsonNode test = JSON.readTree(callRec.path("response").asText()).path("tests").get(index);
            String id = "K-" + HexFormat.of().formatHex(rnd.generateSeed(5));
            String hash = sha256(test.toString());

            ObjectNode c = JSON.createObjectNode();
            c.put("candidateId", id);
            c.put("defect", defect);
            c.put("callId", call);
            c.put("index", index);
            c.put("arm", callRec.path("arm").asText());
            c.put("seed", callRec.path("seed").asLong());
            c.put("operation", callRec.path("operation").asText());
            JsonNode strategies = callRec.path("strategies");
            c.put("strategy", strategies.isArray() && strategies.size() > index ? strategies.get(index).asText() : "");
            c.put("testSha256", hash);
            c.put("assertionClass", rb.test().path("assertionClass").asText());
            cand.append(c).append('\n');

            ObjectNode b = JSON.createObjectNode();
            b.put("candidateId", id);
            b.put("defect", defect);
            b.put("testSha256", hash);
            b.set("test", stripTitle(test));
            b.set("failingAssertions", failing(rb.test()));
            b.set("buggyExchange", rb.test().path("run").path("steps"));
            b.set("patchedExchange", rp.test().path("run").path("steps"));
            blind.append(b).append('\n');

            ObjectNode m = JSON.createObjectNode();
            m.put("candidateId", id);
            m.put("callId", call);
            m.put("index", index);
            map.append(m).append('\n');
        }
        Files.writeString(work.resolve("candidates-" + safe + ".jsonl"), cand.toString());
        Files.writeString(work.resolve("blinded-" + safe + ".jsonl"), blind.toString());
        Files.writeString(work.resolve("mapping-" + safe + ".jsonl"), map.toString());
        System.out.printf("  isolated re-runs (%d per version): reproduced %d, not reproduced %d%n", isoLabels.size(),
                confirmed, notReproduced);
        System.out.println("  wrote candidates-" + safe + ".jsonl (analysis), blinded-" + safe + ".jsonl (reviewers), mapping-"
                + safe + ".jsonl (keep closed until all verdicts are committed)");
    }

    static Map<String, JsonNode> calls(Path work) throws Exception {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(work.resolve("calls.jsonl"))) {
            if (!line.isBlank()) {
                JsonNode c = JSON.readTree(line);
                out.put(c.path("callId").asText(), c);
            }
        }
        return out;
    }

    /** exec file → (callId#index → result); later lines for the same key win (a rerun after an aborted attempt). */
    static Map<String, Result> load(Path f) throws Exception {
        Map<String, Result> out = new TreeMap<>();
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode n = JSON.readTree(line);
            String call = n.path("callId").asText();
            for (JsonNode t : n.path("record").path("tests")) {
                out.put(call + "#" + t.path("index").asInt(),
                        new Result(Outcome.valueOf(t.path("outcome").asText()), t, n.path("record").path("meta")));
            }
        }
        return out;
    }

    static List<Outcome> outcomes(Map<String, Map<String, Result>> runs, String version, List<String> labels, String key) {
        List<Outcome> out = new ArrayList<>();
        for (String l : labels) {
            Result r = runs.getOrDefault(version + "-" + l, Map.of()).get(key);
            out.add(r == null ? Outcome.INFRA_ERROR : r.outcome()); // a missing run is not evaluable
        }
        return out;
    }

    static ArrayNode failing(JsonNode test) {
        ArrayNode out = JSON.createArrayNode();
        for (JsonNode st : test.path("run").path("steps")) {
            for (JsonNode as : st.path("assertions")) {
                if (!as.path("passed").asBoolean(true)) {
                    ObjectNode f = as.deepCopy();
                    f.put("step", st.path("step").asInt());
                    out.add(f);
                }
            }
        }
        return out;
    }

    /** The title can name the strategy ("Persistence round-trip …"), which would unblind the arm. */
    static JsonNode stripTitle(JsonNode test) {
        ObjectNode t = test.deepCopy();
        t.remove("title");
        return t;
    }

    static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
