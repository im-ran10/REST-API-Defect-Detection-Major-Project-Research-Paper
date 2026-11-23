package restdd.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import restdd.probe.OperationLister;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static validation of a generated suite (T2), in three layers:
 * <ol>
 *   <li><b>Schema validity</b> (T1 metric): the response parses and matches {@link SuiteSchema}.</li>
 *   <li><b>Structural validity</b>: every step targets an operation of the project spec, path parameters are
 *       supplied, {@code ${...}} references are defined earlier, assertions are well formed. Structurally
 *       invalid tests are not executed and count as the arm's own outcome (never retried, D26).</li>
 *   <li><b>Spec conformance</b> (T2 metric): required parameters present, declared parameter names and types,
 *       request body valid against its schema. Non-conformance is only <em>reported</em>: negative tests
 *       (e.g. B2) deliberately send invalid input and must still run.</li>
 * </ol>
 * It also classifies assertions for T4 and analysis A1.
 */
public final class StaticValidator {

    public enum AssertionClass { NONE, STATUS_ONLY, MEANINGFUL }

    public record TestReport(int index, String title, List<String> structuralErrors, List<String> conformanceIssues,
                             List<String> operations, AssertionClass assertionClass, int trivialAssertions,
                             boolean expectsClientError) {
        public boolean structurallyValid() {
            return structuralErrors.isEmpty();
        }

        public boolean specConformant() {
            return structurallyValid() && conformanceIssues.isEmpty();
        }
    }

