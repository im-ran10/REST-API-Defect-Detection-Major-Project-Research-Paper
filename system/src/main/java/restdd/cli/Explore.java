package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.exec.TargetConfig;
import restdd.explore.Explorer;
import restdd.explore.FeatureRecord;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * {@code explore}: T7 GET-only, shape-only exploration of a project's reference deployment (§6, D13.1).
 *
 * <pre>explore --project P --spec FILE --target FILE --out FEATURE-RECORD.json [--minutes 15] [--show]</pre>
 * Prints counts only; {@code --show} also prints the rendered shapes (development projects).
 */
final class Explore {

    private Explore() {}

    static void run(Map<String, String> a) throws Exception {
        String project = Main.need(a, "project");
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(Main.need(a, "spec")));
        TargetConfig target = TargetConfig.load(Path.of(Main.need(a, "target")), System::getenv);
        Duration budget = Duration.ofMinutes(Long.parseLong(a.getOrDefault("minutes", "15")));
        Explorer explorer = new Explorer(spec, target.exec(), budget);
        System.out.printf("%s: %d GET operations, 2 passes, budget %d min%n", project, explorer.getOperations().size(),
                budget.toMinutes());
        ObjectNode rec = explorer.explore(project);
        Path out = Path.of(Main.need(a, "out"));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, rec.toPrettyString());
        System.out.println("summary " + rec.path("summary"));
        System.out.printf("elapsed %.1f min | differences between passes %d%n", rec.path("elapsedMs").asLong() / 60000.0,
                rec.path("differences").size());
        if (a.containsKey("show")) {
            FeatureRecord fr = new FeatureRecord(rec);
            for (JsonNode d : rec.path("differences")) {
                System.out.println("difference: " + d.asText());
            }
            rec.path("operations").fieldNames().forEachRemaining(k -> {
                String line = fr.forPrompt(spec.find(k.substring(0, k.indexOf(' ')), k.substring(k.indexOf(' ') + 1)), java.util.List.of());
                System.out.println(line.isEmpty() ? k + " -> " + rec.path("operations").path(k).path("kind").asText()
                        + " " + rec.path("operations").path(k).path("note").asText("") : line);
            });
        }
        System.out.println("feature record: " + out.toAbsolutePath() + " (SHA-256 " + rec.path("recordSha256").asText() + ")");
    }
}
