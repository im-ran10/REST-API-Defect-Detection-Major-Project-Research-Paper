package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import restdd.dsl.JsonPathLite;
import restdd.dsl.StaticValidator;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates the assertions of one step against its response.
 *
 * <p>Semantics (fixed before any experiment; stated in ARCHITECTURE):
 * <ul>
 *   <li>Scalar equality is lenient about representation: {@code 5}, {@code 5.0} and {@code "5"} are equal; objects
 *       and arrays are compared structurally.</li>
 *   <li>A path with {@code [*]} must hold for <em>every</em> element. If the path matches nothing, every operator
 *       fails except {@code notExists} (no vacuous truth).</li>
 *   <li>{@code matches} searches the text with a Java regular expression (anchor with ^...$ for a full match).</li>
 *   <li>{@code responseSchema conformsToSpec}: the status must be documented (exact code, {@code nXX} range or
 *       {@code default}); if that response declares a JSON body, the body must satisfy its schema.</li>
 * </ul>
 */
final class AssertionEvaluator {

    record Result(int index, String type, String op, boolean passed, String detail) {}

    /** What an assertion can see: the step's response and the values defined so far. */
    record Response(int status, Map<String, List<String>> headers, JsonNode json, String text) {}

    private final Refs refs;

    AssertionEvaluator(Refs refs) {
        this.refs = refs;
    }

    List<Result> evaluate(JsonNode assertions, Response r, OperationSlice op) {
        List<Result> out = new ArrayList<>();
        int i = 0;
        for (JsonNode a : assertions) {
            out.add(evaluateOne(i++, a, r, op));
        }
        return out;
    }

    private Result evaluateOne(int i, JsonNode a, Response r, OperationSlice op) {
        String type = a.path("type").asText();
        String opName = a.path("op").asText();
        try {
            JsonNode expected = a.has("value") ? refs.substituteJson(a.get("value")) : MissingNode.getInstance();
            return switch (type) {
                case "status" -> check(i, type, opName, List.of(r.status() == 0 ? MissingNode.getInstance()
                        : com.fasterxml.jackson.databind.node.IntNode.valueOf(r.status())), expected, "status " + r.status());
                case "body" -> {
                    if (r.json() == null) {
                        boolean pass = opName.equals("notExists");
                        yield new Result(i, type, opName, pass, "response body is not JSON");
                    }
                    String path = a.path("path").asText();
                    List<JsonNode> values = JsonPathLite.evaluate(r.json(), JsonPathLite.parse(path));
                    yield check(i, type, opName, values, expected, path);
                }
                case "header" -> {
                    String name = a.path("name").asText();
                    List<String> vals = headerValues(r.headers(), name);
                    List<JsonNode> values = vals.isEmpty() ? List.of() : List.of(TextNode.valueOf(String.join(", ", vals)));
                    yield check(i, type, opName, values, expected, "header " + name);
                }
                case "responseSchema" -> conformsToSpec(i, r, op);
                case "compare" -> {
                    JsonNode left = refs.resolve(a.path("left").asText());
                    JsonNode right = refs.resolve(a.path("right").asText());
                    yield check(i, type, opName, List.of(left), right, "compare");
                }
                default -> new Result(i, type, opName, false, "unknown assertion type");
            };
        } catch (Refs.MissingRef e) {
            return new Result(i, type, opName, false, "undefined reference ${" + e.getMessage() + "}");
        }
    }

    /** Applies {@code op} to every value; empty values pass only {@code notExists}. */
    static Result check(int i, String type, String op, List<JsonNode> values, JsonNode expected, String what) {
        List<JsonNode> present = values.stream().filter(v -> !v.isMissingNode()).toList();
        if (op.equals("exists")) {
            return new Result(i, type, op, !present.isEmpty(), what + (present.isEmpty() ? " not found" : " found"));
        }
        if (op.equals("notExists")) {
            return new Result(i, type, op, present.isEmpty(), what + (present.isEmpty() ? " not found" : " found"));
        }
        if (present.isEmpty()) {
            return new Result(i, type, op, false, what + " not found");
        }
        for (JsonNode v : present) {
            String fail = apply(op, v, expected);
            if (fail != null) {
                return new Result(i, type, op, false, what + ": " + fail);
            }
        }
        return new Result(i, type, op, true, what + ": ok (" + present.size() + " value" + (present.size() == 1 ? ")" : "s)"));
    }

