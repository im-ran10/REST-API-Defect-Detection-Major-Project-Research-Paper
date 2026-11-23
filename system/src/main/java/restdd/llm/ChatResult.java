package restdd.llm;

/**
 * One model call. {@code outputTruncated} = generation stopped at the output cap (done_reason "length");
 * such a response usually fails the schema and counts as the arm's own outcome (no retry, D26).
 */
public record ChatResult(String cacheKey, boolean fromCache, String content, String doneReason,
                         int promptEvalCount, int evalCount, long totalNs, long loadNs, long promptEvalNs,
                         long evalNs) {
    public boolean outputTruncated() {
        return "length".equals(doneReason);
    }
}
