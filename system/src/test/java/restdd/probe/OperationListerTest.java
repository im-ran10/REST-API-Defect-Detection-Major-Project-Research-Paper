package restdd.probe;

import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The same five operations are written as Swagger 2.0, OpenAPI 3.0 and OpenAPI 3.1 (hand-written
 * fixtures, no benchmark data). Every format must yield the identical operation list (D8; 3.1 for
 * ARCHITECTURE open question 7).
 */
class OperationListerTest {

    private static final List<String> EXPECTED = List.of(
            "GET /notes listNotes",
            "POST /notes createNote",
            "DELETE /notes/{id} deleteNote",
            "GET /notes/{id} getNote",
            "PUT /notes/{id} null");

    private static Path fixture(String name) throws URISyntaxException {
        URI uri = OperationListerTest.class.getResource("/specs/" + name).toURI();
        return Path.of(uri);
    }

    private static List<String> render(List<OperationLister.Op> ops) {
        return ops.stream().map(o -> o.method() + " " + o.path() + " " + o.operationId()).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes_swagger20.json", "notes_openapi30.yaml", "notes_openapi31.yaml"})
    void allFormatsGiveTheSameOperations(String file) throws Exception {
        SpecLoader.LoadedSpec spec = SpecLoader.load(fixture(file));
        System.out.println(file + ": declared=" + spec.declaredVersion()
                + " parsedAs=" + spec.openApi().getOpenapi() + " messages=" + spec.messages());
        assertEquals(EXPECTED, render(OperationLister.list(spec.openApi())));
    }

    @Test
    void swagger20IsConvertedTo30() throws Exception {
        SpecLoader.LoadedSpec spec = SpecLoader.load(fixture("notes_swagger20.json"));
        assertEquals("Swagger 2.0", spec.declaredVersion());
        assertEquals(SpecVersion.V30, spec.openApi().getSpecVersion());
        assertTrue(spec.messages().isEmpty(), "unexpected messages: " + spec.messages());
    }

    @Test
    void openApi30ParsesWithoutMessages() throws Exception {
        SpecLoader.LoadedSpec spec = SpecLoader.load(fixture("notes_openapi30.yaml"));
        assertEquals("OpenAPI 3.0.3", spec.declaredVersion());
        assertEquals(SpecVersion.V30, spec.openApi().getSpecVersion());
        assertTrue(spec.messages().isEmpty(), "unexpected messages: " + spec.messages());
    }

    /** 3.1-specific constructs must survive parsing, not just the operation list. */
    @Test
    void openApi31ParsesCleanlyAndKeeps31Features() throws Exception {
        SpecLoader.LoadedSpec spec = SpecLoader.load(fixture("notes_openapi31.yaml"));
        assertEquals("OpenAPI 3.1.0", spec.declaredVersion());
        assertEquals(SpecVersion.V31, spec.openApi().getSpecVersion());
        assertTrue(spec.messages().isEmpty(), "unexpected messages: " + spec.messages());

        assertEquals("Same operations as the 2.0 and 3.0 fixtures", spec.openApi().getInfo().getSummary());
        assertEquals("Apache-2.0", spec.openApi().getInfo().getLicense().getIdentifier());
        assertNotNull(spec.openApi().getWebhooks(), "webhooks dropped");
        assertTrue(spec.openApi().getWebhooks().containsKey("noteCreated"));

        Schema<?> note = spec.openApi().getComponents().getSchemas().get("Note");
        Schema<?> text = (Schema<?>) note.getProperties().get("text");
        assertEquals(Set.of("string", "null"), text.getTypes(), "type array lost");
        Schema<?> kind = (Schema<?>) note.getProperties().get("kind");
        assertEquals("note", kind.getConst());

        var limit = spec.openApi().getPaths().get("/notes").getGet().getParameters().get(0).getSchema();
        assertEquals(0, limit.getExclusiveMinimumValue().intValue(), "numeric exclusiveMinimum lost");
    }

    @Test
    void buildUriJoinsSlashesOnce() {
        assertEquals("http://h/api/index.php/status",
                HttpProbe.buildUri("http://h/api/index.php/", "/status").toString());
        assertEquals("http://h/api/index.php/status",
                HttpProbe.buildUri("http://h/api/index.php", "/status").toString());
        assertThrows(IllegalArgumentException.class, () -> HttpProbe.buildUri("http://h", "status"));
    }
}
