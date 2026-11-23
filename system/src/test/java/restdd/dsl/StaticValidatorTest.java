package restdd.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import restdd.dsl.StaticValidator.AssertionClass;
import restdd.dsl.StaticValidator.SuiteReport;
import restdd.dsl.StaticValidator.TestReport;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StaticValidatorTest {

    static NormalisedSpec spec;
    static StaticValidator validator;

    @BeforeAll
    static void load() throws Exception {
        spec = SpecNormaliser.normalise(Path.of(StaticValidatorTest.class.getResource("/specs/notes_openapi30.yaml").toURI()));
        validator = new StaticValidator(spec, Set.of("image"));
    }

    private static final String GOOD = """
            {"tests": [
              {"title": "create then read back", "steps": [
                {"method": "POST", "path": "/notes", "body": {"text": "hello"},
                 "extract": {"noteId": "$.id"},
                 "assertions": [{"type": "status", "op": "in", "value": [200, 201]}]},
                {"method": "GET", "path": "/notes/{id}", "pathParams": {"id": "${noteId}"},
                 "assertions": [{"type": "status", "op": "equals", "value": 200},
                                {"type": "body", "path": "$.text", "op": "equals", "value": "hello"}]}]},
              {"title": "limit must be positive", "steps": [
                {"method": "GET", "path": "/notes", "query": {"limit": "abc"},
                 "assertions": [{"type": "status", "op": "equals", "value": 400}]}]}
            ]}""";

    @Test
    void validSuite() {
        SuiteReport r = validator.validate(GOOD, 2);
        assertTrue(r.parsed());
        assertTrue(r.schemaValid(), r.schemaErrors().toString());
        TestReport t0 = r.tests().get(0);
        assertTrue(t0.structurallyValid(), t0.structuralErrors().toString());
        assertTrue(t0.specConformant(), t0.conformanceIssues().toString());
        assertEquals(List.of("POST /notes", "GET /notes/{id}"), t0.operations());
        assertEquals(AssertionClass.MEANINGFUL, t0.assertionClass());

        TestReport t1 = r.tests().get(1);
        assertTrue(t1.structurallyValid(), "a negative test is structurally valid");
        assertFalse(t1.specConformant(), "but it is reported as not conforming");
        assertEquals(AssertionClass.STATUS_ONLY, t1.assertionClass());
        assertTrue(t1.expectsClientError());
    }

    @Test
    void wrongTestCountIsSchemaInvalid() {
        assertFalse(validator.validate(GOOD, 3).schemaValid());
        assertFalse(validator.validate("not json", 1).parsed());
    }

    @Test
    void structuralErrors() {
        String bad = """
                {"tests": [{"title": "x", "steps": [
                  {"method": "GET", "path": "/notes/{id}", "assertions": []},
                  {"method": "GET", "path": "/missing", "assertions": []},
                  {"method": "GET", "path": "/notes/{id}", "pathParams": {"id": "${nope}"},
                   "assertions": [{"type": "body", "path": "items..x", "op": "exists"},
                                  {"type": "compare", "left": "${fixture.image}", "op": "equals", "right": ""}]}]}]}""";
        TestReport t = validator.validate(bad, 1).tests().get(0);
        String errors = String.join("\n", t.structuralErrors());
        assertTrue(errors.contains("path parameter 'id' not supplied"), errors);
        assertTrue(errors.contains("no operation GET /missing"), errors);
        assertTrue(errors.contains("${nope}"), errors);
        assertTrue(errors.contains("invalid path"), errors);
        assertTrue(errors.contains("compare assertion needs left and right"), errors);
    }

    @Test
    void concretePathMatchesTemplateAndTrivialAssertionsDoNotCount() {
        String s = """
                {"tests": [{"title": "x", "steps": [
                  {"method": "DELETE", "path": "/notes/7",
                   "assertions": [{"type": "body", "path": "$", "op": "exists"},
                                  {"type": "compare", "left": "${fixture.image}", "op": "equals", "right": "${fixture.image}"}]}]}]}""";
        TestReport t = validator.validate(s, 1).tests().get(0);
        assertTrue(t.structurallyValid(), t.structuralErrors().toString());
        assertEquals(List.of("DELETE /notes/{id}"), t.operations());
        assertEquals(AssertionClass.NONE, t.assertionClass());
        assertEquals(2, t.trivialAssertions());
    }

    @Test
    void bodySchemaConformance() {
        String s = """
                {"tests": [{"title": "x", "steps": [
                  {"method": "POST", "path": "/notes", "body": {"id": "seven"},
                   "assertions": [{"type": "responseSchema", "op": "conformsToSpec"}]}]}]}""";
        TestReport t = validator.validate(s, 1).tests().get(0);
        String issues = String.join("\n", t.conformanceIssues());
        assertTrue(issues.contains("body.text is required"), issues);
        assertTrue(issues.contains("body.id should be integer"), issues);
        assertEquals(AssertionClass.MEANINGFUL, t.assertionClass());
    }

    @Test
    void bodySchemaFallsBackToWildcardMediaType() throws Exception {
        var m = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("object", StaticValidator.bodySchema(m.readTree("{\"*/*\":{\"schema\":{\"type\":\"object\"}}}")).path("type").asText());
        assertEquals("array", StaticValidator.bodySchema(m.readTree("{\"application/vnd.x+json\":{\"schema\":{\"type\":\"array\"}}}")).path("type").asText());
        assertTrue(StaticValidator.bodySchema(m.readTree("{\"text/plain\":{}}")).isMissingNode());
    }

    @Test
    void pathLanguage() {
        assertNotNull(JsonPathLite.parse("$"));
        assertNotNull(JsonPathLite.parse("$.a[0].b"));
        assertTrue(JsonPathLite.parse("$.items[*].name").wildcard());
        assertNull(JsonPathLite.parse("a.b"));
        assertNull(JsonPathLite.parse("$.a..b"));
        JsonNode doc = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                java.util.Map.of("items", List.of(java.util.Map.of("name", "x"), java.util.Map.of("name", "y"))));
        assertEquals(2, JsonPathLite.evaluate(doc, JsonPathLite.parse("$.items[*].name")).size());
    }

    @Test
    void schemaHasExactlySTests() {
        JsonNode schema = SuiteSchema.build(3);
        assertEquals(3, schema.at("/properties/tests/minItems").asInt());
        assertEquals(3, schema.at("/properties/tests/maxItems").asInt());
        assertEquals("tests", schema.at("/required/0").asText());
    }
}
