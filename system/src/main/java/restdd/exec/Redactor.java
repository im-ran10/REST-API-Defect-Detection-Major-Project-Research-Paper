package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Replaces every occurrence of a secret in a record with {@code <redacted>} before it is stored or printed. */
final class Redactor {

    static final String MARK = "<redacted>";

    private Redactor() {}

    static String redact(String text, List<String> secrets) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (String s : secrets) {
            if (s != null && s.length() >= 4) {
                out = out.replace(s, MARK);
            }
        }
        return out;
    }

    static void redact(JsonNode node, List<String> secrets) {
        if (secrets.isEmpty()) {
            return;
        }
        if (node instanceof ObjectNode o) {
            List<String> names = new ArrayList<>();
            o.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode v = o.get(name);
                String safeName = redact(name, secrets);
                if (v.isTextual()) {
                    v = o.textNode(redact(v.asText(), secrets));
                } else {
                    redact(v, secrets);
                }
                if (!safeName.equals(name)) {
                    o.remove(name);
                }
                o.set(safeName, v);
            }
        } else if (node instanceof ArrayNode a) {
            for (int i = 0; i < a.size(); i++) {
                JsonNode v = a.get(i);
                if (v.isTextual()) {
                    a.set(i, a.textNode(redact(v.asText(), secrets)));
                } else {
                    redact(v, secrets);
                }
            }
        }
    }
}
