package restdd.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the client against a fake Ollama server (no model needed). */
class OllamaClientTest {

    @TempDir Path dir;
    HttpServer server;
    final AtomicInteger chatCalls = new AtomicInteger();
    final AtomicReference<String> chatReply = new AtomicReference<>();
    final AtomicReference<Integer> chatStatus = new AtomicReference<>(200);
    final AtomicReference<String> lastChatBody = new AtomicReference<>();
    final AtomicReference<String> psReply = new AtomicReference<>();
    OllamaClient client;
    final GenerationOptions opts = new GenerationOptions("m:4b", 8192, 1024, 0.7, 0.8, 20, 1.0, false, "30m");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", ex -> {
            chatCalls.incrementAndGet();
            lastChatBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(ex, chatStatus.get(), chatReply.get());
        });
        server.createContext("/api/generate", ex -> reply(ex, 200, "{\"done\":true}"));
        server.createContext("/api/ps", ex -> reply(ex, 200, psReply.get()));
        server.createContext("/api/version", ex -> reply(ex, 200, "{\"version\":\"0.40.17\"}"));
        server.createContext("/api/tags", ex -> reply(ex, 200,
                "{\"models\":[{\"name\":\"m:4b\",\"model\":\"m:4b\",\"digest\":\"abc123\"}]}"));
        server.start();
        client = new OllamaClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                dir.resolve("cache"), dir.resolve("calls.jsonl"), Duration.ofSeconds(10));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static String chat(String content, String doneReason, int prompt, int eval) {
        return String.format("{\"message\":{\"role\":\"assistant\",\"content\":%s},\"done\":true,\"done_reason\":\"%s\","
                        + "\"prompt_eval_count\":%d,\"eval_count\":%d,\"total_duration\":5,\"eval_duration\":3}",
                new ObjectMapper().valueToTree(content).toString(), doneReason, prompt, eval);
    }

    @Test
    void sendsGuardsAndCachesCalls() throws Exception {
        chatReply.set(chat("{\"tests\":[]}", "stop", 900, 300));
        ChatResult first = client.chat("sys", "user", new ObjectMapper().readTree("{\"type\":\"object\"}"), opts, 7,
                "abc123", Map.of("arm", "A"));
        assertFalse(first.fromCache());
        assertEquals("{\"tests\":[]}", first.content());
        var sent = new ObjectMapper().readTree(lastChatBody.get());
        assertFalse(sent.path("truncate").asBoolean(true), "truncate=false must be sent");
        assertFalse(sent.path("shift").asBoolean(true), "shift=false must be sent");
        assertFalse(sent.path("stream").asBoolean(true));
        assertEquals(8192, sent.at("/options/num_ctx").asInt());
        assertEquals(7, sent.at("/options/seed").asInt());
        assertEquals("object", sent.at("/format/type").asText());
        assertFalse(sent.path("think").asBoolean(true));

        ChatResult again = client.chat("sys", "user", new ObjectMapper().readTree("{\"type\":\"object\"}"), opts, 7,
                "abc123", Map.of("arm", "A"));
        assertTrue(again.fromCache());
        assertEquals(1, chatCalls.get(), "second identical call served from cache");
        client.chat("sys", "user", new ObjectMapper().readTree("{\"type\":\"object\"}"), opts, 8, "abc123", Map.of());
        assertEquals(2, chatCalls.get(), "a different seed is a different call");
        assertEquals(3, Files.readAllLines(dir.resolve("calls.jsonl")).size());
    }

    @Test
    void outputCapIsRecordedNotRetried() throws Exception {
        chatReply.set(chat("{\"tests\":[", "length", 900, 1024));
        ChatResult r = client.chat("s", "u", new ObjectMapper().readTree("{}"), opts, 1, "abc123", Map.of());
        assertTrue(r.outputTruncated());
        assertEquals(1, chatCalls.get());
    }

    @Test
    void promptTooLongIsAnErrorNeverSilent() throws Exception {
        chatStatus.set(400);
        chatReply.set("{\"error\":\"input length exceeds maximum context length\"}");
        LlmException e = assertThrows(LlmException.class,
                () -> client.chat("s", "u", new ObjectMapper().readTree("{}"), opts, 1, "abc123", Map.of()));
        assertEquals(LlmException.Kind.PROMPT_TOO_LONG, e.kind());

        chatStatus.set(200);
        chatReply.set(chat("{}", "stop", 8000, 300));
        LlmException e2 = assertThrows(LlmException.class,
                () -> client.chat("s", "u2", new ObjectMapper().readTree("{}"), opts, 1, "abc123", Map.of()));
        assertEquals(LlmException.Kind.PROMPT_TOO_LONG, e2.kind());
    }

    @Test
    void preflightRequiresFullGpuAndContext() {
        psReply.set("{\"models\":[{\"name\":\"m:4b\",\"size\":3200,\"size_vram\":3200,\"context_length\":8192}]}");
        assertNotNull(client.preflight(opts));
        psReply.set("{\"models\":[{\"name\":\"m:4b\",\"size\":3200,\"size_vram\":0}]}");
        assertEquals(LlmException.Kind.NOT_ON_GPU, assertThrows(LlmException.class, () -> client.preflight(opts)).kind());
        psReply.set("{\"models\":[{\"name\":\"m:4b\",\"size\":3200,\"size_vram\":3200,\"context_length\":4096}]}");
        assertEquals(LlmException.Kind.NOT_ON_GPU, assertThrows(LlmException.class, () -> client.preflight(opts)).kind());
        assertEquals("abc123", client.modelDigest("m:4b"));
        assertEquals("0.40.17", client.version());
    }
}
