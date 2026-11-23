package restdd.exec;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * How one role authenticates. The secret is read from an environment variable by the caller and is never
 * logged: every recorded exchange is redacted with {@link #secrets()}.
 *
 * <ul>
 *   <li>{@code none} — no credentials;</li>
 *   <li>{@code header} — {@code name: value} (e.g. Dolibarr {@code DOLAPIKEY}, nocodb {@code xc-token});</li>
 *   <li>{@code bearer} — {@code Authorization: Bearer value};</li>
 *   <li>{@code basic} — {@code Authorization: Basic base64(name:value)} (name = user, value = password).</li>
 * </ul>
 */
public record AuthConfig(String kind, String name, String value) {

    public static final AuthConfig NONE = new AuthConfig("none", null, null);

    public AuthConfig {
        if (!List.of("none", "header", "bearer", "basic").contains(kind)) {
            throw new IllegalArgumentException("unknown auth kind '" + kind + "'");
        }
        if (!kind.equals("none") && (value == null || value.isEmpty())) {
            throw new IllegalArgumentException("auth '" + kind + "' needs a non-empty secret");
        }
        if ((kind.equals("header") || kind.equals("basic")) && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("auth '" + kind + "' needs a name");
        }
    }

    void apply(HttpRequest.Builder b) {
        switch (kind) {
            case "header" -> b.setHeader(name, value);
            case "bearer" -> b.setHeader("Authorization", "Bearer " + value);
            case "basic" -> b.setHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((name + ":" + value).getBytes(StandardCharsets.UTF_8)));
            default -> { }
        }
    }

    /** Strings that must never appear in a record. */
    List<String> secrets() {
        if (kind.equals("none")) {
            return List.of();
        }
        if (kind.equals("basic")) {
            return List.of(value, Base64.getEncoder().encodeToString((name + ":" + value).getBytes(StandardCharsets.UTF_8)));
        }
        return List.of(value);
    }

    @Override
    public String toString() {
        return "AuthConfig[" + kind + (name == null ? "" : " " + name) + "]"; // never the secret
    }
}