    public record SuiteReport(boolean parsed, boolean schemaValid, List<String> schemaErrors, List<TestReport> tests) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern REF = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)}");

    private final NormalisedSpec spec;
    private final Set<String> fixtureNames;

    public StaticValidator(NormalisedSpec spec, Set<String> fixtureNames) {
        this.spec = spec;
        this.fixtureNames = Set.copyOf(fixtureNames);
    }

    public SuiteReport validate(String responseText, int s) {
        JsonNode root;
        try {
            root = JSON.readTree(responseText);
        } catch (Exception e) {
            return new SuiteReport(false, false, List.of("not JSON: " + e.getMessage()), List.of());
        }
        List<String> schemaErrors = new ArrayList<>();
        checkSchema(root, s, schemaErrors);
        List<TestReport> reports = new ArrayList<>();
        JsonNode tests = root.path("tests");
        if (tests.isArray()) {
            for (int i = 0; i < tests.size(); i++) {
                reports.add(validateTest(i, tests.get(i)));
            }
        }
        return new SuiteReport(true, schemaErrors.isEmpty(), List.copyOf(schemaErrors), List.copyOf(reports));
    }

    // ---------------------------------------------------------------- layer 1: schema

    static void checkSchema(JsonNode root, int s, List<String> err) {
        JsonNode tests = root.path("tests");
        if (!tests.isArray()) {
            err.add("'tests' is not an array");
            return;
        }
        if (tests.size() != s) {
            err.add("expected " + s + " tests, got " + tests.size());
        }
        for (int i = 0; i < tests.size(); i++) {
            JsonNode t = tests.get(i);
            String at = "tests[" + i + "]";
            if (!t.path("title").isTextual()) {
                err.add(at + ".title missing");
            }
            JsonNode steps = t.path("steps");
            if (!steps.isArray() || steps.isEmpty() || steps.size() > SuiteSchema.MAX_STEPS) {
                err.add(at + ".steps must have 1.." + SuiteSchema.MAX_STEPS + " items");
                continue;
            }
            for (int j = 0; j < steps.size(); j++) {
                JsonNode st = steps.get(j);
                String sat = at + ".steps[" + j + "]";
                if (!SuiteSchema.METHODS.contains(st.path("method").asText())) {
                    err.add(sat + ".method invalid");
                }
                if (!st.path("path").isTextual()) {
                    err.add(sat + ".path missing");
                }
                if (st.has("auth") && !SuiteSchema.AUTH.contains(st.get("auth").asText())) {
                    err.add(sat + ".auth invalid");
                }
                for (String m : List.of("pathParams", "query", "headers", "extract")) {
                    if (st.has(m) && !isStringMap(st.get(m))) {
                        err.add(sat + "." + m + " must map names to strings");
                    }
                }
                JsonNode as = st.path("assertions");
                if (!as.isArray() || as.size() > SuiteSchema.MAX_ASSERTIONS) {
                    err.add(sat + ".assertions must have 0.." + SuiteSchema.MAX_ASSERTIONS + " items");
                    continue;
                }
                for (JsonNode a : as) {
                    if (!SuiteSchema.TYPES.contains(a.path("type").asText())
                            || !SuiteSchema.OPS.contains(a.path("op").asText())) {
                        err.add(sat + ": assertion type/op invalid");
                    }
                }
            }
        }
    }

    private static boolean isStringMap(JsonNode n) {
        if (!n.isObject()) {
            return false;
        }
        for (Iterator<JsonNode> it = n.elements(); it.hasNext(); ) {
            if (!it.next().isTextual()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- layers 2 and 3

    private TestReport validateTest(int index, JsonNode t) {
        List<String> structural = new ArrayList<>();
        List<String> conformance = new ArrayList<>();
        List<String> ops = new ArrayList<>();
        Set<String> defined = new HashSet<>();
        int statusAssertions = 0;
        int meaningful = 0;
        int trivial = 0;
        boolean expectsClientError = false;

        JsonNode steps = t.path("steps");
        for (int j = 0; j < steps.size(); j++) {
            JsonNode st = steps.get(j);
            String at = "step " + (j + 1);
            String method = st.path("method").asText();
            String path = st.path("path").asText();
            OperationSlice op = resolveOperation(method, path);
            if (op == null) {
                structural.add(at + ": no operation " + method + " " + path + " in the project spec");
            } else {
                ops.add(op.key());
                checkPathParams(at, op, path, st.path("pathParams"), structural);
                checkConformance(at, op, st, conformance);
                if (st.has("multipart") && !st.get("multipart").isNull() && !acceptsMultipart(op)) {
                    structural.add(at + ": multipart body on an operation that does not accept multipart");
                }
            }
            checkRefs(at, st, defined, structural);
            JsonNode extract = st.path("extract");
            for (Iterator<Map.Entry<String, JsonNode>> it = extract.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (JsonPathLite.parse(e.getValue().asText()) == null) {
                    structural.add(at + ": extract path '" + e.getValue().asText() + "' invalid");
                }
                defined.add(e.getKey());
            }
            for (JsonNode a : st.path("assertions")) {
                String type = a.path("type").asText();
                String opName = a.path("op").asText();
                String err = assertionError(type, opName, a);
                if (err != null) {
                    structural.add(at + ": " + err);
                    continue;
                }
                if (type.equals("compare")) {
                    for (String side : List.of("left", "right")) {
                        checkRefText(at, a.path(side).asText(), defined, structural);
                    }
                }
                if (type.equals("status")) {
                    statusAssertions++;
                    expectsClientError |= expects4xx(a);
                } else if (isTrivial(type, opName, a)) {
                    trivial++;
                } else {
                    meaningful++;
                }
            }
        }
        AssertionClass cls = meaningful > 0 ? AssertionClass.MEANINGFUL
                : statusAssertions > 0 ? AssertionClass.STATUS_ONLY : AssertionClass.NONE;
        return new TestReport(index, t.path("title").asText(), List.copyOf(structural), List.copyOf(conformance),
                List.copyOf(ops), cls, trivial, expectsClientError);
    }

    /** Exact template match first; otherwise a concrete path matching exactly one template. */
    public OperationSlice resolveOperation(String method, String path) {
        OperationSlice exact = spec.find(method, path);
        if (exact != null) {
            return exact;
        }
        OperationSlice found = null;
        for (OperationSlice s : spec.operations()) {
            if (s.method().equalsIgnoreCase(method) && OperationLister.matchesTemplate(s.path(), path)) {
                if (found != null) {
                    return null; // ambiguous
                }
                found = s;
            }
        }
        return found;
    }

    private static void checkPathParams(String at, OperationSlice op, String usedPath, JsonNode given,
                                        List<String> structural) {
        if (!usedPath.equals(op.path())) {
            return; // concrete path: segments already filled in
        }
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(op.path());
        while (m.find()) {
            if (!given.has(m.group(1))) {
                structural.add(at + ": path parameter '" + m.group(1) + "' not supplied");
            }
        }
    }

    private void checkRefs(String at, JsonNode st, Set<String> defined, List<String> structural) {
        for (String field : List.of("path", "pathParams", "query", "headers", "body", "multipart")) {
            if (st.has(field)) {
                checkRefText(at, st.get(field).toString(), defined, structural);
            }
        }
    }

    private void checkRefText(String at, String text, Set<String> defined, List<String> structural) {
        Matcher m = REF.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            boolean ok = name.startsWith("fixture.") ? fixtureNames.contains(name.substring(8)) : defined.contains(name);
            if (!ok) {
                structural.add(at + ": reference ${" + name + "} is not defined before use");
            }
        }
    }

    static String assertionError(String type, String op, JsonNode a) {
        switch (type) {
            case "status" -> {
                if (!List.of("equals", "notEquals", "in", "notIn", "gt", "gte", "lt", "lte").contains(op)) {
                    return "status assertion with op '" + op + "'";
                }
                if (!a.has("value")) {
                    return "status assertion without value";
                }
            }
            case "body" -> {
                if (JsonPathLite.parse(a.path("path").asText(null)) == null) {
                    return "body assertion with invalid path '" + a.path("path").asText() + "'";
                }
                if (op.equals("conformsToSpec")) {
                    return "body assertion with op conformsToSpec (use type responseSchema)";
                }
                if (needsValue(op) && !a.has("value")) {
                    return "body assertion '" + op + "' without value";
                }
                if (op.equals("matches") && !validRegex(a.path("value").asText())) {
                    return "body assertion 'matches' with an invalid regular expression";
                }
            }
            case "header" -> {
                if (a.path("name").asText("").isBlank()) {
                    return "header assertion without name";
                }
                if (needsValue(op) && !a.has("value")) {
                    return "header assertion '" + op + "' without value";
                }
                if (op.equals("matches") && !validRegex(a.path("value").asText())) {
                    return "header assertion 'matches' with an invalid regular expression";
                }
            }
            case "responseSchema" -> {
                if (!op.equals("conformsToSpec")) {
                    return "responseSchema assertion must use op conformsToSpec";
                }
            }
            case "compare" -> {
                if (a.path("left").asText("").isBlank() || a.path("right").asText("").isBlank()) {
                    return "compare assertion needs left and right";
                }
            }
            default -> {
                return "unknown assertion type '" + type + "'";
            }
        }
        return null;
    }

    private static boolean validRegex(String re) {
        try {
            Pattern.compile(re);
            return true;
        } catch (java.util.regex.PatternSyntaxException e) {
            return false;
        }
    }

    /** Light schema check of a value (used by the executor for {@code responseSchema conformsToSpec}). */
    public static List<String> schemaIssues(JsonNode value, JsonNode schema) {
        List<String> issues = new ArrayList<>();
        checkValue(value, schema, "body", issues, 0);
        return List.copyOf(issues);
    }

    private static boolean needsValue(String op) {
        return !List.of("exists", "notExists", "isEmpty", "isNotEmpty", "conformsToSpec").contains(op);
    }

    /** Trivially true assertions do not count as meaningful (T4). */
    static boolean isTrivial(String type, String op, JsonNode a) {
        if (type.equals("compare")) {
            return a.path("left").asText().equals(a.path("right").asText());
        }
        if (type.equals("body") && "$".equals(a.path("path").asText()) && op.equals("exists")) {
            return true;
        }
        return false;
    }

    private static boolean expects4xx(JsonNode a) {
        JsonNode v = a.path("value");
        if (v.isInt()) {
            return v.asInt() >= 400 && v.asInt() < 500;
        }
        if (v.isArray()) {
            for (JsonNode x : v) {
                if (x.asInt() >= 400 && x.asInt() < 500) {
                    return true;
                }
            }
        }
        return a.path("op").asText().startsWith("gt") && v.asInt() >= 399 && v.asInt() < 500;
    }

    private static boolean acceptsMultipart(OperationSlice op) {
        JsonNode content = op.slice().path("requestBody").path("content");
        for (Iterator<String> it = content.fieldNames(); it.hasNext(); ) {
            if (it.next().contains("multipart")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- conformance (reported only)

    private static void checkConformance(String at, OperationSlice op, JsonNode st, List<String> issues) {
        JsonNode params = op.slice().path("parameters");
        Set<String> declaredQuery = new HashSet<>();
        Set<String> declaredHeader = new HashSet<>();
        for (JsonNode p : params) {
            String in = p.path("in").asText();
            String name = p.path("name").asText();
            JsonNode given = switch (in) {
                case "query" -> st.path("query").get(name);
                case "header" -> headerIgnoreCase(st.path("headers"), name);
                case "path" -> st.path("pathParams").get(name);
                default -> null;
            };
            if (in.equals("query")) {
                declaredQuery.add(name);
            }
            if (in.equals("header")) {
                declaredHeader.add(name.toLowerCase());
            }
            boolean pathFilled = in.equals("path") && !st.path("path").asText().contains("{" + name + "}");
            if (given == null && p.path("required").asBoolean(false) && !pathFilled) {
                issues.add(at + ": required " + in + " parameter '" + name + "' missing");
            }
            if (given != null && given.isTextual() && !given.asText().contains("${")) {
                String problem = scalarProblem(given.asText(), p.path("schema"));
                if (problem != null) {
                    issues.add(at + ": " + in + " parameter '" + name + "' " + problem);
                }
            }
        }
        for (Iterator<String> it = st.path("query").fieldNames(); it.hasNext(); ) {
            String q = it.next();
            if (!declaredQuery.contains(q)) {
                issues.add(at + ": undeclared query parameter '" + q + "'");
            }
        }
        JsonNode rb = op.slice().path("requestBody");
        boolean hasBody = st.has("body") && !st.get("body").isNull();
        if (rb.path("required").asBoolean(false) && !hasBody && !(st.has("multipart") && !st.get("multipart").isNull())) {
            issues.add(at + ": required request body missing");
        }
        if (hasBody) {
            JsonNode schema = bodySchema(rb.path("content"));
            if (!schema.isMissingNode()) {
                List<String> bodyIssues = new ArrayList<>();
                JsonNode body = st.get("body");
                if (body.isTextual()) {
                    try {
                        body = JSON.readTree(body.asText());
                    } catch (Exception ignored) {
                        // keep as text; the type check reports it
                    }
                }
                checkValue(body, schema, "body", bodyIssues, 0);
                bodyIssues.forEach(b -> issues.add(at + ": " + b));
            }
        }
    }

    /**
     * Body schema to check against: {@code application/json}, else any JSON media type, else {@code *}{@code /*}
     * (what swagger-parser assigns when a Swagger 2.0 spec declares no {@code consumes}).
     */
    public static JsonNode bodySchema(JsonNode content) {
        if (content.has("application/json")) {
            return content.get("application/json").path("schema");
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = content.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().contains("json")) {
                return e.getValue().path("schema");
            }
        }
        return content.has("*/*") ? content.get("*/*").path("schema") : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static JsonNode headerIgnoreCase(JsonNode headers, String name) {
        for (Iterator<Map.Entry<String, JsonNode>> it = headers.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    static String scalarProblem(String value, JsonNode schema) {
        String type = schema.path("type").asText("");
        try {
            switch (type) {
                case "integer" -> Long.parseLong(value);
                case "number" -> Double.parseDouble(value);
                case "boolean" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        return "is not a boolean";
                    }
                }
                default -> { }
            }
        } catch (NumberFormatException e) {
            return "is not of type " + type;
        }
        if (schema.has("enum")) {
            for (JsonNode e : schema.get("enum")) {
                if (e.asText().equals(value)) {
                    return null;
                }
            }
            return "is not one of the declared enum values";
        }
        return null;
    }

    /** Light JSON-schema check: type, enum, required, properties, items, allOf/anyOf/oneOf. */
    static void checkValue(JsonNode v, JsonNode schema, String at, List<String> issues, int depth) {
        if (depth > 12 || schema.has("$recursive") || schema.has("$truncated") || schema.has("$unresolved")) {
            return;
        }
        if (schema.has("allOf")) {
            for (JsonNode s : schema.get("allOf")) {
                checkValue(v, s, at, issues, depth + 1);
            }
        }
        for (String alt : List.of("anyOf", "oneOf")) {
            if (schema.has(alt)) {
                boolean any = false;
                for (JsonNode s : schema.get(alt)) {
                    List<String> tmp = new ArrayList<>();
                    checkValue(v, s, at, tmp, depth + 1);
                    any |= tmp.isEmpty();
                }
                if (!any) {
                    issues.add(at + " matches none of the " + alt + " alternatives");
                }
            }
        }
        if (v.isTextual() && v.asText().contains("${")) {
            return; // filled at run time
        }
        JsonNode typeNode = schema.path("type");
        List<String> types = new ArrayList<>();
        if (typeNode.isArray()) {
            typeNode.forEach(t -> types.add(t.asText()));
        } else if (typeNode.isTextual()) {
            types.add(typeNode.asText());
        }
        if (!types.isEmpty() && types.stream().noneMatch(t -> matchesType(v, t))) {
            if (!(v.isNull() && schema.path("nullable").asBoolean(false))) {
                issues.add(at + " should be " + String.join("|", types));
            }
            return;
        }
        if (schema.has("enum") && !v.isNull()) {
            boolean found = false;
            for (JsonNode e : schema.get("enum")) {
                found |= e.equals(v) || e.asText().equals(v.asText());
            }
            if (!found) {
                issues.add(at + " is not one of the enum values");
            }
        }
        if (v.isObject()) {
            for (JsonNode r : schema.path("required")) {
                if (!v.has(r.asText())) {
                    issues.add(at + "." + r.asText() + " is required");
                }
            }
            JsonNode props = schema.path("properties");
            for (Iterator<Map.Entry<String, JsonNode>> it = v.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (props.has(e.getKey())) {
                    checkValue(e.getValue(), props.get(e.getKey()), at + "." + e.getKey(), issues, depth + 1);
                }
            }
        }
        if (v.isArray() && schema.has("items")) {
            for (int i = 0; i < v.size(); i++) {
                checkValue(v.get(i), schema.get("items"), at + "[" + i + "]", issues, depth + 1);
            }
        }
    }

    private static boolean matchesType(JsonNode v, String t) {
        return switch (t) {
            case "string" -> v.isTextual();
            case "integer" -> v.isIntegralNumber();
            case "number" -> v.isNumber();
            case "boolean" -> v.isBoolean();
            case "array" -> v.isArray();
            case "object" -> v.isObject();
            case "null" -> v.isNull();
            default -> true;
        };
    }
}
