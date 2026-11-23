package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.dsl.StaticValidator;
import restdd.exec.ResetAdapter;
import restdd.exec.SuiteRunner;
import restdd.exec.TargetConfig;
import restdd.exec.TestExecutor;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code exec}: runs one suite file against one deployment and writes the full record (T5). The spec is used
 * uncompacted here: compaction only shapes prompts, while response checks need the full schemas.
 */
final class Exec {

    private Exec() {}

    static void run(Map<String, String> a) throws Exception {
        TargetConfig target = TargetConfig.load(Path.of(Main.need(a, "target")), System::getenv);
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(Main.need(a, "spec")));
        String suiteText = Files.readString(Path.of(Main.need(a, "suite")));
        JsonNode parsed = new ObjectMapper().readTree(suiteText);
        int s = Integer.parseInt(a.getOrDefault("s", String.valueOf(parsed.path("tests").size())));

        Set<String> fixtureNames = new HashSet<>(target.exec().fixtures().keySet());
        fixtureNames.add("uid");
        StaticValidator validator = new StaticValidator(spec, fixtureNames);
        TestExecutor executor = new TestExecutor(target.exec(), validator);
        ResetAdapter reset = target.reset();

        if (a.containsKey("snapshot")) {
            ObjectNode snap = new ObjectMapper().createObjectNode();
            reset.snapshot(snap);
            System.out.println("snapshot taken (" + reset.name() + ")");
        }
        List<Integer> only = a.containsKey("only")
                ? Arrays.stream(a.get("only").split(",")).map(x -> Integer.parseInt(x.trim()) - 1).toList() : null;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("label", a.getOrDefault("label", "manual"));
        meta.put("suiteFile", Path.of(a.get("suite")).getFileName().toString());
        meta.put("specSha256", spec.sourceSha256());
        ObjectNode rec = new SuiteRunner(validator, executor, reset).run(suiteText, s, meta, only);

        System.out.println("schema-valid " + rec.at("/validation/schemaValid").asBoolean() + " | reset " + reset.name()
                + " | " + rec.path("durationMs").asLong() + " ms");
        for (JsonNode t : rec.path("tests")) {
            System.out.printf("test %d  %-11s %s%n", t.path("index").asInt() + 1, t.path("outcome").asText(),
                    t.path("reason").asText());
        }
        System.out.println("summary " + rec.path("summary"));
        Path out = Path.of(a.getOrDefault("out", "runs/exec-" + System.currentTimeMillis() + ".json"));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, rec.toPrettyString());
        System.out.println("record: " + out.toAbsolutePath());
    }
}
