package restdd.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecNormaliserTest {

    static Path fixture(String name) throws Exception {
        return Path.of(SpecNormaliserTest.class.getResource("/specs/" + name).toURI());
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes_swagger20.json", "notes_openapi30.yaml", "notes_openapi31.yaml"})
    void sameOperationsInEveryFormatAndNoRefsLeft(String file) throws Exception {
        NormalisedSpec spec = SpecNormaliser.normalise(fixture(file));
        assertEquals(List.of("GET /notes", "POST /notes", "DELETE /notes/{id}", "GET /notes/{id}", "PUT /notes/{id}"),
                spec.operations().stream().map(OperationSlice::key).toList());
        for (OperationSlice s : spec.operations()) {
            assertFalse(s.text().contains("$ref"), s.key() + " still holds a $ref: " + s.text());
            assertTrue(s.estimatedTokens() > 0);
        }
        OperationSlice post = spec.find("POST", "/notes");
        assertEquals("text", post.slice().at("/requestBody/content/application~1json/schema/required/0").asText(),
                "request body schema inlined: " + post.text());
        OperationSlice get = spec.find("GET", "/notes/{id}");
        assertEquals("id", get.slice().at("/parameters/0/name").asText(), "path-level parameter merged");
        assertEquals(64, spec.sourceSha256().length());
        assertEquals(0, spec.unresolvedRefs());
    }

    @Test
    void defaultValueMappedAndRecursionCut() throws Exception {
        NormalisedSpec spec = SpecNormaliser.normalise(fixture("tree_swagger20_defaultvalue.json"));
        assertEquals(1, spec.defaultValueMapped(), "only the parameter without 'default' is mapped");
        OperationSlice get = spec.find("GET", "/nodes");
        assertEquals(100, get.slice().at("/parameters/0/schema/default").asInt(), get.text());
        assertEquals("ASC", get.slice().at("/parameters/1/schema/default").asText(), "existing default kept");
        assertTrue(spec.recursiveCuts() >= 1);
        assertTrue(get.text().contains("\"$recursive\":\"Node\""), get.text());
    }

    @Test
    void compactionCapsDescriptionsAndDepth() throws Exception {
        NormalisedSpec full = SpecNormaliser.normalise(fixture("tree_swagger20_defaultvalue.json"));
        NormalisedSpec compact = SpecNormaliser.normalise(fixture("tree_swagger20_defaultvalue.json"),
                new SpecNormaliser.Compaction(5, true, 1));
        String text = compact.find("GET", "/nodes").text();
        assertTrue(text.contains("$truncated") || text.length() <= full.find("GET", "/nodes").text().length(), text);
    }

    @Test
    void sanitiserReportsLeakageWords() throws Exception {
        Path tmp = java.nio.file.Files.createTempFile("spec", ".yaml");
        java.nio.file.Files.writeString(tmp, """
                openapi: 3.0.3
                info: {title: t, version: '1', description: 'Workaround for bug #123, see https://github.com/x/y/issues/1'}
                paths: {}
                """);
        List<SpecSanitiser.Hit> hits = SpecSanitiser.scan(tmp);
        List<String> terms = hits.stream().map(h -> h.term().toLowerCase()).toList();
        assertTrue(terms.contains("workaround"));
        assertTrue(terms.contains("bug"));
        assertTrue(terms.contains("#123"));
        assertTrue(terms.stream().anyMatch(t -> t.startsWith("https://github.com")));
        assertEquals("/info/description", hits.get(0).pointer());
    }
}
