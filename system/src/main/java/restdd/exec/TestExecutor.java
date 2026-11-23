package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.dsl.JsonPathLite;
import restdd.dsl.StaticValidator;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one generated test against one deployment (T5). Steps run in order; the run stops at the first failing
 * step. Every exchange is recorded (with secrets redacted) so verdicts can be recomputed and reviewed.
 *
 * <p>Fixed rules: redirects are never followed; at most {@code SuiteSchema.MAX_STEPS} requests per test; a
 * response body is read for at most {@code bodyTimeout} and {@code maxBodyBytes} (streaming endpoints are cut,
 * not waited for); 502/503/504 and transport errors are infrastructure errors, never detections.
 */
public final class TestExecutor {

    public record TestRun(Outcome outcome, String reason, ObjectNode record) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}/]+)}");
    private static final Set<Integer> GATEWAY = Set.of(502, 503, 504);
    private static final Set<String> RESTRICTED_HEADERS = Set.of("connection", "content-length", "expect", "host",
            "upgrade");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "restdd-body-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final ExecConfig config;
    private final StaticValidator resolver;
    private final HttpClient http;

    public TestExecutor(ExecConfig config, StaticValidator resolver) {
        this.config = config;
        this.resolver = resolver;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** A fresh run-scoped unique value for {@code ${fixture.uid}}. */
    static String newUid() {
        byte[] b = new byte[4];
        RANDOM.nextBytes(b);
        return "rd" + HexFormat.of().formatHex(b);
    }

    public TestRun run(JsonNode test) {
        String uid = newUid();
        Map<String, String> fixtures = new LinkedHashMap<>(config.fixtures());
        fixtures.put("uid", uid);
        Refs refs = new Refs(fixtures);
        AssertionEvaluator evaluator = new AssertionEvaluator(refs);

        ObjectNode rec = JSON.createObjectNode();
        rec.put("title", test.path("title").asText());
        rec.put("uid", uid);
        ArrayNode stepsRec = rec.putArray("steps");
        long start = System.nanoTime();
        Outcome outcome = Outcome.PASS;
        String reason = "all assertions held";

        JsonNode steps = test.path("steps");
        for (int j = 0; j < steps.size(); j++) {
            JsonNode st = steps.get(j);
            ObjectNode sr = stepsRec.addObject();
            sr.put("step", j + 1);
            String at = "step " + (j + 1) + ": ";

            HttpRequest request;
            try {
                request = build(st, refs, sr);
            } catch (Refs.MissingRef e) {
                outcome = Outcome.INVALID;
                reason = at + "undefined reference ${" + e.getMessage() + "}";
                break;
            } catch (IllegalArgumentException e) {
                outcome = Outcome.INVALID;
                reason = at + e.getMessage();
                break;
            }

            AssertionEvaluator.Response response;
            try {
                response = send(request, sr);
            } catch (IOException e) {
                outcome = Outcome.INFRA_ERROR;
                reason = at + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            if (GATEWAY.contains(response.status())) {
                outcome = Outcome.INFRA_ERROR;
                reason = at + "gateway/unavailable status " + response.status();
                break;
            }

            OperationSlice op = operationOf(st, refs);
            List<AssertionEvaluator.Result> results = evaluator.evaluate(st.path("assertions"), response, op);
            ArrayNode ar = sr.putArray("assertions");
            AssertionEvaluator.Result firstFailure = null;
            for (AssertionEvaluator.Result r : results) {
                ObjectNode a = ar.addObject();
                a.put("index", r.index());
                a.put("type", r.type());
                a.put("op", r.op());
                a.put("passed", r.passed());
                a.put("detail", r.detail());
                if (!r.passed() && firstFailure == null) {
                    firstFailure = r;
                }
            }
            if (firstFailure != null) {
                outcome = Outcome.FAIL;
                reason = at + "assertion " + (firstFailure.index() + 1) + " (" + firstFailure.type() + " "
                        + firstFailure.op() + ") failed: " + firstFailure.detail();
                break;
            }

            String missing = extract(st.path("extract"), response, refs, sr);
            if (missing != null) {
                outcome = Outcome.SETUP_ERROR;
                reason = at + missing;
                break;
            }
        }
        rec.put("outcome", outcome.name());
        rec.put("reason", reason);
        rec.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        Redactor.redact(rec, secrets());
        return new TestRun(outcome, Redactor.redact(reason, secrets()), rec);
    }

    private List<String> secrets() {
        List<String> s = new java.util.ArrayList<>(config.defaultAuth().secrets());
        if (config.secondaryAuth() != null) {
            s.addAll(config.secondaryAuth().secrets());
        }
        return s;
    }

    // ------------------------------------------------------------------ request

    private HttpRequest build(JsonNode st, Refs refs, ObjectNode sr) {
        String method = st.path("method").asText();
        String path = fillPath(refs.substituteText(st.path("path").asText()), st.path("pathParams"), refs);
        StringBuilder url = new StringBuilder(config.baseUrl()).append(path.startsWith("/") ? "" : "/").append(path);
        JsonNode query = st.path("query");
        boolean first = !path.contains("?");
        for (Iterator<Map.Entry<String, JsonNode>> it = query.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            url.append(first ? '?' : '&').append(enc(refs.substituteText(e.getKey()))).append('=')
                    .append(enc(refs.substituteText(e.getValue().asText())));
            first = false;
        }
        URI uri;
        try {
            uri = URI.create(url.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid URL " + url);
        }

        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(config.requestTimeout());
        String auth = st.path("auth").asText("default");
        switch (auth) {
            case "none" -> { }
            case "secondary" -> {
                if (config.secondaryAuth() == null) {
                    throw new IllegalArgumentException("auth 'secondary' used but this project has no second role");
                }
                config.secondaryAuth().apply(b);
            }
            default -> config.defaultAuth().apply(b);
        }

        ObjectNode reqRec = sr.putObject("request");
        reqRec.put("method", method);
        reqRec.put("url", uri.toString());
        reqRec.put("auth", auth);
        ObjectNode hRec = reqRec.putObject("headers");
        boolean contentTypeGiven = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = st.path("headers").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            if (RESTRICTED_HEADERS.contains(name.toLowerCase())) {
                hRec.put(name, "(not sent: restricted header)");
                continue;
            }
            String value = refs.substituteText(e.getValue().asText());
            try {
                b.setHeader(name, value);
            } catch (IllegalArgumentException ex) {
                hRec.put(name, "(not sent: invalid header)");
                continue;
            }
            hRec.put(name, value);
            contentTypeGiven |= name.equalsIgnoreCase("content-type");
        }

        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();
        JsonNode mp = st.path("multipart");
        JsonNode jb = st.path("body");
        if (mp.isObject() && !mp.isEmpty()) {
            String boundary = "restdd" + newUid();
            String text = multipart(mp, refs, boundary);
            body = HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8);
            if (!contentTypeGiven) {
                b.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
            }
            reqRec.put("body", text);
        } else if (!jb.isMissingNode() && !jb.isNull()) {
            JsonNode filled = refs.substituteJson(jb);
            String text = filled.isTextual() ? filled.asText() : filled.toString();
            body = HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8);
            if (!contentTypeGiven) {
                b.setHeader("Content-Type", "application/json");
            }
            reqRec.put("body", text);
        }
        b.method(method, method.equals("HEAD") ? HttpRequest.BodyPublishers.noBody() : body);
        return b.build();
    }

    /** Fills {name} placeholders from pathParams; a placeholder without a value makes the test invalid. */
    static String fillPath(String path, JsonNode pathParams, Refs refs) {
        Matcher m = PLACEHOLDER.matcher(path);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            JsonNode v = pathParams.get(m.group(1));
            if (v == null || v.isNull()) {
                throw new IllegalArgumentException("path parameter '" + m.group(1) + "' has no value");
            }
            m.appendReplacement(b, Matcher.quoteReplacement(enc(refs.substituteText(v.asText()))));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String multipart(JsonNode mp, Refs refs, String boundary) {
        StringBuilder b = new StringBuilder();
        for (Iterator<Map.Entry<String, JsonNode>> it = mp.path("fields").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            b.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(refs.substituteText(e.getKey())).append("\"\r\n\r\n")
                    .append(refs.substituteText(e.getValue().asText())).append("\r\n");
        }
        if (mp.hasNonNull("fileField")) {
            b.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(refs.substituteText(mp.get("fileField").asText()))
                    .append("\"; filename=\"").append(refs.substituteText(mp.path("fileName").asText("test.txt"))).append("\"\r\n")
                    .append("Content-Type: text/plain\r\n\r\n")
                    .append(refs.substituteText(mp.path("fileContent").asText(""))).append("\r\n");
        }
        return b.append("--").append(boundary).append("--\r\n").toString();
    }

    // ------------------------------------------------------------------ response

    private AssertionEvaluator.Response send(HttpRequest request, ObjectNode sr) throws IOException, InterruptedException {
        long t0 = System.nanoTime();
        HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        AtomicBoolean timedOut = new AtomicBoolean(false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean truncated = false;
        try (InputStream in = resp.body()) {
            ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                timedOut.set(true);
                try {
                    in.close();
                } catch (IOException ignored) {
                    // closing aborts the read below
                }
            }, config.bodyTimeout().toMillis(), TimeUnit.MILLISECONDS);
            try {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    int room = config.maxBodyBytes() - buf.size();
                    if (n >= room) {
                        buf.write(chunk, 0, Math.max(room, 0));
                        truncated = true;
                        break;
                    }
                    buf.write(chunk, 0, n);
                }
            } catch (IOException e) {
                if (!timedOut.get()) {
                    throw e;
                }
            } finally {
                watchdog.cancel(false);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        String text = buf.toString(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = resp.headers().map();
        JsonNode json = null;
        if (!truncated && !timedOut.get()) {
            json = parseJson(text, AssertionEvaluator.headerValues(headers, "content-type"));
        }

        ObjectNode rr = sr.putObject("response");
        rr.put("status", resp.statusCode());
        ObjectNode hr = rr.putObject("headers");
        headers.forEach((k, v) -> hr.put(k, String.join(", ", v)));
        rr.put("bodyBytes", buf.size());
        rr.put("body", text.length() > config.maxRecordedChars() ? text.substring(0, config.maxRecordedChars()) : text);
        rr.put("bodyRecordedTruncated", text.length() > config.maxRecordedChars());
        rr.put("bodyReadTruncated", truncated);
        rr.put("bodyTimedOut", timedOut.get());
        rr.put("json", json != null);
        rr.put("ms", ms);
        return new AssertionEvaluator.Response(resp.statusCode(), headers, json, text);
    }

    static JsonNode parseJson(String text, List<String> contentType) {
        String t = text.strip();
        boolean looksJson = t.startsWith("{") || t.startsWith("[")
                || contentType.stream().anyMatch(c -> c.toLowerCase().contains("json"));
        if (t.isEmpty() || !looksJson) {
            return null;
        }
        try {
            return JSON.readTree(t);
        } catch (IOException e) {
            return null;
        }
    }

    private OperationSlice operationOf(JsonNode st, Refs refs) {
        String method = st.path("method").asText();
        OperationSlice op = resolver.resolveOperation(method, st.path("path").asText());
        if (op == null) {
            try {
                op = resolver.resolveOperation(method, refs.substituteText(st.path("path").asText()));
            } catch (Refs.MissingRef ignored) {
                // stays null
            }
        }
        return op;
    }

    /** Returns null on success, otherwise why an extraction found nothing. */
    private static String extract(JsonNode extract, AssertionEvaluator.Response r, Refs refs, ObjectNode sr) {
        if (!extract.isObject() || extract.isEmpty()) {
            return null;
        }
        ObjectNode er = sr.putObject("extracted");
        for (Iterator<Map.Entry<String, JsonNode>> it = extract.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (r.json() == null) {
                return "extract '" + e.getKey() + "': response body is not JSON";
            }
            JsonPathLite.Parsed p = JsonPathLite.parse(e.getValue().asText());
            List<JsonNode> values = p == null ? List.of() : JsonPathLite.evaluate(r.json(), p);
            if (values.isEmpty() || values.get(0).isNull()) {
                return "extract '" + e.getKey() + "' found nothing at " + e.getValue().asText();
            }
            refs.put(e.getKey(), values.get(0));
            er.set(e.getKey(), values.get(0));
        }
        return null;
    }
}
