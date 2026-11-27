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

    /** D51: text values carry a length cap in the structured-output schema; bodies stay unconstrained. */
    @Test
    void schemaCapsTextValues() {
        var step = restdd.dsl.SuiteSchema.build(1).at("/properties/tests/items/properties/steps/items/properties");
        assertEquals(200, step.at("/query/additionalProperties/maxLength").asInt());
        assertEquals(200, step.at("/pathParams/additionalProperties/maxLength").asInt());
        assertEquals(300, step.at("/path/maxLength").asInt());
        assertTrue(step.path("body").isEmpty(), "body stays any JSON");
    }

    /** Rubric R-T4 (owner, 24 Nov 2026): the automatic class must equal the manual verdict. */
    @Test
    void t4RubricMatchesTheManualRule() {
        java.util.function.Function<String, AssertionClass> cls = assertions -> validator.validate(
                "{\"tests\": [{\"title\": \"x\", \"steps\": [{\"method\": \"GET\", \"path\": \"/notes\", \"assertions\": ["
                        + assertions + "]}]}]}", 1).tests().get(0).assertionClass();
        String status = "{\"type\": \"status\", \"op\": \"equals\", \"value\": 200}";
        // trivial
        assertEquals(AssertionClass.STATUS_ONLY, cls.apply(status));
        assertEquals(AssertionClass.STATUS_ONLY, cls.apply(status + ", {\"type\": \"body\", \"path\": \"$\", \"op\": \"exists\"},"
                + " {\"type\": \"body\", \"path\": \"$\", \"op\": \"isNotEmpty\"}"));
        assertEquals(AssertionClass.STATUS_ONLY, cls.apply(status + ", {\"type\": \"body\", \"path\": \"$\", \"op\": \"isType\", \"value\": \"array\"}"));
        assertEquals(AssertionClass.STATUS_ONLY, cls.apply(status + ", {\"type\": \"header\", \"name\": \"Content-Type\", \"op\": \"exists\"}"));
        // non-trivial
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"body\", \"path\": \"$\", \"op\": \"contains\", \"value\": \"order\"}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"body\", \"path\": \"$\", \"op\": \"lengthGte\", \"value\": 1}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"body\", \"path\": \"$.id\", \"op\": \"exists\"}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"body\", \"path\": \"$.request_data\", \"op\": \"notExists\"}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"body\", \"path\": \"$[*].text\", \"op\": \"isType\", \"value\": \"string\"}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"header\", \"name\": \"Content-Type\", \"op\": \"contains\", \"value\": \"json\"}"));
        assertEquals(AssertionClass.MEANINGFUL, cls.apply("{\"type\": \"responseSchema\", \"op\": \"conformsToSpec\"}"));
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

    @Test
    void answerSchemaRequiresPerTypeFields() {
        assertTrue(SuiteSchema.build(3).at("/properties/tests/items/properties/steps/items/properties/assertions/items/anyOf").isMissingNode(),
                "D53 is opt-in");
        com.fasterxml.jackson.databind.JsonNode any = SuiteSchema.assertionSchema().path("anyOf");
        assertEquals(7, any.size());
        boolean bodyNeedsPath = false;
        for (com.fasterxml.jackson.databind.JsonNode v : any) {
            String type = v.at("/properties/type/enum/0").asText();
            java.util.List<String> req = new java.util.ArrayList<>();
            v.path("required").forEach(r -> req.add(r.asText()));
            if (type.equals("body")) {
                bodyNeedsPath = req.contains("path");
                assertTrue(bodyNeedsPath);
                v.at("/properties/op/enum").forEach(o -> assertNotEquals("conformsToSpec", o.asText()));
            }
            if (type.equals("status")) {
                assertTrue(req.contains("value"));
            }
            if (type.equals("header")) {
                assertTrue(req.contains("name"));
            }
        }
        assertTrue(bodyNeedsPath);
    }

    @Test
    void bodyPathPatternMatchesExactlyTheParsedSubset() {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(SuiteSchema.BODY_PATH_PATTERN);
        for (String ok : java.util.List.of("$", "$.a", "$[0].b", "$[*].alpha2Code", "$.error.message", "$.x-y[12]")) {
            assertTrue(p.matcher(ok).matches(), ok);
            assertTrue(JsonPathLite.parse(ok) != null, ok);
        }
        for (String bad : java.util.List.of("alpha2Code", "message", "$.[*].name", "$..a", "$.a b", "")) {
            assertTrue(!p.matcher(bad).matches(), bad);
        }
    }
}
