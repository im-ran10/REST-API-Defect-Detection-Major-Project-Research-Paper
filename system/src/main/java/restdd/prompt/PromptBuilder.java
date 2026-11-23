package restdd.prompt;

import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the prompt skeleton shared by all arms (D12): an identical system prompt (format, rules, one example
 * on a fictional API, D35) and an identical operation context; only the task section differs between arms.
 *
 * <p>Operation context = the operation's slice + "related operations" (method, path, summary of other
 * operations on the same resource, so tests can create and read back what they need) + the exploration
 * shape record (D13.1) + the fixture variable names. The related-operations rule is deterministic and
 * defect-independent: same first static path segment(s), sorted, capped.
 */
public final class PromptBuilder {

    public static final int MAX_RELATED = 20;
    private static final int SUMMARY_CAP = 80;

    private final String system;

    public PromptBuilder() throws IOException {
        try (InputStream in = PromptBuilder.class.getResourceAsStream("/prompts/system.txt")) {
            if (in == null) {
                throw new IOException("prompts/system.txt missing from the classpath");
            }
            this.system = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String system() {
        return system;
    }

    /** Arm A task section (final methodology §4.2), for {@code s} slots. */
    public static String armATask(int s) {
        return "Write " + s + " test case" + (s == 1 ? "" : "s")
                + " for this operation that are likely to reveal defects, with assertions.";
    }

    public String user(NormalisedSpec spec, OperationSlice op, String explorationShapes, List<String> fixtureNames,
                       String taskSection) {
        StringBuilder sb = new StringBuilder();
        sb.append("OPERATION UNDER TEST\n").append(op.text()).append("\n\n");
        List<OperationSlice> related = related(spec, op);
        if (!related.isEmpty()) {
            sb.append("OTHER OPERATIONS YOU MAY CALL\n");
            for (OperationSlice r : related) {
                sb.append(r.method()).append(' ').append(r.path());
                String summary = r.slice().path("summary").asText("");
                if (!summary.isBlank()) {
                    sb.append(" - ").append(summary.length() > SUMMARY_CAP ? summary.substring(0, SUMMARY_CAP) + "…" : summary);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (explorationShapes != null && !explorationShapes.isBlank()) {
            sb.append("OBSERVED RESPONSE SHAPES (no values)\n").append(explorationShapes).append("\n\n");
        }
        if (!fixtureNames.isEmpty()) {
            sb.append("FIXTURE VALUES AVAILABLE: ");
            sb.append(String.join(", ", fixtureNames.stream().map(n -> "${fixture." + n + "}").toList()));
            sb.append("\n\n");
        }
        sb.append("TASK\n").append(taskSection).append('\n');
        return sb.toString();
    }

    /** Other operations whose path starts with the same static segments as the operation (at most 2). */
    static List<OperationSlice> related(NormalisedSpec spec, OperationSlice op) {
        String prefix = staticPrefix(op.path());
        List<OperationSlice> out = new ArrayList<>();
        for (OperationSlice s : spec.operations()) {
            if (s != op && !prefix.isEmpty() && staticPrefix(s.path()).equals(prefix)) {
                out.add(s);
                if (out.size() == MAX_RELATED) {
                    break;
                }
            }
        }
        return out;
    }

    /** First two static (non-parameter) leading segments, e.g. /v4.0.0/libpod/containers/{name}/start → /v4.0.0/libpod. */
    static String staticPrefix(String path) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (seg.startsWith("{") || n == 2) {
                break;
            }
            sb.append('/').append(seg);
            n++;
        }
        return sb.toString();
    }
}