    /** Null if {@code actual op expected} holds, otherwise a short reason. */
    static String apply(String op, JsonNode actual, JsonNode expected) {
        switch (op) {
            case "equals" -> {
                return same(actual, expected) ? null : "is " + show(actual) + ", expected " + show(expected);
            }
            case "notEquals" -> {
                return !same(actual, expected) ? null : "is " + show(actual);
            }
            case "in", "notIn" -> {
                boolean found = false;
                if (expected.isArray()) {
                    for (JsonNode e : expected) {
                        found |= same(actual, e);
                    }
                } else {
                    found = same(actual, expected);
                }
                boolean want = op.equals("in");
                return found == want ? null : "is " + show(actual) + (want ? ", not in " : ", in ") + show(expected);
            }
            case "contains", "notContains" -> {
                boolean has = contains(actual, expected);
                boolean want = op.equals("contains");
                return has == want ? null : show(actual) + (want ? " does not contain " : " contains ") + show(expected);
            }
            case "matches" -> {
                try {
                    String text = scalarText(actual);
                    if (text == null) {
                        return "is not a scalar";
                    }
                    String t = text.length() > 10_000 ? text.substring(0, 10_000) : text;
                    return Pattern.compile(expected.asText()).matcher(t).find() ? null
                            : show(actual) + " does not match " + expected.asText();
                } catch (PatternSyntaxException e) {
                    return "invalid regular expression";
                }
            }
            case "isType" -> {
                return isType(actual, expected.asText()) ? null : "is " + typeOf(actual) + ", expected " + expected.asText();
            }
            case "gt", "gte", "lt", "lte" -> {
                BigDecimal x = number(actual);
                BigDecimal y = number(expected);
                if (x == null || y == null) {
                    return "not comparable as numbers: " + show(actual) + " vs " + show(expected);
                }
                int c = x.compareTo(y);
                boolean ok = switch (op) {
                    case "gt" -> c > 0;
                    case "gte" -> c >= 0;
                    case "lt" -> c < 0;
                    default -> c <= 0;
                };
                return ok ? null : show(actual) + " is not " + op + " " + show(expected);
            }
            case "lengthEquals", "lengthGte", "lengthLte" -> {
                int len = length(actual);
                BigDecimal y = number(expected);
                if (len < 0 || y == null) {
                    return "length not defined for " + typeOf(actual);
                }
                int c = Integer.compare(len, y.intValue());
                boolean ok = op.equals("lengthEquals") ? c == 0 : op.equals("lengthGte") ? c >= 0 : c <= 0;
                return ok ? null : "length " + len + " is not " + op.substring(6).toLowerCase() + " " + y.intValue();
            }
            case "isEmpty" -> {
                return isEmpty(actual) ? null : show(actual) + " is not empty";
            }
            case "isNotEmpty" -> {
                return !isEmpty(actual) ? null : "is empty";
            }
            case "conformsToSpec" -> {
                return "conformsToSpec is only valid with type responseSchema";
            }
            default -> {
                return "unknown operator " + op;
            }
        }
    }

    static boolean same(JsonNode a, JsonNode b) {
        if (a == null || b == null || a.isMissingNode() || b.isMissingNode()) {
            return false;
        }
        if (a.isValueNode() && b.isValueNode() && !a.isNull() && !b.isNull()) {
            BigDecimal x = number(a);
            BigDecimal y = number(b);
            if (x != null && y != null && (a.isNumber() || b.isNumber())) {
                return x.compareTo(y) == 0;
            }
            return a.asText().equals(b.asText());
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        return a.equals(b);
    }

    private static boolean contains(JsonNode actual, JsonNode expected) {
        if (actual.isArray()) {
            for (JsonNode e : actual) {
                if (same(e, expected)) {
                    return true;
                }
            }
            return false;
        }
        if (actual.isObject()) {
            return actual.has(expected.asText());
        }
        String text = scalarText(actual);
        return text != null && text.contains(expected.asText());
    }

    private static String scalarText(JsonNode n) {
        return n.isValueNode() && !n.isNull() ? n.asText() : null;
    }

    static BigDecimal number(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isTextual()) {
            try {
                return new BigDecimal(n.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int length(JsonNode n) {
        if (n.isArray() || n.isObject()) {
            return n.size();
        }
        if (n.isTextual()) {
            return n.asText().length();
        }
        return -1;
    }

    private static boolean isEmpty(JsonNode n) {
        return n.isNull() || ((n.isArray() || n.isObject()) && n.isEmpty()) || (n.isTextual() && n.asText().isEmpty());
    }

    private static boolean isType(JsonNode n, String t) {
        return switch (t) {
            case "string" -> n.isTextual();
            case "integer" -> n.isIntegralNumber();
            case "number" -> n.isNumber();
            case "boolean" -> n.isBoolean();
            case "array" -> n.isArray();
            case "object" -> n.isObject();
            case "null" -> n.isNull();
            default -> false;
        };
    }

    private static String typeOf(JsonNode n) {
        if (n.isTextual()) return "string";
        if (n.isIntegralNumber()) return "integer";
        if (n.isNumber()) return "number";
        if (n.isBoolean()) return "boolean";
        if (n.isArray()) return "array";
        if (n.isObject()) return "object";
        if (n.isNull()) return "null";
        return "missing";
    }

    private static String show(JsonNode n) {
        String s = n == null ? "null" : n.toString();
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }

    static List<String> headerValues(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return List.of();
    }

    private static Result conformsToSpec(int i, Response r, OperationSlice op) {
        String type = "responseSchema";
        String o = "conformsToSpec";
        if (op == null) {
            return new Result(i, type, o, false, "operation not in the spec");
        }
        JsonNode responses = op.slice().path("responses");
        String code = String.valueOf(r.status());
        JsonNode resp = responses.get(code);
        if (resp == null) {
            resp = responses.get(code.charAt(0) + "XX");
        }
        if (resp == null) {
            resp = responses.get(code.charAt(0) + "xx");
        }
        if (resp == null) {
            resp = responses.get("default");
        }
        if (resp == null) {
            return new Result(i, type, o, false, "status " + code + " is not documented for " + op.key());
        }
        JsonNode schema = StaticValidator.bodySchema(resp.path("content"));
        if (schema.isMissingNode()) {
            return new Result(i, type, o, true, "status " + code + " documented; no JSON body schema to check");
        }
        if (r.json() == null) {
            return new Result(i, type, o, false, "status " + code + " documents a JSON body; response body is not JSON");
        }
        List<String> issues = StaticValidator.schemaIssues(r.json(), schema);
        return issues.isEmpty() ? new Result(i, type, o, true, "body conforms to the " + code + " schema")
                : new Result(i, type, o, false, String.join("; ", issues.subList(0, Math.min(3, issues.size()))));
    }
}
