package restdd.gen;

import restdd.spec.SpecNormaliser.OperationSlice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * D47 operation cap: deterministic, defect-blind selection of {@code cap} operations from a project spec.
 *
 * <ol>
 *   <li>Section = first path segment (e.g. {@code /products/{id}} → {@code products}).</li>
 *   <li>Each section gets a share of the cap proportional to its size (largest-remainder rounding; ties broken
 *       by section name).</li>
 *   <li>Within a section, operations are taken in the order of SHA-256 of their key ({@code "METHOD /path"}).</li>
 * </ol>
 * A cap of 0 or at least the number of operations returns all operations. The result is sorted by key; its
 * key list and SHA-256 are recorded and frozen (D47).
 */
public final class OperationSelector {

    private OperationSelector() {}

    public static List<OperationSlice> select(List<OperationSlice> ops, int cap) {
        List<OperationSlice> all = new ArrayList<>(ops);
        all.sort(Comparator.comparing(OperationSlice::key));
        if (cap <= 0 || cap >= all.size()) {
            return all;
        }
        Map<String, List<OperationSlice>> sections = new TreeMap<>();
        for (OperationSlice o : all) {
            sections.computeIfAbsent(section(o.path()), k -> new ArrayList<>()).add(o);
        }
        Map<String, Integer> quota = quotas(sections, all.size(), cap);
        List<OperationSlice> out = new ArrayList<>();
        for (Map.Entry<String, List<OperationSlice>> e : sections.entrySet()) {
            List<OperationSlice> members = new ArrayList<>(e.getValue());
            members.sort(Comparator.comparing(o -> sha256(o.key())));
            out.addAll(members.subList(0, quota.get(e.getKey())));
        }
        out.sort(Comparator.comparing(OperationSlice::key));
        return out;
    }

    static Map<String, Integer> quotas(Map<String, List<OperationSlice>> sections, int total, int cap) {
        Map<String, Integer> q = new LinkedHashMap<>();
        List<Map.Entry<String, Double>> remainders = new ArrayList<>();
        int assigned = 0;
        for (Map.Entry<String, List<OperationSlice>> e : sections.entrySet()) {
            double exact = (double) cap * e.getValue().size() / total;
            int floor = (int) Math.floor(exact);
            q.put(e.getKey(), floor);
            assigned += floor;
            remainders.add(Map.entry(e.getKey(), exact - floor));
        }
        remainders.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        for (int i = 0; assigned < cap; i++) {
            String k = remainders.get(i % remainders.size()).getKey();
            if (q.get(k) < sections.get(k).size()) {
                q.put(k, q.get(k) + 1);
                assigned++;
            }
        }
        return q;
    }

    static String section(String path) {
        for (String seg : path.split("/")) {
            if (!seg.isEmpty()) {
                return seg;
            }
        }
        return "";
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** SHA-256 of the newline-joined, sorted operation keys: the frozen identity of a selection. */
    public static String listSha256(List<OperationSlice> selected) {
        return sha256(String.join("\n", selected.stream().map(OperationSlice::key).sorted().toList()));
    }
}
