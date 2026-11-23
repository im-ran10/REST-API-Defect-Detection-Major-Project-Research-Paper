package restdd.exec;

/**
 * Outcome of one test in one run against one deployment. The verdict engine (T10) combines runs and versions
 * into the protocol classes of EXPERIMENT_PROTOCOL §9 (PASS, FAIL, FLAKY, ERROR-INFRA, ERROR-TEST).
 */
public enum Outcome {
    /** All assertions of all steps held. */
    PASS,
    /** An assertion failed on a valid exchange (the run stops at that step). */
    FAIL,
    /**
     * The test's own setup failed: an {@code extract} found nothing, so later steps could not be built. ERROR-TEST
     * if it happens on both versions; on one version only, the verdict engine treats it like FAIL there.
     */
    SETUP_ERROR,
    /** Connection error, timeout, gateway status (502/503/504) or reset failure. Never a detection. */
    INFRA_ERROR,
    /** Not executable as written (structurally invalid, unknown fixture, missing role). Never a detection. */
    INVALID
}
