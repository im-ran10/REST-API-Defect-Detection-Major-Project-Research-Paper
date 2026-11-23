package restdd.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a Swagger 2.0 / OpenAPI 3.x spec into one self-contained JSON "slice" per operation (T1).
 *
 * <p>Normalisation choices (identical for every arm, so they cannot bias the comparison; ARCHITECTURE):
 * <ul>
 *   <li>Swagger 2.0 is converted to OpenAPI 3.0 by swagger-parser; OpenAPI 3.x is used as written.</li>
 *   <li>Swagger 2.0 parameters carrying the non-standard {@code defaultValue} (Dolibarr) get it copied to
 *       {@code default} before conversion, because the converter drops unknown attributes (E35).</li>
 *   <li>{@code $ref}s are inlined per slice; a reference already open on the current path is replaced by
 *       {@code {"$recursive": "<name>"}}, so recursive schemas terminate.</li>
 *   <li>Vendor extensions ({@code x-*}) and {@code servers} are dropped. Descriptions and examples are kept
 *       unless a {@link Compaction} rule says otherwise (D41).</li>
 *   <li>Responses: every declared status code with its description; schemas only for 2xx codes.</li>
 * </ul>
 */
public final class SpecNormaliser {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper JSON = new ObjectMapper();
    // Alphabetical, so slices come in the same order as OperationLister (path, then method).
    private static final List<String> METHODS = List.of("delete", "get", "head", "options", "patch", "post", "put");
    /** Rough characters-per-token ratio for budgeting only; real counts come from the model (E39). */
    public static final double CHARS_PER_TOKEN = 3.5;

    /**
     * Arm-independent compaction rule (D41). {@link #NONE} keeps everything.
     *
     * @param descriptionCap         max characters kept per description/summary (0 = unlimited)
     * @param dropExamples           drop {@code example}/{@code examples}
     * @param maxSchemaDepth         schema nesting depth after which a schema is cut to its type (0 = unlimited)
     * @param dropSchemaDescriptions drop {@code description}/{@code title} inside schemas (operation summary,
     *                               operation description and parameter descriptions are kept, subject to the cap)
     * @param responseSchemaDepth    like maxSchemaDepth, but only for 2xx response schemas (0 = unlimited); the
     *                               runner still checks responses against the full spec
     */
    public record Compaction(int descriptionCap, boolean dropExamples, int maxSchemaDepth,
                             boolean dropSchemaDescriptions, int responseSchemaDepth) {
        public static final Compaction NONE = new Compaction(0, false, 0, false, 0);

        public Compaction(int descriptionCap, boolean dropExamples, int maxSchemaDepth) {
            this(descriptionCap, dropExamples, maxSchemaDepth, false, 0);
        }
    }

    /** One operation ready for a prompt. {@code key} is "METHOD /path/{template}". */
    public record OperationSlice(String key, String method, String path, ObjectNode slice, String text,
                                 int estimatedTokens) {}

    /** Result: slices in deterministic order (path, then method) plus provenance and counters. */
    public record NormalisedSpec(String declaredVersion, String sourceSha256, List<OperationSlice> operations,
                                 int defaultValueMapped, int recursiveCuts, int unresolvedRefs,
                                 List<String> messages) {
        public OperationSlice find(String method, String path) {
            for (OperationSlice s : operations) {
                if (s.method().equalsIgnoreCase(method) && s.path().equals(path)) {
                    return s;
                }
            }
            return null;
        }
    }

    private final Compaction compaction;
    private JsonNode root;
    private int recursiveCuts;
    private int unresolvedRefs;
    private final java.util.Set<String> unresolvedNames = new java.util.TreeSet<>();

    private SpecNormaliser(Compaction compaction) {
        this.compaction = compaction;
    }

    public static NormalisedSpec normalise(Path specFile) throws IOException {
        return normalise(specFile, Compaction.NONE);
    }

    public static NormalisedSpec normalise(Path specFile, Compaction compaction) throws IOException {
        byte[] bytes = Files.readAllBytes(specFile);
        return new SpecNormaliser(compaction).run(new String(bytes, StandardCharsets.UTF_8), sha256(bytes));
    }

