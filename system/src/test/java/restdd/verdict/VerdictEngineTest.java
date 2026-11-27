package restdd.verdict;

import org.junit.jupiter.api.Test;
import restdd.exec.Outcome;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static restdd.exec.Outcome.FAIL;
import static restdd.exec.Outcome.INFRA_ERROR;
import static restdd.exec.Outcome.INVALID;
import static restdd.exec.Outcome.PASS;
import static restdd.exec.Outcome.SETUP_ERROR;

class VerdictEngineTest {

    @Test
    void classesFollowProtocolSection9() {
        assertEquals(Verdict.PASS, VerdictEngine.classify(List.of(PASS, PASS, PASS)));
        assertEquals(Verdict.FAIL, VerdictEngine.classify(List.of(FAIL, FAIL, FAIL)));
        assertEquals(Verdict.FLAKY, VerdictEngine.classify(List.of(FAIL, PASS, FAIL)));
        assertEquals(Verdict.ERROR_INFRA, VerdictEngine.classify(List.of(FAIL, INFRA_ERROR, FAIL)));
        assertEquals(Verdict.ERROR_INFRA, VerdictEngine.classify(List.of()), "missing run = not evaluable");
        assertEquals(Verdict.ERROR_TEST, VerdictEngine.classify(List.of(INVALID)));
        assertEquals(Verdict.FAIL, VerdictEngine.classify(List.of(FAIL, SETUP_ERROR, FAIL)), "consistently not passing");
        assertEquals(Verdict.SETUP, VerdictEngine.classify(List.of(SETUP_ERROR, SETUP_ERROR, SETUP_ERROR)));
    }

    @Test
    void setupErrorIsResolvedAcrossVersions() {
        Verdict[] both = VerdictEngine.resolve(Verdict.SETUP, Verdict.SETUP);
        assertEquals(Verdict.ERROR_TEST, both[0]);
        assertEquals(Verdict.ERROR_TEST, both[1]);
        Verdict[] one = VerdictEngine.resolve(Verdict.SETUP, Verdict.PASS);
        assertEquals(Verdict.FAIL, one[0], "setup failing on the buggy version only counts as FAIL there");
        assertEquals(Verdict.PASS, one[1]);
    }

    @Test
    void candidateNeedsFailOnBuggyAndPassOnPatchedInEveryRun() {
        assertTrue(VerdictEngine.candidate(List.of(FAIL, FAIL, FAIL), List.of(PASS, PASS, PASS)));
        assertTrue(VerdictEngine.candidate(List.of(SETUP_ERROR, SETUP_ERROR, SETUP_ERROR), List.of(PASS, PASS, PASS)));
        assertFalse(VerdictEngine.candidate(List.of(FAIL, PASS, FAIL), List.of(PASS, PASS, PASS)), "flaky on buggy");
        assertFalse(VerdictEngine.candidate(List.of(FAIL, FAIL, FAIL), List.of(PASS, FAIL, PASS)), "flaky on patched");
        assertFalse(VerdictEngine.candidate(List.of(FAIL, FAIL, FAIL), List.of(PASS, INFRA_ERROR, PASS)), "infra");
        assertFalse(VerdictEngine.candidate(List.of(FAIL, FAIL, FAIL), List.of(FAIL, FAIL, FAIL)), "fails on both");
        assertFalse(VerdictEngine.candidate(List.of(PASS, PASS, PASS), List.of(FAIL, FAIL, FAIL)), "reverse direction");
        assertFalse(VerdictEngine.candidate(List.of(INVALID), List.of(PASS)));
    }
}
