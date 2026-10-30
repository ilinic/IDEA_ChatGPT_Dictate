package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;

/** Models used only for correcting the transcribed text, not for speech recognition. */
enum CorrectionModel {
    LUNA("gpt-6-luna", "GPT-6 Luna — минимальная стоимость (по умолчанию)", "none"),
    TERRA("gpt-6.1-terra", "GPT-6.1 Terra — повышенное качество", "none"),
    SOL("gpt-6.1-sol", "GPT-6.1 Sol — сложные случаи", "low"),
    ASTRA("gpt-6-astra", "GPT-6 Astra — максимальное качество", "low");

    private static final String SETTINGS_KEY = "nz.ilinic.ideadictate.correctionModel";

    final String id;
    final String label;
    final String reasoningEffort;

    CorrectionModel(String id, String label, String reasoningEffort) {
        this.id = id;
        this.label = label;
        this.reasoningEffort = reasoningEffort;
    }

    static CorrectionModel selected() {
        String value = PropertiesComponent.getInstance().getValue(SETTINGS_KEY);
        for (CorrectionModel model : values()) {
            if (model.id.equals(value)) return model;
        }
        return LUNA;
    }

    static void save(CorrectionModel model) {
        PropertiesComponent.getInstance().setValue(SETTINGS_KEY, model.id);
    }

    @Override
    public String toString() {
        return label;
    }
}
