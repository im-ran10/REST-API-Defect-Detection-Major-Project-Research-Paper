package restdd.select;

import com.fasterxml.jackson.databind.JsonNode;
import restdd.explore.FeatureRecord;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes {@link OperationFeatures} for every operation of a project-level spec (T8; deterministic). */
public final class FeatureExtractor {

    private FeatureExtractor() {}

    public static Map<String, OperationFeatures> extract(NormalisedSpec spec, FeatureRecord record, boolean secondRole) {
        Map<String, Set<String>> methodsByPath = new HashMap<>();
        for (OperationSlice s : spec.operations()) {
            methodsByPath.computeIfAbsent(s.path(), k -> new HashSet<>()).add(s.method());
        }
        Map<String, OperationFeatures> out = new LinkedHashMap<>();
        for (OperationSlice s : spec.operations()) {
            out.put(s.key(), features(s, methodsByPath, record, secondRole));
        }
        return out;
    }

    static OperationFeatures features(OperationSlice s, Map<String, Set<String>> methodsByPath, FeatureRecord record,
                                      boolean secondRole) {
        JsonNode slice = s.slice();
        String path = s.path();
        String[] segs = segments(path);
        int pathParams = 0;
        for (String g : segs) {
            pathParams += isParam(g) ? 1 : 0;
        }
        List<String> query = new ArrayList<>();
        boolean enumQuery = false;
        boolean constrained = false;
        for (JsonNode p : slice.path("parameters")) {
            JsonNode schema = p.has("schema") ? p.get("schema") : p;
            if ("query".equals(p.path("in").asText())) {
                query.add(p.path("name").asText().toLowerCase());
                enumQuery |= schema.has("enum");
            }
            constrained |= hasConstraint(schema);
        }
        JsonNode content = slice.path("requestBody").path("content");
        boolean hasBody = slice.has("requestBody");
        boolean multipart = false;
        for (Iterator<String> it = content.fieldNames(); it.hasNext(); ) {
            String media = it.next();
            multipart |= media.startsWith("multipart/");
            constrained |= hasConstraint(content.path(media).path("schema"));
        }
        boolean has2xxSchema = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = slice.path("responses").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> r = it.next();
            if (r.getKey().startsWith("2") && r.getValue().toString().contains("\"schema\"")) {
                has2xxSchema = true;
            }
        }
        boolean secured = slice.path("security").isArray() && !slice.path("security").isEmpty();

        boolean itemPath = segs.length > 0 && isParam(segs[segs.length - 1]);
        String collection = itemPath ? parent(path) : path;
        String item = itemPath ? path : childItem(path, methodsByPath);
        Set<String> itemMethods = item == null ? Set.of() : methodsByPath.getOrDefault(item, Set.of());
        Set<String> collMethods = methodsByPath.getOrDefault(collection, Set.of());
        String m = s.method();

        boolean getSameItem = itemPath && itemMethods.contains("GET");
        boolean createWithItemGet = !itemPath && m.equals("POST") && itemMethods.contains("GET");
        boolean writeWithGet = (createWithItemGet) || (itemPath && (m.equals("PUT") || m.equals("PATCH")) && getSameItem);
        boolean collectionAndItem = collMethods.contains("GET") && itemMethods.contains("GET");
        boolean alternateKey = m.equals("GET") && itemPath && segs.length >= 3 && !isParam(segs[segs.length - 2])
                && hasItemSibling(segs, methodsByPath);
        boolean actionSubresource = !m.equals("GET") && segs.length >= 2 && !isParam(segs[segs.length - 1])
                && isParam(segs[segs.length - 2]);
        boolean lifecycle = collMethods.contains("POST") && itemMethods.contains("GET")
                && (itemMethods.contains("PUT") || itemMethods.contains("PATCH") || itemMethods.contains("DELETE"));

        boolean observedJson = false;
        boolean textField = false;
        boolean refArray = false;
        if (record != null && m.equals("GET")) {
            JsonNode o = record.operation(s.key());
            if (o != null && "json".equals(o.path("kind").asText())) {
                observedJson = true;
                textField = hasTextField(o.path("shape"));
                refArray = o.path("hasReferenceArray").asBoolean();
            }
        }
        return new OperationFeatures(s.key(), m, path, pathParams, List.copyOf(query), enumQuery, hasBody, multipart,
                constrained, has2xxSchema, secured, secondRole, getSameItem, createWithItemGet, writeWithGet,
                collectionAndItem, alternateKey, actionSubresource, lifecycle, observedJson, textField, refArray);
    }

    static String[] segments(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }

    static boolean isParam(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }

    static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "/" : path.substring(0, i);
    }

    /** {@code /x} → the first {@code /x/{param}} path that exists (sorted), else null. */
    static String childItem(String path, Map<String, Set<String>> methodsByPath) {
        List<String> cands = new ArrayList<>();
        for (String p : methodsByPath.keySet()) {
            if (p.startsWith(path + "/{") && p.indexOf('/', path.length() + 1) < 0 && p.endsWith("}")) {
                cands.add(p);
            }
        }
        java.util.Collections.sort(cands);
        return cands.isEmpty() ? null : cands.get(0);
    }

    /** {@code /x/ref/{ref}}: does {@code /x/{param}} have a GET? */
    static boolean hasItemSibling(String[] segs, Map<String, Set<String>> methodsByPath) {
        StringBuilder base = new StringBuilder();
        for (int i = 0; i < segs.length - 2; i++) {
            base.append('/').append(segs[i]);
        }
        String item = childItem(base.toString(), methodsByPath);
        return item != null && methodsByPath.get(item).contains("GET");
    }

    static boolean hasConstraint(JsonNode schema) {
        if (schema == null || schema.isMissingNode()) {
            return false;
        }
        for (String k : List.of("enum", "format", "minimum", "maximum", "minLength", "maxLength", "pattern", "required")) {
            if (schema.has(k)) {
                return true;
            }
        }
        for (Iterator<JsonNode> it = schema.path("properties").elements(); it.hasNext(); ) {
            JsonNode p = it.next();
            for (String k : List.of("enum", "format", "minimum", "maximum", "minLength", "maxLength", "pattern")) {
                if (p.has(k)) {
                    return true;
                }
            }
        }
        return schema.has("type") && !"string".equals(schema.path("type").asText());
    }

    /** Shape representation of {@link restdd.explore.Shapes}: a text leaf starting with "string" inside an object. */
    static boolean hasTextField(JsonNode shape) {
        if (shape == null) {
            return false;
        }
        if (shape.has("{}")) {
            for (JsonNode v : shape.get("{}")) {
                if ((v.isTextual() && v.asText().startsWith("string")) || hasTextField(v)) {
                    return true;
                }
            }
            return false;
        }
        if (shape.has("[]")) {
            return hasTextField(shape.get("[]"));
        }
        if (shape.has("map")) {
            return hasTextField(shape.get("map"));
        }
        return false;
    }
}