    private NormalisedSpec run(String text, String sourceSha) throws IOException {
        JsonNode raw = Yaml.mapper().readTree(text);
        if (raw == null || !raw.isObject()) {
            throw new IOException("Spec is not a JSON/YAML object");
        }
        List<String> messages = new ArrayList<>();
        int mapped = 0;
        String declared;
        if (raw.hasNonNull("swagger")) {
            declared = "Swagger " + raw.get("swagger").asText();
            mapped = mapDefaultValue(raw);
            root = convertSwagger2((ObjectNode) raw, messages);
        } else if (raw.hasNonNull("openapi")) {
            declared = "OpenAPI " + raw.get("openapi").asText();
            root = raw;
        } else {
            throw new IOException("Neither 'swagger' nor 'openapi' field found");
        }

        List<OperationSlice> ops = new ArrayList<>();
        JsonNode paths = root.path("paths");
        List<String> pathNames = new ArrayList<>();
        paths.fieldNames().forEachRemaining(pathNames::add);
        pathNames.sort(String::compareTo);
        for (String p : pathNames) {
            JsonNode item = paths.get(p);
            if (item.has("$ref")) {
                item = deref(item.get("$ref").asText(), messages);
                if (item == null) {
                    messages.add("unresolved path-item $ref at " + p);
                    continue;
                }
            }
            for (String m : METHODS) {
                if (item.has(m)) {
                    ObjectNode slice = buildSlice(m, p, item, item.get(m));
                    String sliceText = JSON.writeValueAsString(slice);
                    ops.add(new OperationSlice(m.toUpperCase() + " " + p, m.toUpperCase(), p, slice, sliceText,
                            (int) Math.ceil(sliceText.length() / CHARS_PER_TOKEN)));
                }
            }
        }
        unresolvedNames.forEach(r -> messages.add("unresolved $ref: " + r));
        return new NormalisedSpec(declared, sourceSha, List.copyOf(ops), mapped, recursiveCuts, unresolvedRefs,
                List.copyOf(messages));
    }

    /** Copies Swagger 2.0 {@code defaultValue} to {@code default} on parameter objects that lack one. */
    static int mapDefaultValue(JsonNode raw) {
        int count = 0;
        List<JsonNode> paramLists = new ArrayList<>();
        if (raw.has("parameters") && raw.get("parameters").isObject()) {
            raw.get("parameters").elements().forEachRemaining(paramLists::add);
        }
        JsonNode paths = raw.path("paths");
        for (Iterator<JsonNode> it = paths.elements(); it.hasNext(); ) {
            JsonNode item = it.next();
            if (item.has("parameters")) {
                paramLists.add(item.get("parameters"));
            }
            for (String m : METHODS) {
                if (item.has(m) && item.get(m).has("parameters")) {
                    paramLists.add(item.get(m).get("parameters"));
                }
            }
        }
        for (JsonNode list : paramLists) {
            Iterable<JsonNode> params = list.isArray() ? list : List.of(list);
            for (JsonNode p : params) {
                if (p instanceof ObjectNode o && o.has("defaultValue") && !o.has("default")) {
                    o.set("default", o.get("defaultValue"));
                    o.remove("defaultValue");
                    count++;
                }
            }
        }
        return count;
    }

