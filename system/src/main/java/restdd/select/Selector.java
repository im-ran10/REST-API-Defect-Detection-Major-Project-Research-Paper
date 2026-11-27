package restdd.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared selector for arms B and C (EXPERIMENT_PROTOCOL §3; final methodology §5.3): per operation, the entries
 * whose predicate fires, in catalogue order, fill the {@code s} slots round-robin from index (seed mod count);
 * if none fires, every slot gets the catalogue's default entry. No priors. Every choice carries its reason.
 */
public final class Selector {

    public record Slot(int index, String entryId, String reason) {}

    private Selector() {}

    public static List<String> fired(Catalogue c, OperationFeatures f, boolean ignorePredicates) {
        List<String> out = new ArrayList<>();
        for (Catalogue.Entry e : c.entries()) {
            if (ignorePredicates ? !e.trigger().equals("default") : Predicates.fires(e.trigger(), f)) {
                out.add(e.id());
            }
        }
        return out;
    }

    /**
     * @param ignorePredicates development-only C∅ ablation (D37): every non-default entry counts as fired
     */
    public static List<Slot> assign(Catalogue c, OperationFeatures f, long seed, int s, boolean ignorePredicates) {
        List<String> fired = fired(c, f, ignorePredicates);
        List<Slot> slots = new ArrayList<>();
        if (fired.isEmpty()) {
            for (int i = 0; i < s; i++) {
                slots.add(new Slot(i, c.defaultId(), "default: no predicate fired"));
            }
            return slots;
        }
        int start = (int) Math.floorMod(seed, (long) fired.size());
        for (int i = 0; i < s; i++) {
            String id = fired.get((start + i) % fired.size());
            String why = ignorePredicates ? "C∅ ablation: predicates ignored"
                    : "predicate " + c.entry(id).trigger() + " fired; fired " + fired + ", start " + start;
            slots.add(new Slot(i, id, why));
        }
        return slots;
    }

    /** Shared slot presentation, identical for B and C (sources are kept out of the prompt). */
    public static String task(Catalogue c, List<Slot> slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write ").append(slots.size()).append(" test case").append(slots.size() == 1 ? "" : "s")
                .append(", one per slot, in slot order. Each test applies its slot's strategy: carry out the probe")
                .append(" and check the oracle with at least one assertion.\n");
        for (Slot sl : slots) {
            Catalogue.Entry e = c.entry(sl.entryId());
            sb.append("\nSLOT ").append(sl.index() + 1).append(" - ").append(e.name()).append('\n')
                    .append("Applies when: ").append(e.applicability()).append('\n')
                    .append("Probe: ").append(e.probe()).append('\n')
                    .append("Oracle: ").append(e.oracle()).append('\n')
                    .append("Example (fictional API): ").append(e.example()).append('\n');
        }
        return sb.toString();
    }

    /** Firing counts per entry over a set of operations (aggregate only; safe for held-out projects). */
    public static Map<String, Integer> firingCounts(Catalogue c, Iterable<OperationFeatures> ops) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        c.entries().forEach(e -> counts.put(e.id(), 0));
        int none = 0;
        for (OperationFeatures f : ops) {
            List<String> fired = fired(c, f, false);
            fired.forEach(id -> counts.merge(id, 1, Integer::sum));
            none += fired.isEmpty() ? 1 : 0;
        }
        counts.put("(default only)", none);
        return counts;
    }
}
