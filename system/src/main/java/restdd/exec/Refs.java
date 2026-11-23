package restdd.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Values a test can reference: {@code ${name}} (extracted by an earlier step) and {@code ${fixture.name}}.
 * A string that is exactly one reference becomes the referenced JSON value (so numbers stay numbers);
 * otherwise references are replaced by their text.
 */
final class Refs {

    static final class MissingRef extends RuntimeException {
        MissingRef(String name) {
            super(name, null, false, false);
        }
    }

    private static final Pattern REF = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)}");
    private static final Pattern WHOLE = Pattern.compile("^\\$\\{([A-Za-z0-9_.\\-]+)}$");

    private final Map<String, String> fixtures;
    private final Map<String, JsonNode> extracted = new LinkedHashMap<>();

    Refs(Map<String, String> fixtures) {
        this.fixtures = new HashMap<>(fixtures);
    }

    void put(String name, JsonNode value) {
        extracted.put(name, value);
    }

    Map<String, JsonNode> extracted() {
        return extracted;
    }

    JsonNode lookup(String name) {
        if (name.startsWith("fixture.")) {
            String v = fixtures.get(name.substring(8));
            if (v == null) {
                throw new MissingRef(name);
            }
            return TextNode.valueOf(v);
        }
        JsonNode v = extracted.get(name);
        if (v == null) {
            throw new MissingRef(name);
        }
        return v;
    }

    /** A whole-reference string resolves to its value; any other string to its substituted text. */
    JsonNode resolve(String text) {
        Matcher w = WHOLE.matcher(text);
        if (w.matches()) {
            return lookup(w.group(1));
        }
        return TextNode.valueOf(substituteText(text));
    }

    String substituteText(String text) {
        Matcher m = REF.matcher(text);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            JsonNode v = lookup(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(v.isValueNode() ? v.asText() : v.toString()));
        }
        m.appendTail(b);
        return b.toString();
    }

    JsonNode substituteJson(JsonNode n) {
        if (n.isTextual()) {
            return resolve(n.asText());
        }
        if (n.isObject()) {
            ObjectNode o = JsonNodeFactory.instance.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                o.set(substituteText(e.getKey()), substituteJson(e.getValue()));
            }
            return o;
        }
        if (n.isArray()) {
            ArrayNode a = JsonNodeFactory.instance.arrayNode();
            n.forEach(x -> a.add(substituteJson(x)));
            return a;
        }
        return n;
    }
}
