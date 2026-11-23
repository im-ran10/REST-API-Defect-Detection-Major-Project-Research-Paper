package restdd.exec;

import java.time.Duration;
import java.util.Map;

/**
 * Where and how tests run. The base URL always comes from the runner configuration, never from the spec (D5.1).
 *
 * @param baseUrl       e.g. {@code http://localhost:8080/api/index.php}; test paths are appended to it
 * @param defaultAuth   role used by steps with {@code auth: default} (or no auth field)
 * @param secondaryAuth second role for {@code auth: secondary}; null if this project has no second-role fixture
 * @param fixtures      values for {@code ${fixture.name}} (never secrets); {@code uid} is added per test run
 * @param requestTimeout time allowed until the response headers arrive
 * @param bodyTimeout   time allowed to read the response body (streaming endpoints are cut here, not hung)
 * @param maxBodyBytes  bytes read from a response body; the rest is discarded and marked truncated
 * @param maxRecordedChars characters of each body kept in the record
 */
public record ExecConfig(String baseUrl, AuthConfig defaultAuth, AuthConfig secondaryAuth, Map<String, String> fixtures,
                         Duration requestTimeout, Duration bodyTimeout, int maxBodyBytes, int maxRecordedChars) {

    public ExecConfig {
        if (baseUrl == null || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("base URL must start with http:// or https://");
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        defaultAuth = defaultAuth == null ? AuthConfig.NONE : defaultAuth;
        fixtures = Map.copyOf(fixtures == null ? Map.of() : fixtures);
        if (fixtures.containsKey("uid")) {
            throw new IllegalArgumentException("fixture 'uid' is reserved (a fresh value per test run)");
        }
    }

    public static ExecConfig of(String baseUrl, AuthConfig auth) {
        return new ExecConfig(baseUrl, auth, null, Map.of(), Duration.ofSeconds(30), Duration.ofSeconds(30),
                2_000_000, 32_000);
    }
}
