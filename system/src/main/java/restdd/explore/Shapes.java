package restdd.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Shape-only view of JSON responses (EXPERIMENT_PROTOCOL §6, D13.1): keys, JSON types, nesting and format tags.
 * <b>No value ever leaves this class.</b> Objects whose keys look like data (numeric or UUID-like keys, or more
 * than {@value #MAP_KEYS} keys) become {@code map<string, …>} so that IDs used as keys do not leak.
 *
 * <p>Representation: a scalar is a text node ({@code "string"}, {@code "string:date-time"}, {@code "integer"},
 * {@code "number"}, {@code "boolean"}, {@code "null"}, {@code "mixed"}); an object is {@code {"{}": {key: shape}}};
 * a map is {@code {"map": shape}}; an array is {@code {"[]": shape}} ({@code {"[]": null}} when empty).
 */
public final class Shapes {

    static final int MAX_DEPTH = 6;
    static final int MAP_KEYS = 40;
    static final int ARRAY_SAMPLE = 5;
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}.*");
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HEX_ID = Pattern.compile("^[0-9a-f]{24,64}$");
    private static final Pattern INT_STRING = Pattern.compile("^-?\\d{1,18}$");
    private static final Pattern NUM_STRING = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern URI = Pattern.compile("^(https?|ftp)://.*");
    private static final Pattern DATA_KEY = Pattern.compile("^(-?\\d+|[0-9a-fA-F-]{20,})$");
    private static final Pattern ID_LIKE = Pattern.compile("(?i)^(id|uuid|rowid|_id|.*_id|.*Id|.*ID)$");

    private Shapes() {}

    public static JsonNode of(JsonNode v) {
        return of(v, 0);
    }

    private static JsonNode of(JsonNode v, int depth) {
        if (v == null || v.isMissingNode() || v.isNull()) {
            return TextNode.valueOf("null");
        }
        if (v.isBoolean()) {
            return TextNode.valueOf("boolean");
        }
        if (v.isIntegralNumber()) {
            return TextNode.valueOf("integer");
        }
        if (v.isNumber()) {
            return TextNode.valueOf("number");
        }
        if (v.isTextual()) {
            return TextNode.valueOf(stringTag(v.asText()));
        }
        if (depth >= MAX_DEPTH) {
            return TextNode.valueOf(v.isArray() ? "array" : "object");
        }
        if (v.isArray()) {
            JsonNode item = null;
            int i = 0;
            for (JsonNode e : v) {
                if (i++ == ARRAY_SAMPLE) {
                    break;
                }
                item = item == null ? of(e, depth + 1) : merge(item, of(e, depth + 1));
            }
            ObjectNode a = F.objectNode();
            a.set("[]", item == null ? F.nullNode() : item);
            return a;
        }
        if (isMapLike(v)) {
            JsonNode item = null;
            int i = 0;
            for (Iterator<JsonNode> it = v.elements(); it.hasNext() && i++ < ARRAY_SAMPLE; ) {
                JsonNode s = of(it.next(), depth + 1);
                item = item == null ? s : merge(item, s);
            }
            ObjectNode m = F.objectNode();
            m.set("map", item == null ? F.nullNode() : item);
            return m;
        }
        ObjectNode fields = F.objectNode();
        Map<String, JsonNode> sorted = new TreeMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = v.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            sorted.put(e.getKey(), of(e.getValue(), depth + 1));
        }
        sorted.forEach(fields::set);
        ObjectNode o = F.objectNode();
        o.set("{}", fields);
        return o;
    }

    static boolean isMapLike(JsonNode o) {
        if (o.size() > MAP_KEYS) {
            return true;
        }
        if (o.size() < 2) {
            return false;
        }
        for (Iterator<String> it = o.fieldNames(); it.hasNext(); ) {
            if (!DATA_KEY.matcher(it.next()).matches()) {
                return false;
            }
        }
        return true;
    }

    static String stringTag(String s) {
        if (DATE_TIME.matcher(s).matches()) return "string:date-time";
        if (DATE.matcher(s).matches()) return "string:date";
        if (UUID.matcher(s).matches()) return "string:uuid";
        if (HEX_ID.matcher(s).matches()) return "string:hex-id";
        if (INT_STRING.matcher(s).matches()) return "string:integer";
        if (NUM_STRING.matcher(s).matches()) return "string:number";
        if (EMAIL.matcher(s).matches()) return "string:email";
        if (URI.matcher(s).matches()) return "string:uri";
        return "string";
    }

    private static String kind(JsonNode s) {
        if (s == null || s.isNull()) return "empty";
        if (s.isTextual()) return "scalar";
        if (s.has("{}")) return "object";
        if (s.has("map")) return "map";
        return "array";
    }

    /** Union of two shapes (array elements, map values). */
    static JsonNode merge(JsonNode a, JsonNode b) {
        if (a == null || a.isNull()) return b;
        if (b == null || b.isNull()) return a;
        String ka = kind(a), kb = kind(b);
        if (!ka.equals(kb)) {
            if (a.isTextual() && a.asText().equals("null")) return b;
            if (b.isTextual() && b.asText().equals("null")) return a;
            return TextNode.valueOf("mixed");
        }
        switch (ka) {
            case "scalar" -> {
                if (a.asText().equals(b.asText())) return a;
                String x = a.asText(), y = b.asText();
                if (x.equals("null")) return b;
                if (y.equals("null")) return a;
                if (x.startsWith("string") && y.startsWith("string")) return TextNode.valueOf("string");
                if ((x.equals("integer") && y.equals("number")) || (x.equals("number") && y.equals("integer"))) {
                    return TextNode.valueOf("number");
                }
                return TextNode.valueOf("mixed");
            }
            case "object" -> {
                Map<String, JsonNode> m = new TreeMap<>();
                a.get("{}").fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue()));
                b.get("{}").fields().forEachRemaining(e -> m.merge(e.getKey(), e.getValue(), Shapes::merge));
                ObjectNode f = F.objectNode();
                m.forEach(f::set);
                ObjectNode o = F.objectNode();
                o.set("{}", f);
                return o;
            }
            case "map" -> {
                ObjectNode o = F.objectNode();
                o.set("map", merge(a.get("map"), b.get("map")));
                return o;
            }
            default -> {
                ObjectNode o = F.objectNode();
                JsonNode m = merge(a.get("[]"), b.get("[]"));
                o.set("[]", m == null ? F.nullNode() : m);
                return o;
            }
        }
    }

    /**
     * Fields present in both runs (§6 reproducibility rule); differing scalar types become {@code mixed}.
     * Paths dropped or changed are appended to {@code differences}.
     */
    static JsonNode intersect(JsonNode a, JsonNode b, String at, List<String> differences) {
        if (a == null || b == null) {
            differences.add(at + ": seen in one run only");
            return null;
        }
        String ka = kind(a), kb = kind(b);
        if (!ka.equals(kb)) {
            differences.add(at + ": " + ka + " vs " + kb);
            return TextNode.valueOf("mixed");
        }
        switch (ka) {
            case "empty" -> {
                return a;
            }
            case "scalar" -> {
                if (!a.asText().equals(b.asText())) {
                    differences.add(at + ": " + a.asText() + " vs " + b.asText());
                    return merge(a, b);
                }
                return a;
            }
            case "object" -> {
                ObjectNode f = F.objectNode();
                JsonNode fa = a.get("{}"), fb = b.get("{}");
                for (Iterator<String> it = fa.fieldNames(); it.hasNext(); ) {
                    String k = it.next();
                    if (fb.has(k)) {
                        JsonNode s = intersect(fa.get(k), fb.get(k), at + "." + k, differences);
                        if (s != null) {
                            f.set(k, s);
                        }
                    } else {
                        differences.add(at + "." + k + ": first run only");
                    }
                }
                for (Iterator<String> it = fb.fieldNames(); it.hasNext(); ) {
                    String k = it.next();
                    if (!fa.has(k)) {
                        differences.add(at + "." + k + ": second run only");
                    }
                }
                ObjectNode o = F.objectNode();
                o.set("{}", f);
                return o;
            }
            case "map" -> {
                ObjectNode o = F.objectNode();
                JsonNode s = intersect(a.get("map"), b.get("map"), at + "<map>", differences);
                o.set("map", s == null ? F.nullNode() : s);
                return o;
            }
            default -> {
                ObjectNode o = F.objectNode();
                JsonNode ia = a.get("[]"), ib = b.get("[]");
                JsonNode s = (ia.isNull() || ib.isNull()) ? (ia.isNull() ? ib : ia)
                        : intersect(ia, ib, at + "[]", differences);
                o.set("[]", s == null ? F.nullNode() : s);
                return o;
            }
        }
    }

    /** Compact text for prompts, e.g. {@code {id: integer, ref: string, lines: [{qty: string:number}]}}. */
    public static String render(JsonNode s, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        render(s, 0, maxDepth, sb);
        return sb.toString();
    }

    private static void render(JsonNode s, int depth, int maxDepth, StringBuilder sb) {
        switch (kind(s)) {
            case "empty" -> sb.append("unknown");
            case "scalar" -> sb.append(s.asText());
            case "map" -> {
                sb.append("map<string, ");
                render(s.get("map"), depth + 1, maxDepth, sb);
                sb.append('>');
            }
            case "array" -> {
                sb.append('[');
                render(s.get("[]"), depth + 1, maxDepth, sb);
                sb.append(']');
            }
            default -> {
                if (depth >= maxDepth) {
                    sb.append("{…}");
                    return;
                }
                sb.append('{');
                List<String> parts = new ArrayList<>();
                s.get("{}").fields().forEachRemaining(e -> {
                    StringBuilder p = new StringBuilder(e.getKey()).append(": ");
                    render(e.getValue(), depth + 1, maxDepth, p);
                    parts.add(p.toString());
                });
                sb.append(String.join(", ", parts)).append('}');
            }
        }
    }

    /** Code-computed feature: an id-like key at the top level or in the first array/map level. */
    public static boolean hasIdLikeField(JsonNode s) {
        JsonNode top = unwrap(s);
        if (top != null && top.has("{}")) {
            for (Iterator<String> it = top.get("{}").fieldNames(); it.hasNext(); ) {
                if (ID_LIKE.matcher(it.next()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Code-computed feature: an array of id-bearing objects, or an id-named array of scalars, anywhere. */
    public static boolean hasReferenceArray(JsonNode s) {
        return refArray(s, null, 0);
    }

    private static boolean refArray(JsonNode s, String key, int depth) {
        if (s == null || s.isNull() || s.isTextual() || depth > MAX_DEPTH) {
            return false;
        }
        if (s.has("[]")) {
            JsonNode item = s.get("[]");
            if (item != null && item.has("{}") && hasIdLikeField(item)) {
                return true;
            }
            if (item != null && item.isTextual() && key != null && key.toLowerCase().matches(".*(ids|_id)$")) {
                return true;
            }
            return refArray(item, key, depth + 1);
        }
        if (s.has("map")) {
            return refArray(s.get("map"), key, depth + 1);
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = s.get("{}").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (refArray(e.getValue(), e.getKey(), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** The element shape of a top-level array or of a wrapped list ({@code list}, {@code data}, {@code items}). */
    static JsonNode unwrap(JsonNode s) {
        if (s == null || s.isNull() || s.isTextual()) {
            return s;
        }
        if (s.has("[]")) {
            return s.get("[]");
        }
        if (s.has("{}")) {
            for (String w : List.of("list", "data", "items", "results")) {
                JsonNode x = s.get("{}").get(w);
                if (x != null && x.has("[]")) {
                    return x.get("[]");
                }
            }
        }
        return s;
    }
}
