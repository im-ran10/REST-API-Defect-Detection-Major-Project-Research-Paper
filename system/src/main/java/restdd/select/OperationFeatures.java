package restdd.select;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic per-operation features (final methodology §5.1; D41): from the project-level spec, the shared
 * exploration feature record (shapes only) and the runner configuration (second role). No benchmark data.
 */
public record OperationFeatures(
        String key, String method, String path,
        int pathParams, List<String> queryParams, boolean enumQuery,
        boolean hasBody, boolean multipart, boolean constrained,
        boolean has2xxSchema, boolean secured, boolean secondRole,
        boolean getSameItem, boolean createWithItemGet, boolean writeWithGet,
        boolean collectionAndItem, boolean alternateKey, boolean actionSubresource, boolean lifecycle,
        boolean observedJson, boolean observedTextField, boolean hasReferenceArray) {

    public static final Set<String> FILTER = Set.of("filter", "filters", "sqlfilters", "search", "q", "query", "where");
    public static final Set<String> PAGING = Set.of("limit", "offset", "page", "per_page");

    public boolean get() {
        return method.equals("GET");
    }

    public boolean hasInput() {
        return pathParams > 0 || !queryParams.isEmpty() || hasBody;
    }

    public boolean filtering() {
        return queryParams.stream().anyMatch(FILTER::contains);
    }

    public boolean paging() {
        return queryParams.stream().anyMatch(PAGING::contains);
    }

    public boolean projecting() {
        return queryParams.stream().anyMatch(q -> q.equals("fields") || q.startsWith("sort") || q.startsWith("order"));
    }

    /** Values for the selection log (feature name → value). */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new TreeMap<>();
        m.put("method", method);
        m.put("pathParams", pathParams);
        m.put("queryParams", queryParams);
        m.put("enumQuery", enumQuery);
        m.put("hasBody", hasBody);
        m.put("multipart", multipart);
        m.put("constrained", constrained);
        m.put("has2xxSchema", has2xxSchema);
        m.put("secured", secured);
        m.put("secondRole", secondRole);
        m.put("getSameItem", getSameItem);
        m.put("createWithItemGet", createWithItemGet);
        m.put("writeWithGet", writeWithGet);
        m.put("collectionAndItem", collectionAndItem);
        m.put("alternateKey", alternateKey);
        m.put("actionSubresource", actionSubresource);
        m.put("lifecycle", lifecycle);
        m.put("observedJson", observedJson);
        m.put("observedTextField", observedTextField);
        m.put("hasReferenceArray", hasReferenceArray);
        return m;
    }
}
