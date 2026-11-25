package restdd.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import restdd.exec.AuthConfig;
import restdd.exec.ExecConfig;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** T7: shape extraction (no values), two-pass intersection, GET-only exploration with identifiers chosen by code. */
class ExploreTest {

    static final ObjectMapper JSON = new ObjectMapper();
    HttpServer server;
    String base;
    final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    volatile boolean secondPassAddsField = false;
    int listCalls = 0;

    // ------------------------------------------------------------------ shapes

    @Test
    void shapesKeepKeysTypesAndFormatsButNoValues() throws Exception {
        JsonNode v = JSON.readTree("""
                {"id": 17, "ref": "PR-SECRET-0042", "price": "12.50000000", "date_creation": "2022-12-11 10:00:00",
                 "uuid": "0b0e3c1a-4a8f-4d0e-9a3e-2f6c1f0b9d11", "email": "x@example.com", "tags": ["a", "b"],
                 "lines": [{"rowid": "3", "qty": 2}, {"rowid": "4", "qty": 2.5, "note": null}],
                 "byId": {"101": {"n": "x"}, "102": {"n": "y"}}}""");
        JsonNode s = Shapes.of(v);
        String text = Shapes.render(s, 4);
        for (String value : List.of("PR-SECRET-0042", "12.5", "2022", "0b0e3c1a", "example.com", "101", "102")) {
            assertFalse(text.contains(value), "value leaked into the shape: " + value + " in " + text);
        }
        assertTrue(text.contains("id: integer"), text);
        assertTrue(text.contains("price: string:number"), text);
        assertTrue(text.contains("date_creation: string:date-time"), text);
        assertTrue(text.contains("uuid: string:uuid"), text);
        assertTrue(text.contains("email: string:email"), text);
        assertTrue(text.contains("lines: [{qty: number, rowid: string:integer, note: null}]"), text);
        assertTrue(text.contains("byId: map<string, {n: string}>"), text);
        assertTrue(Shapes.hasIdLikeField(s));
        assertTrue(Shapes.hasReferenceArray(s), "lines carry rowid");
    }

    @Test
    void intersectionKeepsFieldsSeenInBothRuns() {
        List<String> diffs = new ArrayList<>();
        JsonNode a = Shapes.of(JSON.valueToTree(Map.of("id", 1, "a", "x", "only1", true)));
        JsonNode b = Shapes.of(JSON.valueToTree(Map.of("id", 2, "a", 3, "only2", false)));
        String text = Shapes.render(Shapes.intersect(a, b, "$", diffs), 3);
        assertEquals("{a: mixed, id: integer}", text);
        assertEquals(3, diffs.size(), diffs.toString());
    }

    @Test
    void wideEntitiesKeepFieldNamesAndIdKeyedObjectsBecomeMaps() throws Exception {
        StringBuilder wide = new StringBuilder("{");
        for (int i = 0; i < 60; i++) {
            wide.append("\"field").append(i).append("\": ").append(i % 2 == 0 ? "null" : "\"v\"").append(i < 59 ? "," : "");
        }
        wide.append("}");
        String text = Shapes.render(Shapes.of(JSON.readTree(wide.toString())), 3);
        assertTrue(text.startsWith("{field1: string, "), "non-null fields first, names kept: " + text.substring(0, 60));
        assertTrue(Shapes.render(Shapes.of(JSON.readTree("{\"17\": {\"a\": 1}, \"18\": {\"a\": 2}}")), 3).startsWith("map<string, "));
    }

    @Test
    void identifiersCanComeFromAnyListWithAMatchingField() throws Exception {
        Map<String, JsonNode> bodies = Map.of("/v2/all", JSON.readTree("""
                [{"name": "N1", "capital": "C1", "alpha3Code": "A31", "callingCodes": ["93"],
                  "currencies": [{"code": "AFN", "name": "x"}], "regionalBlocs": [{"acronym": "SAARC"}]},
                 {"name": "N2", "capital": "C2", "alpha3Code": "A32", "callingCodes": ["355"],
                  "currencies": [{"code": "ALL"}], "regionalBlocs": []}]"""));
        assertEquals(List.of("C1", "C2"), Explorer.fromList("/v2/capital/{capital}", "capital", bodies));
        assertEquals(List.of("A31", "A32"), Explorer.fromList("/v2/alpha/{code}", "code", bodies));
        assertEquals(List.of("93", "355"), Explorer.fromList("/v2/callingcode/{callingcode}", "callingcode", bodies));
        assertEquals(List.of("AFN", "ALL"), Explorer.fromList("/v2/currency/{currency}", "currency", bodies));
        assertEquals(List.of(), Explorer.fromList("/v2/lang/{et}", "et", bodies));
    }

