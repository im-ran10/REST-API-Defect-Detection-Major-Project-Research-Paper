package restdd.llm;

/**
 * Model-card decoding settings per model family (D48, D52). Qwen3 4B Instruct-2507: temperature 0.7, top-p 0.8,
 * top-k 20, presence penalty 1.5 (D52). Gemma 3 (D27 fallback): its model card's temperature 1.0, top-p 0.95,
 * top-k 64 and no presence penalty (D52 is Qwen's own anti-repetition setting). Repeat penalty 1.0 for both.
 * Every value is still identical for all arms and recorded in each manifest and call.
 */
public record ModelDefaults(double temperature, double topP, int topK, double repeatPenalty, Double presencePenalty) {

    public static ModelDefaults forModel(String model) {
        if (model.toLowerCase().startsWith("gemma3")) {
            return new ModelDefaults(1.0, 0.95, 64, 1.0, null);
        }
        return new ModelDefaults(0.7, 0.8, 20, 1.0, 1.5);
    }
}
