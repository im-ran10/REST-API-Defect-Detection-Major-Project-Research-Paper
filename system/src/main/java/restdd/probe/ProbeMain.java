package restdd.probe;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 probe: load a spec, list its operations, send one GET, print status and body.
 *
 * <pre>
 * java -jar target/probe.jar --spec FILE --base-url URL --get /path
 *      [--auth-header NAME --auth-env ENV_VAR] [--max-body N]
 * </pre>
 *
 * The auth value is read from the environment variable named by --auth-env and is never printed.
 */
public final class ProbeMain {

    private static final Set<String> FLAGS =
            Set.of("--spec", "--base-url", "--get", "--auth-header", "--auth-env", "--max-body");

    private static final String USAGE = """
            Usage: java -jar probe.jar --spec FILE --base-url URL --get /path
                                       [--auth-header NAME --auth-env ENV_VAR] [--max-body N]
              --spec         Swagger 2.0 or OpenAPI 3.x file (JSON or YAML)
              --base-url     base URL the path is appended to (the spec's servers/host are ignored)
              --get          path of the GET request to send, e.g. /status
              --auth-header  header to carry the API key, e.g. DOLAPIKEY
              --auth-env     environment variable holding the key, e.g. DOLIBARR_API_KEY
              --max-body     characters of the body to print (default 4000; 0 = all)
            """;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Map<String, String> opt;
        try {
            opt = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.print(USAGE);
            return 2;
        }
        for (String required : List.of("--spec", "--base-url", "--get")) {
            if (!opt.containsKey(required)) {
                System.err.println("Error: missing " + required);
                System.err.print(USAGE);
                return 2;
            }
        }
        String authHeader = opt.get("--auth-header");
        String authEnv = opt.get("--auth-env");
        if ((authHeader == null) != (authEnv == null)) {
            System.err.println("Error: --auth-header and --auth-env must be given together");
            return 2;
        }
        String authValue = null;
        if (authEnv != null) {
            authValue = System.getenv(authEnv);
            if (authValue == null || authValue.isBlank()) {
                // Name only, never the value.
                System.err.println("Error: environment variable " + authEnv + " is not set or empty"
                        + " (for Dolibarr: source ~/.dolibarr_credentials)");
                return 2;
            }
        }
        int maxBody;
        try {
            maxBody = Integer.parseInt(opt.getOrDefault("--max-body", "4000"));
        } catch (NumberFormatException e) {
            System.err.println("Error: --max-body must be an integer");
            return 2;
        }

        // 1. Load the spec and list operations.
        SpecLoader.LoadedSpec spec;
        try {
            spec = SpecLoader.load(Path.of(opt.get("--spec")));
        } catch (Exception e) {
            System.err.println("Error loading spec: " + e.getMessage());
            return 1;
        }
        List<OperationLister.Op> ops = OperationLister.list(spec.openApi());
        System.out.println("== Spec");
        System.out.println("file:              " + opt.get("--spec"));
        System.out.println("declared version:  " + spec.declaredVersion());
        System.out.println("parsed as:         OpenAPI " + spec.openApi().getOpenapi()
                + " (" + spec.openApi().getSpecVersion() + ")");
        System.out.println("paths:             " + (spec.openApi().getPaths() == null ? 0 : spec.openApi().getPaths().size()));
        System.out.println("operations:        " + ops.size());
        System.out.println("parser messages:   " + spec.messages().size());
        spec.messages().forEach(m -> System.out.println("  - " + m));
        System.out.println();
        System.out.println("== Operations");
        ops.forEach(op -> System.out.println("  " + op));
        System.out.println();

        // 2. Send one GET.
        String path = opt.get("--get");
        // Path without query string, matched against the spec's path templates.
        String pathOnly = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        List<String> matches = ops.stream()
                .filter(o -> o.method().equals("GET") && OperationLister.matchesTemplate(o.path(), pathOnly))
                .map(OperationLister.Op::path)
                .toList();
        System.out.println("== Request");
        System.out.println("GET " + opt.get("--base-url") + path);
        System.out.println("matches spec:      " + (matches.isEmpty() ? "no GET operation" : "GET " + String.join(", ", matches)));
        System.out.println("auth:              " + (authHeader == null
                ? "none" : "header " + authHeader + " from $" + authEnv + " (value not shown)"));
        System.out.println();
        HttpProbe.Result res;
        try {
            res = HttpProbe.get(opt.get("--base-url"), path, authHeader, authValue);
        } catch (Exception e) {
            System.err.println("Request failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 1;
        }
        String body = res.body() == null ? "" : res.body();
        System.out.println("== Response");
        System.out.println("status:            " + res.status());
        System.out.println("body length:       " + body.length() + " chars");
        System.out.println("body:");
        if (maxBody > 0 && body.length() > maxBody) {
            System.out.println(body.substring(0, maxBody));
            System.out.println("... [truncated: " + (body.length() - maxBody) + " more chars; use --max-body 0]");
        } else {
            System.out.println(body);
        }
        return 0;
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("--help") || flag.equals("-h")) {
                throw new IllegalArgumentException("help requested");
            }
            if (!FLAGS.contains(flag)) {
                throw new IllegalArgumentException("unknown argument " + flag);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + flag);
            }
            opt.put(flag, args[++i]);
        }
        return opt;
    }
}