    private static JsonNode convertSwagger2(ObjectNode raw, List<String> messages) throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIParser().readContents(Json.mapper().writeValueAsString(raw), null,
                options);
        if (result.getMessages() != null) {
            result.getMessages().forEach(msg -> messages.add("parser: " + msg));
        }
        OpenAPI api = result.getOpenAPI();
        if (api == null) {
            throw new IOException("swagger-parser could not convert the Swagger 2.0 spec: " + messages);
        }
        return Json.mapper().readTree(Json.mapper().writeValueAsString(api));
    }

    private ObjectNode buildSlice(String method, String path, JsonNode item, JsonNode op) {
        ObjectNode s = F.objectNode();
        s.put("operation", method.toUpperCase() + " " + path);
        copyText(op, s, "summary");
        copyText(op, s, "description");
        if (op.hasNonNull("operationId")) {
            s.put("operationId", op.get("operationId").asText());
        }

        // Parameters: path-level ones first, overridden by operation-level ones with the same name+in.
        Map<String, JsonNode> params = new LinkedHashMap<>();
        for (JsonNode src : List.of(item.path("parameters"), op.path("parameters"))) {
            for (JsonNode p : src) {
                JsonNode resolved = inline(p, new ArrayDeque<>(), 0, compaction.maxSchemaDepth(), false);
                params.put(resolved.path("in").asText() + ":" + resolved.path("name").asText(), resolved);
            }
        }
        if (!params.isEmpty()) {
            ArrayNode arr = s.putArray("parameters");
            params.values().forEach(arr::add);
        }
        if (op.has("requestBody")) {
            s.set("requestBody", inline(op.get("requestBody"), new ArrayDeque<>(), 0, compaction.maxSchemaDepth(), false));
        }
        ObjectNode responses = s.putObject("responses");
        JsonNode rs = op.path("responses");
        List<String> codes = new ArrayList<>();
        rs.fieldNames().forEachRemaining(codes::add);
        codes.sort(String::compareTo);
        for (String code : codes) {
            JsonNode r = rs.get(code);
            if (r.has("$ref")) {
                JsonNode target = deref(r.get("$ref").asText(), null);
                r = target == null ? F.objectNode() : target;
            }
            ObjectNode out = F.objectNode();
            if (r.hasNonNull("description")) {
                out.put("description", cap(r.get("description").asText()));
            }
            if (code.startsWith("2") && r.has("content") && !r.get("content").isEmpty()) {
                int depth = compaction.responseSchemaDepth() > 0 ? compaction.responseSchemaDepth() : compaction.maxSchemaDepth();
                out.set("content", inline(r.get("content"), new ArrayDeque<>(), 0, depth, false));
            }
            responses.set(code, out);
        }
        JsonNode security = op.has("security") ? op.get("security") : root.path("security");
        if (security.isArray() && !security.isEmpty()) {
            s.set("security", security);
        }
        return s;
    }

    private static final java.util.Set<String> SCHEMA_KEYS = java.util.Set.of("schema", "properties", "items",
            "additionalProperties", "allOf", "anyOf", "oneOf", "not");

    /**
     * Deep copy with $refs inlined, x-* removed, compaction applied. {@code inSchema} is true below a "schema"
     * key (or inside properties/items/...), where schema-internal descriptions may be dropped.
     */
    private JsonNode inline(JsonNode node, Deque<String> open, int schemaDepth, int maxDepth, boolean inSchema) {
        if (node == null) {
            return F.nullNode();
        }
        if (node.isObject()) {
            if (node.has("$ref") && node.get("$ref").isTextual()) {
                String ref = node.get("$ref").asText();
                String name = ref.substring(ref.lastIndexOf('/') + 1);
                if (open.contains(ref)) {
                    recursiveCuts++;
                    return F.objectNode().put("$recursive", name);
                }
                JsonNode target = deref(ref, null);
                if (target == null) {
                    unresolvedRefs++;
                    unresolvedNames.add(ref);
                    return F.objectNode().put("$unresolved", name);
                }
                open.push(ref);
                JsonNode out = inline(target, open, schemaDepth, maxDepth, inSchema);
                open.pop();
                return out;
            }
            // At the depth limit, a schema that still has children (properties, items, ...) is cut to its type.
            if (maxDepth > 0 && schemaDepth >= maxDepth && hasChildSchemas(node)) {
                ObjectNode cut = F.objectNode();
                if (node.has("type")) {
                    cut.set("type", node.get("type"));
                }
                cut.put("$truncated", true);
                return cut;
            }
            ObjectNode out = F.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                String k = e.getKey();
                if (k.startsWith("x-")) {
                    continue;
                }
                if (compaction.dropExamples() && (k.equals("example") || k.equals("examples"))) {
                    continue;
                }
                if (inSchema && compaction.dropSchemaDescriptions()
                        && (k.equals("description") || k.equals("title")) && e.getValue().isTextual()) {
                    continue;
                }
                if ((k.equals("description") || k.equals("summary")) && e.getValue().isTextual()) {
                    out.set(k, TextNode.valueOf(cap(e.getValue().asText())));
                    continue;
                }
                boolean deeper = k.equals("properties") || k.equals("items") || k.equals("additionalProperties")
                        || k.equals("allOf") || k.equals("anyOf") || k.equals("oneOf");
                // Inside "properties" the keys are property names, not schema keywords.
                boolean childInSchema = inSchema || SCHEMA_KEYS.contains(k);
                out.set(k, inline(e.getValue(), open, deeper ? schemaDepth + 1 : schemaDepth, maxDepth, childInSchema));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = F.arrayNode();
            node.forEach(n -> out.add(inline(n, open, schemaDepth, maxDepth, inSchema)));
            return out;
        }
        return node.deepCopy();
    }

    private static boolean hasChildSchemas(JsonNode n) {
        return n.has("properties") || n.has("items") || n.has("allOf") || n.has("oneOf") || n.has("anyOf")
                || (n.has("additionalProperties") && n.get("additionalProperties").isObject());
    }

    private JsonNode deref(String ref, List<String> messages) {
        if (!ref.startsWith("#/")) {
            if (messages != null) {
                messages.add("external $ref not supported: " + ref);
            }
            return null;
        }
        JsonNode cur = root;
        for (String part : ref.substring(2).split("/")) {
            cur = cur.path(part.replace("~1", "/").replace("~0", "~"));
            if (cur.isMissingNode()) {
                return null;
            }
        }
        return cur;
    }

    private void copyText(JsonNode from, ObjectNode to, String field) {
        if (from.hasNonNull(field) && from.get(field).isTextual() && !from.get(field).asText().isBlank()) {
            to.put(field, cap(from.get(field).asText()));
        }
    }

    private String cap(String s) {
        int c = compaction.descriptionCap();
        return c > 0 && s.length() > c ? s.substring(0, c) + "…" : s;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
