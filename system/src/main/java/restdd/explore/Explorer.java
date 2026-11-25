package restdd.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.exec.ExecConfig;
import restdd.exec.TestExecutor;
import restdd.dsl.StaticValidator;
import restdd.gen.OperationSelector;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T7: GET-only, shape-only exploration of a project's reference deployment (EXPERIMENT_PROTOCOL §6, D13.1).
 *
 * <ul>
 *   <li>Only GET requests (other methods are never built); no redirects; 10 s per request; the time budget is
 *       split over two passes.</li>
 *   <li>Operations in order of path, then method. At most 2 requests per operation: an operation with path
 *       parameters takes its values, chosen by code, from a fixture of the same name or from an element of the
 *       matching list operation's response (first element, then second if the first fails).</li>
 *   <li>Required query parameters get a value from the spec (default, enum, example) or the operation is
 *       skipped.</li>
 *   <li>Only shapes are kept (see {@link Shapes}); response bodies and chosen identifiers stay in memory.</li>
 *   <li>Two passes; the record keeps the fields seen in both and lists the differences.</li>
 * </ul>
 */
public final class Explorer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}/]+)}");
    private static final List<String> ID_KEYS = List.of("id", "rowid", "uuid", "code", "ref", "acronym", "key", "name",
            "Names"); // matched case-insensitively

    private final NormalisedSpec spec;
    private final ExecConfig config;
    private final TestExecutor executor;
    private final Duration budget;

    record Observation(int status, String kind, JsonNode shape, String note) {}

    public Explorer(NormalisedSpec spec, ExecConfig target, Duration budget) {
        this.spec = spec;
        // explorer limits (§6): 10 s per request, bodies kept whole in memory for shape extraction
        this.config = new ExecConfig(target.baseUrl(), target.defaultAuth(), target.secondaryAuth(), target.fixtures(),
                Duration.ofSeconds(10), Duration.ofSeconds(10), 4_000_000, 4_000_000);
        this.executor = new TestExecutor(config, new StaticValidator(spec, java.util.Set.of()));
        this.budget = budget;
    }

    public List<OperationSlice> getOperations() {
        List<OperationSlice> ops = new ArrayList<>(spec.operations().stream().filter(o -> o.method().equals("GET")).toList());
        ops.sort(Comparator.comparing(OperationSlice::path).thenComparing(OperationSlice::method));
        return ops;
    }

    /** Runs two passes and returns the shared feature record. */
    public ObjectNode explore(String project) {
        Duration half = budget.dividedBy(2);
        long t0 = System.nanoTime();
        Map<String, Observation> a = pass(half);
        Map<String, Observation> b = pass(half);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        ObjectNode rec = JSON.createObjectNode();
        rec.put("project", project);
        rec.put("createdAt", Instant.now().toString());
        rec.put("specSha256", spec.sourceSha256());
        rec.put("passes", 2);
        rec.put("budgetSeconds", budget.toSeconds());
        rec.put("elapsedMs", ms);
        ObjectNode ops = rec.putObject("operations");
        ArrayNode diffs = rec.putArray("differences");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OperationSlice op : getOperations()) {
            Observation x = a.get(op.key());
            Observation y = b.get(op.key());
            ObjectNode o = ops.putObject(op.key());
            if (x == null || y == null) {
                o.put("kind", "not-reached");
                o.put("note", "time budget reached");
                counts.merge("not-reached", 1, Integer::sum);
                continue;
            }
            o.put("status", x.status());
            if (x.status() != y.status()) {
                o.put("statusSecondRun", y.status());
                diffs.add(op.key() + ": status " + x.status() + " vs " + y.status());
            }
            String kind = x.kind().equals(y.kind()) ? x.kind() : "mixed";
            o.put("kind", kind);
            if (x.note() != null) {
                o.put("note", x.note());
            }
            if (kind.equals("json")) {
                List<String> d = new ArrayList<>();
                JsonNode shape = Shapes.intersect(x.shape(), y.shape(), op.key() + " $", d);
                d.forEach(diffs::add);
                o.set("shape", shape);
                o.put("hasIdLikeField", Shapes.hasIdLikeField(shape));
                o.put("hasReferenceArray", Shapes.hasReferenceArray(shape));
            }
            counts.merge(kind, 1, Integer::sum);
        }
        ObjectNode summary = rec.putObject("summary");
        summary.put("getOperations", getOperations().size());
        counts.forEach(summary::put);
        summary.put("differences", diffs.size());
        rec.put("recordSha256", OperationSelector.sha256(ops.toString()));
        return rec;
    }

    private Map<String, Observation> pass(Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        Map<String, Observation> seen = new LinkedHashMap<>();
        Map<String, JsonNode> bodies = new HashMap<>(); // in memory only: list responses, for choosing identifiers
        for (OperationSlice op : getOperations()) {
            if (System.nanoTime() > deadline) {
                break;
            }
            Map<String, String> query = new LinkedHashMap<>();
            String missing = requiredQuery(op, query);
            if (missing != null) {
                seen.put(op.key(), new Observation(0, "skipped", null, "required query parameter without a spec value: " + missing));
                continue;
            }
            List<String> names = placeholders(op.path());
            if (names.isEmpty()) {
                Response r = get(op.path(), Map.of(), query);
                seen.put(op.key(), r.observation(null));
                if (r.json != null && r.status >= 200 && r.status < 300) {
                    bodies.put(op.path(), r.json);
                }
                continue;
            }
            List<Map<String, String>> candidates = candidates(op.path(), names, bodies);
            if (candidates.isEmpty()) {
                seen.put(op.key(), new Observation(0, "skipped", null, "no identifier available"));
                continue;
            }
            Response r = null;
            for (int i = 0; i < Math.min(2, candidates.size()); i++) {
                r = get(op.path(), candidates.get(i), query);
                if (r.status >= 200 && r.status < 300) {
                    break;
                }
            }
            seen.put(op.key(), r.observation(null));
        }
        return seen;
    }

    // ------------------------------------------------------------------ identifiers chosen by code

    static List<String> placeholders(String path) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(path);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Up to two complete sets of path-parameter values; empty if any parameter cannot be filled. */
    List<Map<String, String>> candidates(String path, List<String> names, Map<String, JsonNode> bodies) {
        List<Map<String, String>> sets = new ArrayList<>(List.of(new LinkedHashMap<>(), new LinkedHashMap<>()));
        for (String name : names) {
            String fixture = fixture(name);
            if (fixture != null) {
                sets.forEach(s -> s.put(name, fixture));
                continue;
            }
            List<String> values = fromList(path, name, bodies);
            if (values.isEmpty()) {
                return List.of();
            }
            sets.get(0).put(name, values.get(0));
            sets.get(1).put(name, values.size() > 1 ? values.get(1) : values.get(0));
        }
        return sets.get(0).equals(sets.get(1)) ? List.of(sets.get(0)) : sets;
    }

    private String fixture(String name) {
        for (Map.Entry<String, String> e : config.fixtures().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Values for {@code name} from the list operation whose path is the prefix before {@code {name}}. */
    static List<String> fromList(String path, String name, Map<String, JsonNode> bodies) {
        String prefix = path.substring(0, path.indexOf("{" + name + "}"));
        prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        JsonNode body = bodies.get(prefix);
        if (body == null) {
            String best = null;
            for (String p : bodies.keySet()) {
                if (p.startsWith(prefix + "/") && (best == null || p.length() < best.length())) {
                    best = p;
                }
            }
            body = best == null ? null : bodies.get(best);
        }
        if (body == null) {
            return fromAnyList(path, name, bodies);
        }
        JsonNode list = elements(body);
        if (list == null) {
            return fromAnyList(path, name, bodies);
        }
        List<String> out = new ArrayList<>();
        for (JsonNode e : list) {
            String v = idOf(e, name);
            if (v != null) {
                out.add(v);
            }
            if (out.size() == 2) {
                break;
            }
        }
        return out.isEmpty() ? fromAnyList(path, name, bodies) : out;
    }

    /**
     * Fallback: any observed list whose elements carry a field named like the parameter (same name, a plural of
     * it, or a name ending in it, e.g. {@code {code}} ← {@code alpha3Code}, {@code {callingcode}} ← {@code callingCodes}).
     */
    static List<String> fromAnyList(String path, String name, Map<String, JsonNode> bodies) {
        // lists of the operation's own section first (E57: /invoices/ref/{ref} takes a ref from /invoices), then path order
        String section = section(path);
        List<String> paths = new ArrayList<>(bodies.keySet());
        paths.sort(java.util.Comparator.comparing((String p) -> !section(p).equals(section)).thenComparing(p -> p));
        for (String p : paths) {
            JsonNode list = elements(bodies.get(p));
            if (list == null) {
                continue;
            }
            List<String> out = new ArrayList<>();
            for (JsonNode e : list) {
                String v = matchingField(e, name);
                if (v != null) {
                    out.add(v);
                }
                if (out.size() == 2) {
                    break;
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return List.of();
    }

    private static String section(String path) {
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    static String matchingField(JsonNode e, String name) {
        if (!e.isObject()) {
            return null;
        }
        String n = name.toLowerCase();
        for (Iterator<String> it = e.fieldNames(); it.hasNext(); ) {
            String f = it.next();
            String k = f.toLowerCase();
            String singular = k.endsWith("ies") ? k.substring(0, k.length() - 3) + "y" : k.endsWith("s") ? k.substring(0, k.length() - 1) : k;
            if (k.equals(n) || singular.equals(n) || (n.length() >= 4 && k.endsWith(n))) {
                JsonNode v = e.get(f);
                if (v.isArray() && !v.isEmpty()) {
                    v = v.get(0);
                }
                if (v.isObject()) {
                    return idOf(v, name);
                }
                if (v.isValueNode() && !v.isNull() && !v.asText().isEmpty()) {
                    return v.asText();
                }
            }
        }
        return null;
    }

    private static JsonNode elements(JsonNode body) {
        if (body.isArray()) {
            return body;
        }
        if (body.isObject()) {
            for (String w : List.of("list", "data", "items", "results")) {
                if (body.path(w).isArray()) {
                    return body.get(w);
                }
            }
        }
        return null;
    }

    static String idOf(JsonNode e, String name) {
        if (e.isValueNode()) {
            return e.asText();
        }
        if (!e.isObject()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        keys.add(name);
        keys.addAll(ID_KEYS);
        for (String k : keys) {
            for (Iterator<String> it = e.fieldNames(); it.hasNext(); ) {
                String f = it.next();
                if (f.equalsIgnoreCase(k)) {
                    JsonNode v = e.get(f);
                    if (v.isArray() && !v.isEmpty()) {
                        v = v.get(0);
                    }
                    if (v.isValueNode() && !v.isNull() && !v.asText().isEmpty()) {
                        String s = v.asText();
                        return s.startsWith("/") ? s.substring(1) : s; // Docker-style names "/x"
                    }
                }
            }
        }
        return null;
    }

    /** Fills required query parameters from the spec; returns the name of one it cannot fill, or null. */
    static String requiredQuery(OperationSlice op, Map<String, String> query) {
        for (JsonNode p : op.slice().path("parameters")) {
            if (!"query".equals(p.path("in").asText()) || !p.path("required").asBoolean(false)) {
                continue;
            }
            JsonNode schema = p.path("schema");
            JsonNode v = schema.has("default") ? schema.get("default")
                    : schema.path("enum").isArray() && !schema.path("enum").isEmpty() ? schema.get("enum").get(0)
                    : p.has("example") ? p.get("example")
                    : schema.has("example") ? schema.get("example") : null;
            if (v == null || v.isNull() || v.isContainerNode()) {
                return p.path("name").asText();
            }
            query.put(p.path("name").asText(), v.asText());
        }
        return null;
    }

    // ------------------------------------------------------------------ requests

    private record Response(int status, String kind, JsonNode json, String note) {
        Observation observation(String extraNote) {
            JsonNode shape = json == null ? null : Shapes.of(json);
            return new Observation(status, kind, shape, extraNote != null ? extraNote : note);
        }
    }

    private Response get(String path, Map<String, String> pathParams, Map<String, String> query) {
        ObjectNode test = JSON.createObjectNode();
        test.put("title", "explore");
        ObjectNode step = test.putArray("steps").addObject();
        step.put("method", "GET"); // the explorer never builds any other method
        step.put("path", path);
        ObjectNode pp = step.putObject("pathParams");
        pathParams.forEach(pp::put);
        ObjectNode q = step.putObject("query");
        query.forEach(q::put);
        step.putArray("assertions");
        TestExecutor.TestRun run = executor.run(test);
        JsonNode resp = run.record().at("/steps/0/response");
        if (resp.isMissingNode()) {
            return new Response(-1, "error", null, run.reason());
        }
        int status = resp.path("status").asInt();
        if (resp.path("bodyTimedOut").asBoolean() || resp.path("bodyReadTruncated").asBoolean()) {
            return new Response(status, "stream", null, "body cut (stream or too large)");
        }
        String body = resp.path("body").asText("");
        if (body.isBlank()) {
            return new Response(status, "empty", null, null);
        }
        if (!resp.path("json").asBoolean()) {
            return new Response(status, "text", null, null);
        }
        try {
            return new Response(status, "json", JSON.readTree(body), null);
        } catch (Exception e) {
            return new Response(status, "text", null, null);
        }
    }
}
