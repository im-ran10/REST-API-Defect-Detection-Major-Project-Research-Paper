package restdd.verdict;

/** Per-test, per-version class over r runs (EXPERIMENT_PROTOCOL §9, D4.1). */
public enum Verdict {
    PASS, FAIL, FLAKY, ERROR_INFRA, ERROR_TEST,
    /** Internal: the test's own setup failed in every run on this version; resolved across versions (T5 rule). */
    SETUP
}
