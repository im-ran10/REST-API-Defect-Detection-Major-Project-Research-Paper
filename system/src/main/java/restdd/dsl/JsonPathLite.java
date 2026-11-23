package restdd.dsl;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal path language of the test format: {@code $}, {@code $.a.b}, {@code $.a[0]}, {@code $.a[*].b}.
 * {@code [*]} expands to every element; an assertion on such a path must hold for all of them.
 */
public final class JsonPathLite {

    private static final Pattern SEGMENT = Pattern.compile("\\.([A-Za-z0-9_@$\\-]+)|\\[(\\d+|\\*)\\]");

    /** Parsed path: segments are field names, integer indexes or "*". */
    public record Parsed(List<String> segments, boolean wildcard) {}

    private JsonPathLite() {}

    /** Returns null if the path is not valid in this language. */
    public static Parsed parse(String path) {
        if (path == null || !path.startsWith("$")) {
            return null;
        }
        List<String> segs = new ArrayList<>();
        boolean wildcard = false;
        Matcher m = SEGMENT.matcher(path);
        int pos = 1;
        while (pos < path.length()) {
            if (!m.find(pos) || m.start() != pos) {
                return null;
            }
            String seg = m.group(1) != null ? m.group(1) : "[" + m.group(2) + "]";
            wildcard |= "[*]".equals(seg);
            segs.add(seg);
            pos = m.end();
        }
        return new Parsed(List.copyOf(segs), wildcard);
    }

    /** Values at the path (several when it contains [*]); empty list when the path does not exist. */
    public static List<JsonNode> evaluate(JsonNode root, Parsed p) {
        List<JsonNode> cur = new ArrayList<>(List.of(root));
        for (String seg : p.segments()) {
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode n : cur) {
                if (seg.equals("[*]")) {
                    if (n.isArray()) {
                        n.forEach(next::add);
                    }
                } else if (seg.startsWith("[")) {
                    int i = Integer.parseInt(seg.substring(1, seg.length() - 1));
                    if (n.isArray() && i < n.size()) {
                        next.add(n.get(i));
                    }
                } else if (n.isObject() && n.has(seg)) {
                    next.add(n.get(seg));
                }
            }
            cur = next;
        }
        return cur;
    }
}
