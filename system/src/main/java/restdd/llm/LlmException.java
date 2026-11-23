package restdd.llm;

/** Failures that stop a run instead of being recorded as an arm's outcome. */
public class LlmException extends RuntimeException {

    public enum Kind {
        /** Ollama unreachable, crashed or answered 5xx: infrastructure, the call may be re-attempted. */
        INFRASTRUCTURE,
        /** The prompt does not fit the context window. Never truncated silently (D43). */
        PROMPT_TOO_LONG,
        /** The model is not fully on the GPU, or loaded with another context window (D43 preflight). */
        NOT_ON_GPU,
        /** A request Ollama rejected for another reason: a bug in the caller. */
        BAD_REQUEST
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(kind + ": " + message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
