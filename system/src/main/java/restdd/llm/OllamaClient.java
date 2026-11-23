package restdd.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Local Ollama client (T3, D27). Uses the native {@code /api/chat} endpoint (the OpenAI-compatible one
 * ignores {@code num_ctx}). Guards required by D43:
 * <ul>
 *   <li>{@code truncate=false} and {@code shift=false}: a prompt that does not fit is an error, never silently
 *       cut; additionally {@code prompt_eval_count + eval_count} must stay below {@code num_ctx}.</li>
 *   <li>{@link #preflight}: the model must be loaded 100% on the GPU with the configured context window.</li>
 *   <li>No retries on content: an invalid response is returned as is and counts as the arm's outcome (D26).</li>
 * </ul>
 * Every call is cached on disk under a hash of the complete request (model digest included), so an
 * interrupted overnight run resumes without repeating finished calls, and every call is appended to a JSONL log.
 */
public final class OllamaClient {

    private static final ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final URI base;
    private final HttpClient http;
    private final Path cacheDir;
    private final Path callLog;
    private final Duration callTimeout;

    public OllamaClient(URI base, Path cacheDir, Path callLog, Duration callTimeout) throws IOException {
        this.base = base;
        this.http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(10)).build();
        this.cacheDir = cacheDir;
        this.callLog = callLog;
        this.callTimeout = callTimeout;
        Files.createDirectories(cacheDir);
        if (callLog.getParent() != null) {
            Files.createDirectories(callLog.getParent());
        }
    }

    // ------------------------------------------------------------------ metadata

    public String version() {
        return get("/api/version").path("version").asText("unknown");
    }

    /** Full sha256 digest of a pulled model, as listed by {@code /api/tags}. */
    public String modelDigest(String model) {
        for (JsonNode m : get("/api/tags").path("models")) {
            if (model.equals(m.path("name").asText()) || model.equals(m.path("model").asText())) {
                return m.path("digest").asText();
            }
        }
        throw new LlmException(LlmException.Kind.BAD_REQUEST, "model not pulled: " + model);
    }

    /**
     * Loads the model with the given options (a one-token call) and checks {@code /api/ps}: the whole model must
     * be in VRAM and, when Ollama reports it, the context window must equal {@code num_ctx}.
     */
    public JsonNode preflight(GenerationOptions o) {
        ObjectNode req = JSON.createObjectNode();
        req.put("model", o.model());
        req.put("prompt", "OK");
        req.put("stream", false);
        req.put("keep_alive", o.keepAlive());
        ObjectNode opts = o.toOllamaOptions(0);
        opts.put("num_predict", 1);
        req.set("options", opts);
        post("/api/generate", req);
        for (JsonNode m : get("/api/ps").path("models")) {
            if (o.model().equals(m.path("name").asText()) || o.model().equals(m.path("model").asText())) {
                long size = m.path("size").asLong();
                long vram = m.path("size_vram").asLong();
                if (size <= 0 || vram < size) {
                    String hint = vram == 0
                            ? "Ollama is running on the CPU (GPU discovery failed): sudo systemctl restart ollama, then retry"
                            : "the model does not fit in GPU memory at num_ctx " + o.numCtx()
                              + ": lower num_ctx, or use the q8_0 KV cache with flash attention (README)";
                    throw new LlmException(LlmException.Kind.NOT_ON_GPU, String.format(
                            "%s is %d of %d bytes in VRAM; %s", o.model(), vram, size, hint));
                }
                if (m.has("context_length") && m.get("context_length").asInt() != o.numCtx()) {
                    throw new LlmException(LlmException.Kind.NOT_ON_GPU, "loaded with context "
                            + m.get("context_length").asInt() + ", expected " + o.numCtx());
                }
                return m;
            }
        }
        throw new LlmException(LlmException.Kind.NOT_ON_GPU, o.model() + " not listed by /api/ps after loading");
    }

    // ------------------------------------------------------------------ chat

    /**
     * One structured-output call. {@code tags} (arm, project, operation, seed, ...) go to the call log only;
     * they never reach the model.
     */
    public ChatResult chat(String system, String user, JsonNode schema, GenerationOptions o, long seed,
                           String modelDigest, Map<String, String> tags) {
        ObjectNode req = JSON.createObjectNode();
        req.put("model", o.model());
        ArrayNode messages = req.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        req.put("stream", false);
        req.set("format", schema);
        if (o.think() != null) {
            req.put("think", o.think());
        }
        req.put("truncate", false);
        req.put("shift", false);
        req.put("keep_alive", o.keepAlive());
        req.set("options", o.toOllamaOptions(seed));

        String key = cacheKey(req, modelDigest);
        Path cached = cacheDir.resolve(key + ".json");
        if (Files.exists(cached)) {
            try {
                JsonNode c = JSON.readTree(cached.toFile());
                ChatResult r = toResult(key, true, c.path("response"));
                log(r, o, seed, modelDigest, tags);
                return r;
            } catch (IOException e) {
                throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "unreadable cache entry " + cached);
            }
        }

        JsonNode resp = post("/api/chat", req);
        ChatResult r = toResult(key, false, resp);
        if (r.promptEvalCount() <= 0) {
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "response without prompt_eval_count");
        }
        if (r.promptEvalCount() + r.evalCount() >= o.numCtx()) {
            throw new LlmException(LlmException.Kind.PROMPT_TOO_LONG, "prompt " + r.promptEvalCount() + " + output "
                    + r.evalCount() + " tokens reach num_ctx " + o.numCtx());
        }
        ObjectNode store = JSON.createObjectNode();
        store.set("request", req);
        store.put("modelDigest", modelDigest);
        store.put("storedAt", Instant.now().toString());
        store.set("response", resp);
        try {
            Path tmp = cacheDir.resolve(key + ".tmp");
            Files.writeString(tmp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(store));
            Files.move(tmp, cached, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "cannot write cache: " + e.getMessage());
        }
        log(r, o, seed, modelDigest, tags);
        return r;
    }

    private static ChatResult toResult(String key, boolean fromCache, JsonNode resp) {
        return new ChatResult(key, fromCache, resp.path("message").path("content").asText(""),
                resp.path("done_reason").asText(""), resp.path("prompt_eval_count").asInt(0),
                resp.path("eval_count").asInt(0), resp.path("total_duration").asLong(0),
                resp.path("load_duration").asLong(0), resp.path("prompt_eval_duration").asLong(0),
                resp.path("eval_duration").asLong(0));
    }

    static String cacheKey(JsonNode request, String modelDigest) {
        try {
            String canonical = JSON.writeValueAsString(JSON.treeToValue(request, Object.class)) + "|" + modelDigest;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void log(ChatResult r, GenerationOptions o, long seed, String digest, Map<String, String> tags) {
        ObjectNode line = JSON.createObjectNode();
        line.put("time", Instant.now().toString());
        tags.forEach(line::put);
        line.put("cacheKey", r.cacheKey());
        line.put("fromCache", r.fromCache());
        line.put("model", o.model());
        line.put("modelDigest", digest);
        line.put("numCtx", o.numCtx());
        line.put("seed", seed);
        line.put("doneReason", r.doneReason());
        line.put("promptEvalCount", r.promptEvalCount());
        line.put("evalCount", r.evalCount());
        line.put("totalMs", r.totalNs() / 1_000_000);
        line.put("loadMs", r.loadNs() / 1_000_000);
        line.put("promptEvalMs", r.promptEvalNs() / 1_000_000);
        line.put("evalMs", r.evalNs() / 1_000_000);
        try {
            Files.writeString(callLog, JSON.writeValueAsString(line) + "\n", StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "cannot write call log: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ HTTP

    private JsonNode get(String path) {
        return send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).GET().build());
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            return send(HttpRequest.newBuilder(base.resolve(path)).timeout(callTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build());
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.BAD_REQUEST, e.getMessage());
        }
    }

    private JsonNode send(HttpRequest req) {
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "Ollama unreachable at " + base + ": " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, "interrupted");
        }
        JsonNode body;
        try {
            body = JSON.readTree(resp.body());
        } catch (IOException e) {
            body = JSON.createObjectNode().put("error", resp.body());
        }
        int code = resp.statusCode();
        if (code >= 500) {
            throw new LlmException(LlmException.Kind.INFRASTRUCTURE, code + " " + body.path("error").asText());
        }
        if (code >= 400) {
            String err = body.path("error").asText();
            String lower = err.toLowerCase();
            if (lower.contains("context") || lower.contains("exceeds") || lower.contains("too long")) {
                throw new LlmException(LlmException.Kind.PROMPT_TOO_LONG, err);
            }
            throw new LlmException(LlmException.Kind.BAD_REQUEST, code + " " + err);
        }
        return body;
    }
}
