package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import restdd.dsl.StaticValidator;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** T5 runner against a fake Notes API (no real deployment needed). */
class ExecutorTest {

    static NormalisedSpec spec;
    static StaticValidator validator;
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SECRET = "s3cr3t-api-key-value";

    HttpServer server;
    String base;
    @org.junit.jupiter.api.io.TempDir Path dir;

    @BeforeAll
    static void load() throws Exception {
        spec = SpecNormaliser.normalise(Path.of(ExecutorTest.class.getResource("/specs/notes_openapi30.yaml").toURI()));
        validator = new StaticValidator(spec, Set.of("uid"));
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/notes", ex -> {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            switch (method + " " + path) {
                case "POST /api/notes" -> {
                    JsonNode b = JSON.readTree(body);
                    reply(ex, 201, "{\"id\":7,\"text\":" + b.path("text").toString() + "}");
                }
                case "GET /api/notes" -> reply(ex, 200, "[{\"id\":1,\"text\":\"a\"},{\"id\":2,\"text\":null}]");
                case "GET /api/notes/7" -> reply(ex, 200, "{\"id\":7,\"text\":\"hello\",\"tags\":[\"a\",\"b\"]}");
                case "DELETE /api/notes/9" -> reply(ex, 503, "unavailable");
                case "PUT /api/notes/5" -> reply(ex, 200, "{\"seenKey\":\"" + ex.getRequestHeaders().getFirst("DOLAPIKEY") + "\"}");
                case "GET /api/notes/8" -> {
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream os = ex.getResponseBody();
                    os.write("[".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException ignored) {
                        // test shutdown
                    }
                    ex.close();
                }
                default -> reply(ex, 404, "{\"error\":\"not found\"}");
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", body.startsWith("{") || body.startsWith("[") ? "application/json" : "text/plain");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private TestExecutor executor(String baseUrl, AuthConfig auth) {
        return new TestExecutor(new ExecConfig(baseUrl, auth, null, Map.of(), Duration.ofSeconds(5),
                Duration.ofSeconds(1), 2_000_000, 32_000), validator);
    }

    private TestExecutor.TestRun run(String testJson) throws Exception {
        return executor(base, new AuthConfig("header", "DOLAPIKEY", SECRET)).run(JSON.readTree(testJson));
    }

    @Test
    void createThenReadPassesWithTypedReference() throws Exception {
        var r = run("""
                {"title":"create then read","steps":[
                 {"method":"POST","path":"/notes","body":{"text":"hello"},"extract":{"noteId":"$.id"},
                  "assertions":[{"type":"status","op":"in","value":[200,201]}]},
                 {"method":"GET","path":"/notes/{id}","pathParams":{"id":"${noteId}"},
                  "assertions":[{"type":"status","op":"equals","value":"200"},
                                {"type":"body","path":"$.text","op":"equals","value":"hello"},
                                {"type":"body","path":"$.id","op":"equals","value":"${noteId}"},
                                {"type":"body","path":"$.tags","op":"lengthEquals","value":2},
                                {"type":"header","name":"content-type","op":"contains","value":"json"},
                                {"type":"responseSchema","op":"conformsToSpec"}]}]}""");
        assertEquals(Outcome.PASS, r.outcome(), r.reason());
        assertEquals(base + "/notes/7", r.record().at("/steps/1/request/url").asText());
        assertEquals(7, r.record().at("/steps/0/extracted/noteId").asInt());
    }

    @Test
    void failingAssertionStopsTheTest() throws Exception {
        var r = run("""
                {"title":"wrong text","steps":[
                 {"method":"GET","path":"/notes/7","assertions":[{"type":"body","path":"$.text","op":"equals","value":"bye"}]},
                 {"method":"GET","path":"/notes","assertions":[]}]}""");
        assertEquals(Outcome.FAIL, r.outcome());
        assertTrue(r.reason().contains("step 1") && r.reason().contains("\"hello\""), r.reason());
        assertEquals(1, r.record().path("steps").size(), "step 2 not sent after a failure");
    }

    @Test
    void wildcardMustHoldForEveryElementAndNeverVacuously() throws Exception {
        assertEquals(Outcome.PASS, run("""
                {"title":"ids positive","steps":[{"method":"GET","path":"/notes",
                 "assertions":[{"type":"body","path":"$[*].id","op":"gt","value":0}]}]}""").outcome());
        assertEquals(Outcome.FAIL, run("""
                {"title":"texts are strings","steps":[{"method":"GET","path":"/notes",
                 "assertions":[{"type":"body","path":"$[*].text","op":"isType","value":"string"}]}]}""").outcome());
        assertEquals(Outcome.FAIL, run("""
                {"title":"no such field","steps":[{"method":"GET","path":"/notes",
                 "assertions":[{"type":"body","path":"$[*].owner","op":"isNotEmpty"}]}]}""").outcome());
        assertEquals(Outcome.PASS, run("""
                {"title":"field absent","steps":[{"method":"GET","path":"/notes",
                 "assertions":[{"type":"body","path":"$[*].owner","op":"notExists"}]}]}""").outcome());
    }

    @Test
    void missingExtractIsASetupError() throws Exception {
        var r = run("""
                {"title":"bad extract","steps":[
                 {"method":"POST","path":"/notes","body":{"text":"x"},"extract":{"nid":"$.noteId"},"assertions":[]},
                 {"method":"GET","path":"/notes/{id}","pathParams":{"id":"${nid}"},"assertions":[]}]}""");
        assertEquals(Outcome.SETUP_ERROR, r.outcome(), r.reason());
    }

    @Test
    void gatewayStatusAndConnectionErrorsAreInfrastructure() throws Exception {
        assertEquals(Outcome.INFRA_ERROR, run("""
                {"title":"delete","steps":[{"method":"DELETE","path":"/notes/9",
                 "assertions":[{"type":"status","op":"equals","value":503}]}]}""").outcome());
        int port = server.getAddress().getPort();
        server.stop(0);
        var r = executor("http://127.0.0.1:" + port + "/api", AuthConfig.NONE).run(JSON.readTree("""
                {"title":"down","steps":[{"method":"GET","path":"/notes","assertions":[]}]}"""));
        assertEquals(Outcome.INFRA_ERROR, r.outcome(), r.reason());
    }

    @Test
    void streamingBodyIsCutNotAwaited() throws Exception {
        long t0 = System.nanoTime();
        var r = run("""
                {"title":"stream","steps":[{"method":"GET","path":"/notes/8",
                 "assertions":[{"type":"status","op":"equals","value":200},
                               {"type":"body","path":"$","op":"isType","value":"array"}]}]}""");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 3500, "body read must stop at the body timeout, took " + ms + " ms");
        assertEquals(Outcome.FAIL, r.outcome(), "status ok, but a cut body is not JSON");
        assertTrue(r.record().at("/steps/0/response/bodyTimedOut").asBoolean());
    }

    @Test
    void secretsNeverReachTheRecord() throws Exception {
        var r = run("""
                {"title":"echo key","steps":[{"method":"PUT","path":"/notes/5","body":{"text":"t"},
                 "assertions":[{"type":"body","path":"$.seenKey","op":"equals","value":"nope"}]}]}""");
        assertEquals(Outcome.FAIL, r.outcome());
        assertFalse(r.record().toString().contains(SECRET), "secret leaked into the record");
        assertFalse(r.reason().contains(SECRET), "secret leaked into the reason");
        assertTrue(r.record().toString().contains(Redactor.MARK));
    }

    @Test
    void secondaryRoleWithoutFixtureIsInvalid() throws Exception {
        var r = run("""
                {"title":"other user","steps":[{"method":"GET","path":"/notes","auth":"secondary","assertions":[]}]}""");
        assertEquals(Outcome.INVALID, r.outcome(), r.reason());
    }

    @Test
    void responseSchemaChecksDocumentedStatusAndBody() throws Exception {
        assertEquals(Outcome.PASS, run("""
                {"title":"list conforms","steps":[{"method":"GET","path":"/notes",
                 "assertions":[{"type":"responseSchema","op":"conformsToSpec"}]}]}""").outcome());
        var r = run("""
                {"title":"undocumented 404","steps":[{"method":"GET","path":"/notes/99",
                 "assertions":[{"type":"responseSchema","op":"conformsToSpec"}]}]}""");
        assertEquals(Outcome.FAIL, r.outcome());
        assertTrue(r.reason().contains("404 is not documented"), r.reason());
    }

    @Test
    void uidFixtureIsFreshPerRun() throws Exception {
        String t = """
                {"title":"unique name","steps":[{"method":"POST","path":"/notes","body":{"text":"${fixture.uid}"},
                 "assertions":[{"type":"body","path":"$.text","op":"matches","value":"^rd[0-9a-f]{8}$"}]}]}""";
        var a = run(t);
        var b = run(t);
        assertEquals(Outcome.PASS, a.outcome(), a.reason());
        assertNotEquals(a.record().path("uid").asText(), b.record().path("uid").asText());
    }

    // ------------------------------------------------------------------ suite level

    private SuiteRunner suiteRunner(ResetAdapter reset) {
        return new SuiteRunner(validator, executor(base, AuthConfig.NONE), reset);
    }

    private static final String TWO_TESTS = """
            {"tests":[
              {"title":"ok","steps":[{"method":"GET","path":"/notes","assertions":[{"type":"status","op":"equals","value":200}]}]},
              {"title":"no such op","steps":[{"method":"GET","path":"/users","assertions":[]}]}]}""";

    @Test
    void suiteRunsValidTestsAndMarksInvalidOnes() {
        ObjectNode rec = suiteRunner(new ResetAdapter.NoReset()).run(TWO_TESTS, 2, Map.of("label", "t"), null);
        assertTrue(rec.path("executed").asBoolean());
        assertEquals("PASS", rec.at("/tests/0/outcome").asText());
        assertEquals("INVALID", rec.at("/tests/1/outcome").asText());
        assertEquals(1, rec.at("/summary/PASS").asInt());
        assertTrue(rec.at("/validation/schemaValid").asBoolean());
    }

    @Test
    void schemaInvalidSuiteRunsNothing() {
        ObjectNode rec = suiteRunner(new ResetAdapter.NoReset()).run(TWO_TESTS, 3, Map.of(), null);
        assertFalse(rec.path("executed").asBoolean());
        assertEquals(0, rec.path("tests").size());
    }

    @Test
    void failedResetMakesTestsInfrastructureErrors() {
        ResetAdapter broken = new ResetAdapter() {
            public String name() { return "broken"; }
            public void snapshot(ObjectNode r) { }
            public void restore(ObjectNode r) throws ResetException { throw new ResetException("db restore failed"); }
        };
        ObjectNode rec = suiteRunner(broken).run(TWO_TESTS, 2, Map.of(), List.of(0));
        assertEquals(1, rec.path("tests").size(), "only the selected test (isolated re-run)");
        assertEquals("INFRA_ERROR", rec.at("/tests/0/outcome").asText());
    }

    @Test
    void commandResetRunsScriptsAndRecordsFailures() throws Exception {
        CommandReset ok = new CommandReset("cmd", "echo snap > " + dir.resolve("s.txt"), "echo restored",
                null, base + "/notes", AuthConfig.NONE, Duration.ofSeconds(5), Duration.ofSeconds(10));
        ObjectNode rec = JSON.createObjectNode();
        ok.snapshot(rec);
        ok.restore(rec);
        assertEquals(0, rec.at("/restore/exitCode").asInt());
        assertTrue(rec.at("/restore/output").asText().contains("restored"));
        CommandReset bad = new CommandReset("cmd", "true", "exit 3", null, null, AuthConfig.NONE,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
        assertThrows(ResetAdapter.ResetException.class, () -> bad.restore(JSON.createObjectNode()));
    }
}
