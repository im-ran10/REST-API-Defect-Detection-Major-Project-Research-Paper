package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A deployment target read from a JSON file kept outside the repository. Secrets are never in the file: auth
 * entries name an environment variable.
 *
 * <pre>
 * {
 *   "baseUrl": "http://localhost:8080/api/index.php",
 *   "auth": {"kind": "header", "name": "DOLAPIKEY", "env": "DOLIBARR_API_KEY"},
 *   "secondaryAuth": null,
 *   "fixtures": {"imageName": "quay.io/libpod/alpine:latest"},
 *   "requestTimeoutSec": 30, "bodyTimeoutSec": 30,
 *   "reset": {"kind": "command", "dir": "/home/me/dolibarr",
 *             "snapshot": "bash ~/projects/rest-api-defect-detection/scripts/reset/dolibarr_reset.sh snapshot",
 *             "restore":  "bash ~/projects/rest-api-defect-detection/scripts/reset/dolibarr_reset.sh restore",
 *             "healthUrl": "http://localhost:8080/api/index.php/status", "healthUsesAuth": true,
 *             "healthTimeoutSec": 120, "commandTimeoutSec": 600}
 * }
 * </pre>
 * {@code "reset": {"kind": "none"}} for read-only targets.
 */
public record TargetConfig(ExecConfig exec, ResetAdapter reset) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static TargetConfig load(Path file, Function<String, String> env) throws IOException {
        JsonNode t = JSON.readTree(file.toFile());
        AuthConfig auth = auth(t.path("auth"), env);
        AuthConfig secondary = t.path("secondaryAuth").isObject() ? auth(t.get("secondaryAuth"), env) : null;
        Map<String, String> fixtures = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = t.path("fixtures").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            fixtures.put(e.getKey(), e.getValue().asText());
        }
        ExecConfig exec = new ExecConfig(t.path("baseUrl").asText(null), auth, secondary, fixtures,
                Duration.ofSeconds(t.path("requestTimeoutSec").asInt(30)),
                Duration.ofSeconds(t.path("bodyTimeoutSec").asInt(30)),
                t.path("maxBodyBytes").asInt(2_000_000), t.path("maxRecordedChars").asInt(32_000));

        JsonNode r = t.path("reset");
        ResetAdapter reset;
        String kind = r.path("kind").asText("none");
        if (kind.equals("none")) {
            reset = new ResetAdapter.NoReset();
        } else if (kind.equals("command")) {
            reset = new CommandReset(r.path("name").asText("command"),
                    required(r, "snapshot"), required(r, "restore"),
                    r.hasNonNull("dir") ? Path.of(expandHome(r.get("dir").asText())) : null,
                    r.path("healthUrl").asText(null),
                    r.path("healthUsesAuth").asBoolean(false) ? auth : AuthConfig.NONE,
                    Duration.ofSeconds(r.path("healthTimeoutSec").asInt(120)),
                    Duration.ofSeconds(r.path("commandTimeoutSec").asInt(600)));
        } else {
            throw new IllegalArgumentException("unknown reset kind '" + kind + "'");
        }
        return new TargetConfig(exec, reset);
    }

    private static AuthConfig auth(JsonNode a, Function<String, String> env) {
        String kind = a.path("kind").asText("none");
        if (kind.equals("none")) {
            return AuthConfig.NONE;
        }
        String var = a.path("env").asText("");
        String secret = env.apply(var);
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("environment variable '" + var + "' (auth secret) is not set");
        }
        String name = a.path("name").asText(null);
        if (kind.equals("basic") && a.hasNonNull("userEnv")) {
            name = env.apply(a.get("userEnv").asText());
        }
        return new AuthConfig(kind, name, secret);
    }

    private static String required(JsonNode r, String field) {
        if (!r.hasNonNull(field) || r.get(field).asText().isBlank()) {
            throw new IllegalArgumentException("reset." + field + " is required");
        }
        return r.get(field).asText();
    }

    private static String expandHome(String p) {
        return p.startsWith("~/") ? System.getProperty("user.home") + p.substring(1) : p;
    }
}
