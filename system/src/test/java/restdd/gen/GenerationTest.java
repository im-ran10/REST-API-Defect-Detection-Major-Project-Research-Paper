package restdd.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import restdd.llm.GenerationOptions;
import restdd.llm.OllamaClient;
import restdd.prompt.PromptBuilder;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** T6: operation selection (D47), GPU reading parse, and a generation run against a fake Ollama server. */
class GenerationTest {

    @TempDir Path dir;
    HttpServer server;
    final AtomicInteger chatCalls = new AtomicInteger();
    static final ObjectMapper JSON = new ObjectMapper();

    static OperationSlice op(String method, String path) {
        return new OperationSlice(method + " " + path, method, path, JSON.createObjectNode(), "", 10);
    }

    // ------------------------------------------------------------------ D47 selection

    @Test
    void capSelectsProportionallyAndDeterministically() {
        List<OperationSlice> ops = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ops.add(op("GET", "/products/p" + i));
        }
        for (int i = 0; i < 10; i++) {
            ops.add(op("GET", "/users/u" + i));
        }
        List<OperationSlice> a = OperationSelector.select(ops, 8);
        List<OperationSlice> reversed = new ArrayList<>(ops);
        java.util.Collections.reverse(reversed);
        List<OperationSlice> b = OperationSelector.select(reversed, 8);
        assertEquals(8, a.size());
        assertEquals(6L, a.stream().filter(o -> o.path().startsWith("/products")).count(), "30/40 of 8");
        assertEquals(2L, a.stream().filter(o -> o.path().startsWith("/users")).count(), "10/40 of 8");
        assertEquals(a.stream().map(OperationSlice::key).toList(), b.stream().map(OperationSlice::key).toList(),
                "input order must not matter");
        assertEquals(OperationSelector.listSha256(a), OperationSelector.listSha256(b));
    }

    @Test
    void largestRemainderFillsTheCapExactly() {
        List<OperationSlice> ops = new ArrayList<>();
        for (String s : List.of("a", "b", "c")) {
            for (int i = 0; i < 5; i++) {
                ops.add(op("GET", "/" + s + "/x" + i));
            }
        }
        assertEquals(7, OperationSelector.select(ops, 7).size());
        assertEquals(15, OperationSelector.select(ops, 0).size(), "cap 0 = all");
        assertEquals(15, OperationSelector.select(ops, 99).size(), "cap above total = all");
    }

    @Test
    void gpuReadingParsesAndToleratesMissingPower() {
        GpuMonitor.Reading r = GpuMonitor.parse("78, 87.84, 1852, 96, 3831\n");
        assertTrue(r.available());
        assertEquals(78, r.temperatureC());
        assertEquals(1852, r.clockMhz());
        GpuMonitor.Reading na = GpuMonitor.parse("66, [N/A], 1500, 40, 2000");
        assertTrue(na.available());
        assertEquals(-1.0, na.powerW());
        assertFalse(GpuMonitor.parse("garbage").available());
    }

    // ------------------------------------------------------------------ generation run

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String suite = "{\"tests\":[{\"title\":\"list\",\"steps\":[{\"method\":\"GET\",\"path\":\"/notes\","
                + "\"assertions\":[{\"type\":\"status\",\"op\":\"equals\",\"value\":200},"
                + "{\"type\":\"body\",\"path\":\"$[0].text\",\"op\":\"isType\",\"value\":\"string\"}]}]}]}"; // named part: non-trivial under R-T4
        String chat = String.format("{\"message\":{\"role\":\"assistant\",\"content\":%s},\"done\":true,\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":900,\"eval_count\":120,\"total_duration\":2000000000,\"eval_duration\":1000000000}",
                JSON.valueToTree(suite).toString());
        server.createContext("/api/chat", ex -> {
            chatCalls.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            reply(ex, chat);
        });
        server.createContext("/api/generate", ex -> reply(ex, "{\"done\":true}"));
        server.createContext("/api/ps", ex -> reply(ex,
                "{\"models\":[{\"name\":\"m:4b\",\"size\":3200,\"size_vram\":3200,\"context_length\":16384}]}"));
        server.createContext("/api/version", ex -> reply(ex, "{\"version\":\"0.40.17\"}"));
        server.createContext("/api/tags", ex -> reply(ex,
                "{\"models\":[{\"name\":\"m:4b\",\"model\":\"m:4b\",\"digest\":\"abc123\"}]}"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void runArchivesEveryCallAndResumesFromTheCache() throws Exception {
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(GenerationTest.class.getResource("/specs/notes_openapi30.yaml").toURI()));
        List<OperationSlice> ops = OperationSelector.select(spec.operations(), 2);
        GenerationOptions o = new GenerationOptions("m:4b", 16384, 2048, 0.7, 0.8, 20, 1.0, null, "30m");
        GenerationRun.Config cfg = new GenerationRun.Config("notes", spec, spec, ops, List.of("A"), List.of(1L, 2L), 1,
                List.of("uid"), op -> "", null, o, (arm, seed, op) -> GenerationRun.Task.plain(PromptBuilder.armATask(1)));
        Path work = dir.resolve("run");
        OllamaClient client = new OllamaClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                work.resolve("cache"), work.resolve("ollama-calls.jsonl"), Duration.ofSeconds(10));
        GpuMonitor gpu = new GpuMonitor(85, 75);

        GenerationRun.Summary first = new GenerationRun(cfg, client, new PromptBuilder(), gpu, work).run();
        assertEquals(4, first.calls());
        assertEquals(4, first.schemaValid());
        assertEquals(0, first.fromCache());
        assertEquals(4, chatCalls.get());

        List<String> lines = Files.readAllLines(work.resolve("calls.jsonl"));
        assertEquals(4, lines.size());
        JsonNode rec = JSON.readTree(lines.get(0));
        assertTrue(rec.path("userPrompt").asText().contains("OPERATION UNDER TEST"));
        assertTrue(rec.path("userPrompt").asText().contains("${fixture.uid}"));
        assertTrue(rec.path("response").asText().contains("\"tests\""));
        assertTrue(rec.at("/validation/schemaValid").asBoolean());
        assertEquals("MEANINGFUL", rec.at("/validation/tests/0/assertionClass").asText());
        assertEquals(1, rec.path("seed").asInt());
        assertFalse(rec.path("thinkingDetected").asBoolean());
        assertTrue(rec.path("thinkingMode").asText().startsWith("non-thinking"));

        GenerationRun.Summary again = new GenerationRun(cfg, client, new PromptBuilder(), gpu, work).run();
        assertEquals(4, again.fromCache(), "a re-run resumes from the cache");
        assertEquals(4, chatCalls.get(), "no new model calls on resume");
        assertEquals(4, Files.readAllLines(work.resolve("calls.jsonl")).size());

        var m = new GenerationRun(cfg, client, new PromptBuilder(), gpu, work).manifest("abc123", "0.40.17");
        assertEquals(2, m.path("operationCount").asInt());
        assertEquals(OperationSelector.listSha256(ops), m.path("operationListSha256").asText());
        assertEquals(64, m.path("systemPromptSha256").asText().length());
        assertEquals(0.7, m.at("/options/temperature").asDouble());
        assertTrue(m.path("thinkingMode").asText().startsWith("non-thinking"));
    }
}
