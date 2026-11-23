package restdd.spec;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D5.1 sanitisation step 2: scans every text value of a project-level spec for words that could steer
 * generation toward specific defects. It only <em>reports</em> hits; the owner reviews them and every
 * edit is logged (EXPERIMENT_PROTOCOL §5). Hits never change the spec automatically.
 */
public final class SpecSanitiser {

    /** One hit: JSON pointer of the text value, the matched term and a short snippet around it. */
    public record Hit(String pointer, String term, String snippet) {}

    static final Pattern KEYWORDS = Pattern.compile(
            "(?i)\\b(bug|bugs|issue|issues|fix|fixed|fixes|buggy|patched|workaround|regression|todo|fixme)\\b"
                    + "|#\\d+|https?://github\\.com/\\S+");

    private SpecSanitiser() {}

    public static List<Hit> scan(Path specFile) throws IOException {
        JsonNode root = Yaml.mapper().readTree(Files.readString(specFile));
        List<Hit> hits = new ArrayList<>();
        walk(root, "", hits);
        return hits;
    }

    private static void walk(JsonNode n, String ptr, List<Hit> hits) {
        if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                String child = ptr + "/" + e.getKey().replace("~", "~0").replace("/", "~1");
                check(e.getKey(), child + " (key)", hits);
                walk(e.getValue(), child, hits);
            }
        } else if (n.isArray()) {
            for (int i = 0; i < n.size(); i++) {
                walk(n.get(i), ptr + "/" + i, hits);
            }
        } else if (n.isTextual()) {
            check(n.asText(), ptr, hits);
        }
    }

    private static void check(String text, String ptr, List<Hit> hits) {
        Matcher m = KEYWORDS.matcher(text);
        while (m.find()) {
            int a = Math.max(0, m.start() - 30);
            int b = Math.min(text.length(), m.end() + 30);
            hits.add(new Hit(ptr, m.group(), text.substring(a, b).replace('\n', ' ')));
        }
    }
}
