package restdd.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import restdd.exec.TargetConfig;
import restdd.gen.OperationSelector;
import restdd.select.Catalogue;
import restdd.select.FeatureExtractor;
import restdd.select.OperationFeatures;
import restdd.select.Selector;
import restdd.spec.SpecNormaliser;
import restdd.spec.SpecNormaliser.NormalisedSpec;
import restdd.spec.SpecNormaliser.OperationSlice;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * select-report --project P --spec FILE [--shapes RECORD] [--target FILE] [--cap N] [--s 3] [--offsets 0,1,2,3,4] [--show]
 *
 * <p>T8/T9 researcher view of the shared selector, without any model call: firing counts per entry, slot
 * assignments over the seed offsets, and catalogue divergence Div_C / Div_B against the frozen overlap table
 * (D31). Aggregates only; {@code --show} (per-operation slots) is refused for anything but a development project.
 */
final class SelectReport {

    static final Set<String> DEVELOPMENT = Set.of("restcountries", "dolibarr");

    private SelectReport() {}

    static void run(Map<String, String> a) throws Exception {
        String project = Main.need(a, "project");
        boolean show = a.containsKey("show");
        if (show && !DEVELOPMENT.contains(project)) {
            throw new IllegalArgumentException("--show is for development projects only (firewall); held-out: counts only");
        }
        NormalisedSpec full = SpecNormaliser.normalise(Path.of(Main.need(a, "spec")));
        int cap = Integer.parseInt(a.getOrDefault("cap", "0"));
        List<OperationSlice> ops = OperationSelector.select(full.operations(), cap);
        boolean secondRole = false;
        if (a.containsKey("target")) {
            secondRole = TargetConfig.load(Path.of(a.get("target")), n -> "unused").exec().secondaryAuth() != null;
        }
        restdd.explore.FeatureRecord record = a.containsKey("shapes") ? restdd.explore.FeatureRecord.load(Path.of(a.get("shapes"))) : null;
        Map<String, OperationFeatures> features = FeatureExtractor.extract(full, record, secondRole);
        List<OperationFeatures> sel = ops.stream().map(o -> features.get(o.key())).toList();
        int s = Integer.parseInt(a.getOrDefault("s", "3"));
        List<Long> offsets = java.util.Arrays.stream(a.getOrDefault("offsets", "0,1,2,3,4").split(","))
                .map(x -> Long.parseLong(x.trim())).toList();
        System.out.printf("%s: %d operations (cap %s, list SHA-256 %s), second role %s, feature record %s%n", project,
                ops.size(), cap <= 0 ? "none" : cap, OperationSelector.listSha256(ops), secondRole,
                record == null ? "none" : record.sha256().substring(0, 12) + "…");

        JsonNode overlap;
        try (InputStream in = SelectReport.class.getResourceAsStream("/catalogues/overlap.json")) {
            overlap = new ObjectMapper().readTree(in);
        }
        for (String id : List.of("B", "C")) {
            Catalogue c = Catalogue.load(id);
            System.out.printf("%ncatalogue %s %s (SHA-256 %s…)%n", id, c.version(), c.sha256().substring(0, 12));
            System.out.println("  operations on which each entry fires: " + Selector.firingCounts(c, sel));
            Map<String, Integer> assigned = new LinkedHashMap<>();
            c.entries().forEach(e -> assigned.put(e.id(), 0));
            int total = 0;
            int distinct = 0;
            for (OperationFeatures f : sel) {
                for (long seed : offsets) {
                    for (Selector.Slot sl : Selector.assign(c, f, seed, s, false)) {
                        assigned.merge(sl.entryId(), 1, Integer::sum);
                        total++;
                        distinct += overlap.path(id).path(sl.entryId()).isEmpty() ? 1 : 0;
                    }
                }
            }
            System.out.println("  slot assignments over offsets " + offsets + ": " + assigned);
            System.out.printf("  Div_%s = %d / %d = %.1f%% (assignments to entries with no counterpart in the other catalogue)%n",
                    id, distinct, total, total == 0 ? 0.0 : 100.0 * distinct / total);
            if (show) {
                for (OperationFeatures f : sel) {
                    List<String> slots = new ArrayList<>();
                    Selector.assign(c, f, offsets.get(0), s, false).forEach(x -> slots.add(x.entryId()));
                    System.out.printf("  %-55s fired %s -> offset %d slots %s%n", f.key(), Selector.fired(c, f, false),
                            offsets.get(0), slots);
                }
            }
        }
    }
}
