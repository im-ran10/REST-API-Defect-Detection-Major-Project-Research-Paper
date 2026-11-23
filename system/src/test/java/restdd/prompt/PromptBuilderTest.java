package restdd.prompt;

import org.junit.jupiter.api.Test;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    @Test
    void staticPrefix() {
        assertEquals("/libpod/containers", PromptBuilder.staticPrefix("/libpod/containers/{name}/start"));
        assertEquals("/containers", PromptBuilder.staticPrefix("/containers/{id}/json"));
        assertEquals("/notes", PromptBuilder.staticPrefix("/notes/{id}"));
        assertEquals("", PromptBuilder.staticPrefix("/{tenant}/x"));
    }

    @Test
    void userPromptHasContextRelatedOperationsAndTask() throws Exception {
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(getClass().getResource("/specs/notes_openapi30.yaml").toURI()));
        PromptBuilder pb = new PromptBuilder();
        assertTrue(pb.system().contains("FORMAT") && pb.system().contains("EXAMPLE"));
        String user = pb.user(spec, spec.find("GET", "/notes/{id}"), "{}", List.of("image"), PromptBuilder.armATask(3));
        assertTrue(user.startsWith("OPERATION UNDER TEST\n{\"operation\":\"GET /notes/{id}\""), user);
        assertTrue(user.contains("OTHER OPERATIONS YOU MAY CALL\nGET /notes\nPOST /notes\nDELETE /notes/{id}\nPUT /notes/{id}\n"), user);
        assertTrue(user.contains("POST /notes"), user);
        assertFalse(user.contains("\nGET /notes/{id}\n"), "the operation itself is not listed again");
        assertTrue(user.contains("${fixture.image}"));
        assertTrue(user.endsWith("TASK\nWrite 3 test cases for this operation that are likely to reveal defects, with assertions.\n"), user);
    }
}
