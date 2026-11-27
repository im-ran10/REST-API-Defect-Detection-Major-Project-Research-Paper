package restdd.select;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** A strategy catalogue (B or C) loaded from {@code /catalogues/<id>.json}; entries keep their file order (T8). */
public record Catalogue(String id, String version, String defaultId, List<Entry> entries, String sha256) {

    public record Entry(String id, String name, String trigger, String applicability, String probe, String oracle,
                        String example, String source, String adherenceProbe, String adherenceOracle) {}

    public static Catalogue load(String id) throws IOException {
        try (InputStream in = Catalogue.class.getResourceAsStream("/catalogues/" + id + ".json")) {
            if (in == null) {
                throw new IOException("catalogue " + id + " missing from the classpath");
            }
            byte[] bytes = in.readAllBytes();
            JsonNode n = new ObjectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
            List<Entry> entries = new ArrayList<>();
            for (JsonNode e : n.path("entries")) {
                entries.add(new Entry(e.path("id").asText(), e.path("name").asText(), e.path("trigger").asText(),
                        e.path("applicability").asText(), e.path("probe").asText(), e.path("oracle").asText(),
                        e.path("example").asText(), e.path("source").asText(),
                        e.at("/adherence/probe").asText(), e.at("/adherence/oracle").asText()));
                if (!Predicates.ALL.containsKey(e.path("trigger").asText())) {
                    throw new IOException(id + ": entry " + e.path("id").asText() + " has unknown trigger " + e.path("trigger").asText());
                }
            }
            try {
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new Catalogue(n.path("catalogue").asText(), n.path("version").asText(), n.path("default").asText(),
                        List.copyOf(entries), sha);
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public Entry entry(String entryId) {
        return entries.stream().filter(e -> e.id().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(id + " has no entry " + entryId));
    }
}
