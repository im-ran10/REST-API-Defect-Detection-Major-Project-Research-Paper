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

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private SuiteSchema() {}

    /** Schema for a response holding exactly {@code s} tests. */
    public static ObjectNode build(int s) {
        if (s < 1) {
            throw new IllegalArgumentException("s must be >= 1");
        }
        ObjectNode assertion = obj();
        ObjectNode ap = assertion.putObject("properties");
        ap.set("type", enumOf(TYPES));
        ap.set("op", enumOf(OPS));
        ap.set("path", str());
        ap.set("name", str());
        ap.putObject("value");
        ap.set("left", str());
        ap.set("right", str());
        required(assertion, "type", "op");

        ObjectNode multipart = obj();
        ObjectNode mp = multipart.putObject("properties");
        mp.set("fields", stringMap());
        mp.set("fileField", str());
        mp.set("fileName", str());
        mp.set("fileContent", str());

        ObjectNode step = obj();
        ObjectNode sp = step.putObject("properties");
        sp.set("method", enumOf(METHODS));
        sp.set("path", str());
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
        tp.set("title", str());
        tp.set("steps", arrayOf(step, 1, MAX_STEPS));
        required(test, "title", "steps");

        ObjectNode schema = obj();
        schema.putObject("properties").set("tests", arrayOf(test, s, s));
        required(schema, "tests");
        return schema;
    }

    private static ObjectNode obj() {
        ObjectNode o = F.objectNode();
        o.put("type", "object");
        return o;
    }

    private static ObjectNode str() {
        return F.objectNode().put("type", "string");
    }

    private static ObjectNode stringMap() {
        ObjectNode o = obj();
        o.set("additionalProperties", str());
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
