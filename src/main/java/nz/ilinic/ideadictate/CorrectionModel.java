package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;

/** Models used only for correcting the transcribed text, not for speech recognition. */
enum CorrectionModel {
    LUNA("gpt-6-luna", "model.luna", "none"),
    TERRA("gpt-6.1-terra", "model.terra", "none"),
    SOL("gpt-6.1-sol", "model.sol", "low"),
    ASTRA("gpt-6-astra", "model.astra", "low");

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
        return I18n.text(label);
    }
}
