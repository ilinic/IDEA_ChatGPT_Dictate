package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;

/** Global preferences shared by all IDE projects. */
final class SettingsPrefs {
    private static final String CODEX_CONTEXT = "nz.ilinic.ideadictate.useCodexContext";
    private static final String CODEX_TURNS = "nz.ilinic.ideadictate.codexContextTurns";

    private SettingsPrefs() { }

    static boolean useCodexContext() {
        return PropertiesComponent.getInstance().getBoolean(CODEX_CONTEXT, true);
    }

    static void useCodexContext(boolean use) {
        PropertiesComponent.getInstance().setValue(CODEX_CONTEXT, use, true);
    }

    static int contextTurns() {
        int turns = PropertiesComponent.getInstance().getInt(CODEX_TURNS, 8);
        return turns == 2 || turns == 4 || turns == 8 || turns == 16 ? turns : 8;
    }

    static void contextTurns(int turns) {
        PropertiesComponent.getInstance().setValue(CODEX_TURNS, turns, 8);
    }
}
