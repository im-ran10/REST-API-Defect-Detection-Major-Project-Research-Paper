package restdd.verdict;

import restdd.exec.Outcome;

import java.util.List;

/**
 * Combines run outcomes into the protocol classes and decides candidates (EXPERIMENT_PROTOCOL §8–§9, D4.1, D28,
 * D32). Pure functions; no benchmark data, no knowledge of which defect is targeted.
 *
 * <ul>
 *   <li>any INFRA_ERROR → ERROR-INFRA (not evaluable; redeploys are the orchestrator's job);</li>
 *   <li>any INVALID → ERROR-TEST;</li>
 *   <li>all PASS → PASS; all FAIL/SETUP_ERROR → FAIL (all SETUP_ERROR → SETUP, resolved across versions);
 *       PASS mixed with a non-pass → FLAKY;</li>
 *   <li>SETUP on both versions → ERROR-TEST on both; on one version only → FAIL there (T5 note, §9);</li>
 *   <li>candidate ⇔ FAIL on buggy and PASS on patched (no ERROR-INFRA by construction).</li>
 * </ul>
 */
public final class VerdictEngine {

    private VerdictEngine() {}

    public static Verdict classify(List<Outcome> runs) {
        if (runs == null || runs.isEmpty()) {
            return Verdict.ERROR_INFRA; // a missing run is not evaluable
        }
        if (runs.contains(Outcome.INFRA_ERROR)) {
            return Verdict.ERROR_INFRA;
        }
        if (runs.contains(Outcome.INVALID)) {
            return Verdict.ERROR_TEST;
        }
        long pass = runs.stream().filter(o -> o == Outcome.PASS).count();
        long setup = runs.stream().filter(o -> o == Outcome.SETUP_ERROR).count();
        if (pass == runs.size()) {
            return Verdict.PASS;
        }
        if (pass > 0) {
            return Verdict.FLAKY;
        }
        return setup == runs.size() ? Verdict.SETUP : Verdict.FAIL;
    }

    /** The two versions' classes after the cross-version SETUP rule: {buggy, patched}. */
    public static Verdict[] resolve(Verdict buggy, Verdict patched) {
        if (buggy == Verdict.SETUP && patched == Verdict.SETUP) {
            return new Verdict[] {Verdict.ERROR_TEST, Verdict.ERROR_TEST};
        }
        return new Verdict[] {buggy == Verdict.SETUP ? Verdict.FAIL : buggy, patched == Verdict.SETUP ? Verdict.FAIL : patched};
    }

    public static boolean candidate(List<Outcome> buggyRuns, List<Outcome> patchedRuns) {
        Verdict[] v = resolve(classify(buggyRuns), classify(patchedRuns));
        return v[0] == Verdict.FAIL && v[1] == Verdict.PASS;
    }
}
