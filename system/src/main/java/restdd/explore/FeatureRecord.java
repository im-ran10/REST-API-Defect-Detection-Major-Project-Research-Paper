package restdd.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The shared exploration feature record of one project (D5.1 input 2, D13.1): one file, hashed, identical for
 * every arm and seed. Rendered per operation for the prompt's OBSERVED RESPONSE SHAPES section: the operation
 * itself (if it is an observed GET) and then its related operations, as one line each, within a size cap.
 */
public final class FeatureRecord {

    public static final int LINE_CAP = 600;
    public static final int TOTAL_CAP = 2500;
    static final int RENDER_DEPTH = 3;

    private final JsonNode record;

    public FeatureRecord(JsonNode record) {
        this.record = record;
    }

    public static FeatureRecord load(Path file) throws IOException {
        return new FeatureRecord(new ObjectMapper().readTree(file.toFile()));
    }

    public String sha256() {
        return record.path("recordSha256").asText();
    }

    public JsonNode operation(String key) {
        return record.path("operations").path(key);
    }

    /** Observed lines for the operation under test and its related operations (GET observations only). */
    public String forPrompt(OperationSlice op, List<OperationSlice> related) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(op.key());
        for (OperationSlice r : related) {
            keys.add(r.key());
        }
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (String key : keys) {
            String line = line(key);
            if (line == null) {
                continue;
            }
            if (total + line.length() + 1 > TOTAL_CAP) {
                break;
            }
            lines.add(line);
            total += line.length() + 1;
        }
        return String.join("\n", lines);
    }

    String line(String key) {
        JsonNode o = operation(key);
        if (o.isMissingNode()) {
            return null;
        }
        String kind = o.path("kind").asText();
        int status = o.path("status").asInt();
        String body = switch (kind) {
            case "json" -> Shapes.render(o.get("shape"), RENDER_DEPTH);
            case "text" -> "(text body)";
            case "empty" -> "(empty body)";
            default -> null;
        };
        if (body == null || status <= 0) {
            return null;
        }
        String line = key + " -> " + status + " " + body;
        return line.length() > LINE_CAP ? line.substring(0, LINE_CAP - 1) + "…" : line;
    }
}
