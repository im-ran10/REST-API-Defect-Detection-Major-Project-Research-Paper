package restdd.select;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Trigger predicates (final methodology §5.2, D41), keyed by the {@code trigger} name in the catalogue files.
 * They read {@link OperationFeatures} only. "default" never fires: it marks the catalogue's default entry.
 */
public final class Predicates {

    public static final Map<String, Predicate<OperationFeatures>> ALL = new LinkedHashMap<>();
    /** Plain-language definitions, for the mapping artefact (docs/frozen/CATALOGUES.md). */
    public static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        def("default", f -> false, "never fires; used when no other entry of the catalogue fires");
        def("hasInput", OperationFeatures::hasInput, "the operation has a path parameter, a query parameter or a request body");
        def("has2xxSchema", OperationFeatures::has2xxSchema, "a 2xx response with a schema is documented");
        def("hasPathParam", f -> f.pathParams() > 0, "the path has at least one parameter");
        def("deleteWithGet", f -> f.method().equals("DELETE") && f.getSameItem(), "DELETE on an item path that also has GET");
        def("createWithGet", OperationFeatures::createWithItemGet, "POST on a collection whose item path has GET");
        def("secured", OperationFeatures::secured, "the operation (or the spec globally) declares a security requirement");
        def("safeOrIdempotent", f -> f.method().matches("GET|HEAD|PUT|DELETE"), "method GET, HEAD, PUT or DELETE");
        def("multiQuery", f -> f.get() && f.queryParams().size() >= 2, "GET with two or more query parameters");
        def("paging", f -> f.get() && f.paging(), "GET with limit, offset, page or per_page");
        def("filtering", f -> f.get() && f.filtering(), "GET with filter, filters, sqlfilters, search, q, query or where (D41)");
        def("partitioning", f -> f.get() && (f.filtering() || f.enumQuery()), "GET with a filter parameter or an enumerated query parameter");
        def("projecting", f -> f.get() && f.projecting(), "GET with fields, sort* or order*");
        def("constrainedInput", f -> f.hasInput() && (f.hasBody() || f.constrained()),
                "a request body, or a parameter with a type, format, range, length, pattern, enum or required flag");
        def("writeWithGet", OperationFeatures::writeWithGet,
                "POST on a collection, or PUT/PATCH on an item, where the item path has GET");
        def("crossView", f -> f.get() && (f.collectionAndItem() || f.alternateKey() || f.hasReferenceArray()),
                "GET where the resource has both list and item GET, or an alternate-key lookup, or an observed reference array");
        def("textFields", f -> f.get() && f.observedTextField(), "GET whose observed response shape has a string field in an object");
        def("workflow", f -> f.actionSubresource() || (f.lifecycle() && !f.get()),
                "a non-GET action sub-resource (/x/{id}/verb), or a write on a resource with create, read and update/delete (D41)");
        def("secondRole", f -> f.secured() && f.secondRole(), "a secured operation and a second user in the fixture (D41)");
        def("representation", f -> f.paging() || f.projecting(),
                "limit, offset, page, per_page, fields, sort* or order* parameters (D41)");
        def("multipart", OperationFeatures::multipart, "a multipart request body (D41)");
    }

    private static void def(String name, Predicate<OperationFeatures> p, String text) {
        ALL.put(name, p);
        TEXT.put(name, text);
    }

    private Predicates() {}

    public static boolean fires(String name, OperationFeatures f) {
        Predicate<OperationFeatures> p = ALL.get(name);
        if (p == null) {
            throw new IllegalArgumentException("unknown trigger predicate " + name);
        }
        return p.test(f);
    }
}
