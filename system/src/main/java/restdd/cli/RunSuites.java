package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import restdd.dsl.StaticValidator;
import restdd.exec.SuiteRunner;
import restdd.exec.TargetConfig;
import restdd.exec.TestExecutor;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code run-suites}: executes every schema-valid suite of a generation run against one deployment (reset before
 * each suite) and appends the records to {@code exec-<label>.jsonl} in the work folder. Re-running resumes:
 * suites already recorded under that label are skipped.
 *
 * <pre>run-suites --work DIR --target FILE --spec FILE [--label modeltest] [--snapshot]</pre>
 */
final class RunSuites {

    private RunSuites() {}

    static void run(Map<String, String> a) throws Exception {
        Path work = Path.of(Main.need(a, "work"));
        String label = a.getOrDefault("label", "modeltest");
        TargetConfig target = TargetConfig.load(Path.of(Main.need(a, "target")), System::getenv);
        NormalisedSpec spec = SpecNormaliser.normalise(Path.of(Main.need(a, "spec")));
        Set<String> fixtures = new HashSet<>(target.exec().fixtures().keySet());
        fixtures.add("uid");
        StaticValidator validator = new StaticValidator(spec, fixtures);
        SuiteRunner runner = new SuiteRunner(validator, new TestExecutor(target.exec(), validator), target.reset());
        ObjectMapper json = new ObjectMapper();

        if (a.containsKey("snapshot")) {
            target.reset().snapshot(json.createObjectNode());
            System.out.println("snapshot taken (" + target.reset().name() + ")");
        }

        Path out = work.resolve("exec-" + label + ".jsonl");
        Set<String> done = new HashSet<>();
        if (Files.exists(out)) {
            for (String line : Files.readAllLines(out)) {
                if (!line.isBlank()) {
                    done.add(json.readTree(line).path("callId").asText());
                }
            }
        }
        List<String> calls = Files.readAllLines(work.resolve("calls.jsonl"));
        int total = 0;
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            for (String line : calls) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode call = json.readTree(line);
                if (!call.at("/validation/schemaValid").asBoolean()) {
                    continue;
                }
                total++;
                String id = call.path("callId").asText();
                if (done.contains(id)) {
                    continue;
                }
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("callId", id);
                meta.put("label", label);
                ObjectNode rec = runner.run(call.path("response").asText(), call.path("s").asInt(), meta, null);
                ObjectNode line2 = json.createObjectNode();
                line2.put("callId", id);
                line2.set("record", rec);
                w.write(line2.toString());
                w.newLine();
                w.flush();
                StringBuilder sb = new StringBuilder();
                for (JsonNode t : rec.path("tests")) {
                    String o = t.path("outcome").asText();
                    outcomes.merge(o, 1, Integer::sum);
                    sb.append(o, 0, Math.min(4, o.length())).append(' ');
                }
                System.out.printf("[%d] %-60s %s%n", total, id.length() > 60 ? id.substring(0, 57) + "..." : id, sb);
            }
        }
        System.out.println("suites run now: " + outcomes.values().stream().mapToInt(Integer::intValue).sum() + " tests " + outcomes);
        System.out.println("records: " + out.toAbsolutePath());
    }
}
