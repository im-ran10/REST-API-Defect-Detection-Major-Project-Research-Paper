package restdd.probe;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a Swagger 2.0 or OpenAPI 3.x spec with swagger-parser (D8).
 * Swagger 2.0 input is converted to OpenAPI 3.0 by the parser's v2 converter.
 *
 * <p>Phase 1 probe only: this is not the D5.1 allow-listed input loader and writes no manifest.
 */
public final class SpecLoader {

    /** Result of loading a spec: the parsed model, the declared source version and parser messages. */
    public record LoadedSpec(OpenAPI openApi, String declaredVersion, List<String> messages) {}

    private SpecLoader() {}

    public static LoadedSpec load(Path specFile) throws IOException {
        if (!Files.isRegularFile(specFile)) {
            throw new IOException("Spec file not found: " + specFile);
        }
        String declared = declaredVersion(specFile);

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        // resolveFully is needed for OpenAPI 3.1 path items given as $ref to components/pathItems:
        // with resolve=true only, swagger-parser 2.1.48 leaves them unresolved, without a message,
        // and their operations disappear (measured 22 Nov 2026, RESEARCH_LOG E35).
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser()
                .readLocation(specFile.toAbsolutePath().toString(), null, options);

        List<String> messages = new ArrayList<>();
        if (result.getMessages() != null) {
            messages.addAll(result.getMessages());
        }
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new IOException("swagger-parser could not parse " + specFile + ": " + messages);
        }
        // Guard: never lose operations silently. Report any path item still holding a $ref.
        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, item) -> {
                if (item.get$ref() != null) {
                    messages.add("probe: unresolved path-item $ref at " + path + " -> " + item.get$ref()
                            + " (its operations are not listed)");
                }
            });
        }
        return new LoadedSpec(openApi, declared, List.copyOf(messages));
    }

    /** Reads the top-level "swagger" or "openapi" field as written in the file (before any conversion). */
    static String declaredVersion(Path specFile) throws IOException {
        // The YAML mapper also reads JSON.
        JsonNode root = Yaml.mapper().readTree(Files.readString(specFile));
        if (root == null) {
            return "unknown";
        }
        if (root.hasNonNull("swagger")) {
            return "Swagger " + root.get("swagger").asText();
        }
        if (root.hasNonNull("openapi")) {
            return "OpenAPI " + root.get("openapi").asText();
        }
        return "unknown";
    }
}
