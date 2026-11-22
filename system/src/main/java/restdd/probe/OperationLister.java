package restdd.probe;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Lists the operations of a parsed spec in a deterministic order (path, then HTTP method). */
public final class OperationLister {

    /** One operation: HTTP method, path template and operationId (may be null). */
    public record Op(String method, String path, String operationId) {
        @Override
        public String toString() {
            return String.format("%-7s %s%s", method, path,
                    operationId == null ? "" : "  [" + operationId + "]");
        }
    }

    private OperationLister() {}

    public static List<Op> list(OpenAPI openApi) {
        List<Op> ops = new ArrayList<>();
        if (openApi.getPaths() == null) {
            return ops;
        }
        for (Map.Entry<String, PathItem> path : openApi.getPaths().entrySet()) {
            for (Map.Entry<PathItem.HttpMethod, Operation> op : path.getValue().readOperationsMap().entrySet()) {
                ops.add(new Op(op.getKey().name(), path.getKey(), op.getValue().getOperationId()));
            }
        }
        ops.sort(Comparator.comparing(Op::path).thenComparing(Op::method));
        return ops;
    }
}
