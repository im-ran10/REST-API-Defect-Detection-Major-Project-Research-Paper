package restdd.probe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends one GET request and returns status and body.
 *
 * <p>Header values (e.g. an API key) are never logged or returned. The key is sent as a header,
 * never as a query parameter, so it cannot appear in a printed URL.
 */
public final class HttpProbe {

    public record Result(URI uri, int status, String body) {}

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private HttpProbe() {}

    /** Joins base URL and path without doubling or dropping the slash. */
    static URI buildUri(String baseUrl, String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/': " + path);
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + path);
    }

    /**
     * @param headerName  auth header name, or null for no auth
     * @param headerValue auth header value (secret); only used to build the request
     */
    public static Result get(String baseUrl, String path, String headerName, String headerValue)
            throws IOException, InterruptedException {
        URI uri = buildUri(baseUrl, path);
        HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        if (headerName != null) {
            req.header(headerName, headerValue);
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> resp = client.send(req.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Result(uri, resp.statusCode(), resp.body());
    }
}