    @Test
    void ownSectionListsComeFirstAndSingleIdKeysAreMaps() throws Exception {
        Map<String, JsonNode> bodies = Map.of(
                "/agendaevents", JSON.readTree("[{\"id\": \"1\", \"ref\": \"7\"}]"),
                "/invoices", JSON.readTree("[{\"id\": \"3\", \"ref\": \"FA-1\"}]"));
        assertEquals(List.of("FA-1"), Explorer.fromList("/invoices/ref/{ref}", "ref", bodies));
        String text = Shapes.render(Shapes.of(JSON.readTree("{\"opened\": 1, \"refs\": {\"1\": \"PR-0001\"}}")), 3);
        assertTrue(!text.contains("1:") && text.contains("refs: map<string, string>"), text);
    }

    // ------------------------------------------------------------------ explorer

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/notes", ex -> {
            requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            String path = ex.getRequestURI().getPath();
            if (path.equals("/api/notes")) {
                listCalls++;
                String extra = secondPassAddsField && listCalls > 1 ? ",\"color\":\"red\"" : "";
                reply(ex, 200, "[{\"id\":41,\"text\":\"secret-a\"" + extra + "},{\"id\":42,\"text\":\"secret-b\"}]");
            } else if (path.equals("/api/notes/41")) {
                reply(ex, 200, "{\"id\":41,\"text\":\"secret-a\",\"created\":\"2026-01-02T03:04:05Z\"}");
            } else {
                reply(ex, 404, "{\"error\":\"not found\"}");
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private NormalisedSpec spec() throws Exception {
        return SpecNormaliser.normalise(Path.of(ExploreTest.class.getResource("/specs/notes_openapi30.yaml").toURI()));
    }

    @Test
    void exploresOnlyGetsAndTakesTheIdFromTheListResponse() throws Exception {
        Explorer ex = new Explorer(spec(), ExecConfig.of(base, AuthConfig.NONE), Duration.ofMinutes(1));
        JsonNode rec = ex.explore("notes");
        assertTrue(requests.stream().allMatch(r -> r.startsWith("GET ")), requests.toString());
        assertTrue(requests.contains("GET /api/notes/41"), "identifier chosen from the list: " + requests);
        assertEquals(4, requests.size(), "two passes x (list + item)");
        assertEquals("json", rec.at("/operations/GET ~1notes~1{id}/kind").asText(), rec.toPrettyString());
        assertTrue(rec.toString().contains("string:date-time"));
        String ops = rec.path("operations").toString();
        assertFalse(ops.contains("secret-a"), "values never leave the explorer");
        assertFalse(ops.contains("41"), "identifiers never leave the explorer");
        assertEquals(64, rec.path("recordSha256").asText().length());
        String prompt = new FeatureRecord(rec).forPrompt(spec().find("PUT", "/notes/{id}"),
                List.of(spec().find("GET", "/notes/{id}"), spec().find("GET", "/notes")));
        assertTrue(prompt.startsWith("GET /notes/{id} -> 200 {created: string:date-time, id: integer, text: string}"), prompt);
        assertTrue(prompt.contains("GET /notes -> 200 [{id: integer, text: string}]"), prompt);
    }

    @Test
    void aFieldSeenInOnlyOnePassIsDroppedAndReported() throws Exception {
        secondPassAddsField = true;
        JsonNode rec = new Explorer(spec(), ExecConfig.of(base, AuthConfig.NONE), Duration.ofMinutes(1)).explore("notes");
        assertFalse(rec.at("/operations/GET ~1notes/shape").toString().contains("color"));
        assertTrue(rec.path("differences").toString().contains("color"), rec.path("differences").toString());
    }

    @Test
    void anOperationWithoutAnIdentifierIsSkipped() throws Exception {
        server.removeContext("/api/notes");
        server.createContext("/api/notes", ex -> reply(ex, 200, "[]"));
        JsonNode rec = new Explorer(spec(), ExecConfig.of(base, AuthConfig.NONE), Duration.ofMinutes(1)).explore("notes");
        assertEquals("skipped", rec.at("/operations/GET ~1notes~1{id}/kind").asText());
        assertEquals("no identifier available", rec.at("/operations/GET ~1notes~1{id}/note").asText());
    }
}
