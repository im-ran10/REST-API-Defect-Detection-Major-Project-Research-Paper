package restdd.dsl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The test format (T2): a JSON schema shared by all arms and passed to the model as the structured-output
 * {@code format} (D27). One call returns exactly {@code s} tests, test {@code i} filling slot {@code i}.
 *
 * <pre>
 * {"tests": [ {"title": "...", "steps": [ {
 *     "method": "POST", "path": "/notes/{id}",
 *     "pathParams": {"id": "${noteId}"}, "query": {...}, "headers": {...},
 *     "auth": "default" | "none" | "secondary",
 *     "body": any JSON, "multipart": {"fields": {...}, "fileField": "f", "fileName": "a.txt", "fileContent": "hi"},
 *     "extract": {"noteId": "$.id"},
 *     "assertions": [ {"type": "status", "op": "in", "value": [200, 201]},
 *                     {"type": "body", "path": "$.items[*].status", "op": "equals", "value": "open"},
 *                     {"type": "header", "name": "Content-Type", "op": "contains", "value": "json"},
 *                     {"type": "responseSchema", "op": "conformsToSpec"},
 *                     {"type": "compare", "left": "${a}", "op": "equals", "right": "${b}"} ] } ] } ]}
 * </pre>
 *
 * References: {@code ${name}} = a value extracted by an earlier step of the same test;
 * {@code ${fixture.name}} = a runner-supplied fixture value. Paths: {@code $}, {@code $.a.b}, {@code $.a[0]},
 * {@code $.a[*].b} (the assertion must hold for every element).
 */
public final class SuiteSchema {

    public static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    public static final List<String> AUTH = List.of("default", "none", "secondary");
    public static final List<String> TYPES = List.of("status", "body", "header", "responseSchema", "compare");
    public static final List<String> OPS = List.of("equals", "notEquals", "in", "notIn", "exists", "notExists",
            "contains", "notContains", "matches", "isType", "gt", "gte", "lt", "lte", "lengthEquals", "lengthGte",
            "lengthLte", "isEmpty", "isNotEmpty", "conformsToSpec");

    public static final int MAX_STEPS = 6;
    public static final int MAX_ASSERTIONS = 6;

    /**
     * D51: length caps on text values. They stop the degenerate repetition loops seen in G2 (E52: a query value
     * of 2,000 zeros until the output cap) through the structured-output grammar; identical for all arms, and far
     * above any realistic test value. JSON bodies and assertion values (any JSON) are not capped.
     */
    public static final int MAX_TEXT = 200;
    public static final int MAX_TITLE = 150;
    public static final int MAX_PATH = 300;
    public static final int MAX_FILE_CONTENT = 2000;

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private SuiteSchema() {}

    /** Schema for a response holding exactly {@code s} tests. */
    public static ObjectNode build(int s) {
        if (s < 1) {
            throw new IllegalArgumentException("s must be >= 1");
        }
        ObjectNode assertion = assertionSchema();

        ObjectNode multipart = obj();
        ObjectNode mp = multipart.putObject("properties");
        mp.set("fields", stringMap());
        mp.set("fileField", str(MAX_TEXT));
        mp.set("fileName", str(MAX_TEXT));
        mp.set("fileContent", str(MAX_FILE_CONTENT));

        ObjectNode step = obj();
        ObjectNode sp = step.putObject("properties");
        sp.set("method", enumOf(METHODS));
        sp.set("path", str(MAX_PATH));
        sp.set("pathParams", stringMap());
        sp.set("query", stringMap());
        sp.set("headers", stringMap());
        sp.set("auth", enumOf(AUTH));
        sp.putObject("body");
        sp.set("multipart", multipart);
        sp.set("extract", stringMap());
        sp.set("assertions", arrayOf(assertion, 0, MAX_ASSERTIONS));
        required(step, "method", "path", "assertions");

        ObjectNode test = obj();
        ObjectNode tp = test.putObject("properties");
        tp.set("title", str(MAX_TITLE));
        tp.set("steps", arrayOf(step, 1, MAX_STEPS));
        required(test, "title", "steps");

        ObjectNode schema = obj();
        schema.putObject("properties").set("tests", arrayOf(test, s, s));
        required(schema, "tests");
        return schema;
    }

    /** Operators that take no {@code value} (StaticValidator.needsValue). */
    static final List<String> NO_VALUE_OPS = List.of("exists", "notExists", "isEmpty", "isNotEmpty");
    static final List<String> STATUS_OPS = List.of("equals", "notEquals", "in", "notIn", "gt", "gte", "lt", "lte");

    /**
     * D53: one alternative per assertion type, carrying exactly the rules the static validator already applies
     * (status: status operators and a value; body: a path, no conformsToSpec, a value unless the operator takes none;
     * header: a name, the same value rule; responseSchema: conformsToSpec only; compare: left and right). The grammar
     * then cannot produce, e.g., a body assertion without a path. Same schema for every model and arm.
     */
    static ObjectNode assertionSchema() {
        List<String> valueOps = OPS.stream().filter(o -> !NO_VALUE_OPS.contains(o) && !o.equals("conformsToSpec")).toList();
        ObjectNode a = F.objectNode();
        ArrayNode any = a.putArray("anyOf");
        any.add(variant("status", STATUS_OPS, List.of("value")));
        any.add(variant("body", valueOps, List.of("path", "value")));
        any.add(variant("body", NO_VALUE_OPS, List.of("path")));
        any.add(variant("header", valueOps, List.of("name", "value")));
        any.add(variant("header", NO_VALUE_OPS, List.of("name")));
        any.add(variant("responseSchema", List.of("conformsToSpec"), List.of()));
        any.add(variant("compare", OPS, List.of("left", "right")));
        return a;
    }

    private static ObjectNode variant(String type, List<String> ops, List<String> fields) {
        ObjectNode v = obj();
        ObjectNode p = v.putObject("properties");
        p.set("type", enumOf(List.of(type)));
        p.set("op", enumOf(ops));
        List<String> req = new java.util.ArrayList<>(List.of("type", "op"));
        for (String f : fields) {
            if (f.equals("value")) {
                p.putObject("value");
            } else {
                p.set(f, str(MAX_TEXT));
            }
            req.add(f);
        }
        required(v, req.toArray(String[]::new));
        v.put("additionalProperties", false);
        return v;
    }

    private static ObjectNode obj() {
        ObjectNode o = F.objectNode();
        o.put("type", "object");
        return o;
    }

    private static ObjectNode str() {
        return F.objectNode().put("type", "string");
    }

    private static ObjectNode str(int maxLength) {
        return str().put("maxLength", maxLength);
    }

    private static ObjectNode stringMap() {
        ObjectNode o = obj();
        o.set("additionalProperties", str(MAX_TEXT));
        return o;
    }

    private static ObjectNode enumOf(List<String> values) {
        ObjectNode o = str();
        ArrayNode a = o.putArray("enum");
        values.forEach(a::add);
        return o;
    }

    private static ObjectNode arrayOf(ObjectNode items, int min, int max) {
        ObjectNode o = F.objectNode();
        o.put("type", "array");
        o.set("items", items);
        o.put("minItems", min);
        o.put("maxItems", max);
        return o;
    }

    private static void required(ObjectNode o, String... names) {
        ArrayNode r = o.putArray("required");
        for (String n : names) {
            r.add(n);
        }
    }
}
