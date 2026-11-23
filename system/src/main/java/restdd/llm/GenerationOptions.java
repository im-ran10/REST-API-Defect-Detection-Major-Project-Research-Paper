package restdd.llm;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Decoding settings, identical for every arm (D12, D27). Frozen at G4 and recorded in each manifest.
 *
 * @param think null = do not send the field (for non-thinking models); otherwise sent as given
 */
public record GenerationOptions(String model, int numCtx, int numPredict, double temperature, double topP,
                                int topK, double repeatPenalty, Boolean think, String keepAlive) {

    /** Ollama {@code options} object for one call with the given seed. */
    public ObjectNode toOllamaOptions(long seed) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("num_ctx", numCtx);
        o.put("num_predict", numPredict);
        o.put("temperature", temperature);
        o.put("top_p", topP);
        o.put("top_k", topK);
        o.put("repeat_penalty", repeatPenalty);
        o.put("seed", seed);
        return o;
    }
}
