package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.Arrays;

final class DictationKeymap {
    static final String ACTION_ID = "nz.ilinic.ideadictate.ToggleRecording";
    private static final String KEY = "nz.ilinic.ideadictate.functionKey";

    private DictationKeymap() { }

    static String saved() {
        return PropertiesComponent.getInstance().getValue(KEY, "");
    }

    static String[] conflicts(String key) {
        KeymapManager manager = KeymapManager.getInstance();
        if (manager == null) return new String[0];
        return Arrays.stream(manager.getActiveKeymap().getActionIds(keyStroke(key)))
                .filter(id -> !ACTION_ID.equals(id))
                .toArray(String[]::new);
    }

    static void assign(String key) {
        if (!key.matches("F([1-9]|1[0-2])")) throw new IllegalArgumentException("Only F1–F12 allowed");
        install(key);
        PropertiesComponent.getInstance().setValue(KEY, key);
    }

    static void restoreSaved() {
        String key = saved();
        if (key.matches("F([1-9]|1[0-2])")) install(key);
    }

    private static void install(String key) {
        KeymapManager manager = KeymapManager.getInstance();
        if (manager == null) return;
        Keymap map = manager.getActiveKeymap();
        KeyboardShortcut preferred = new KeyboardShortcut(keyStroke(key), null);

        // The user explicitly approved replacing this F-key's other action assignments.
        for (String conflictId : map.getActionIds(keyStroke(key))) {
            if (ACTION_ID.equals(conflictId)) continue;
            for (Shortcut shortcut : map.getShortcuts(conflictId)) {
                if (shortcut instanceof KeyboardShortcut keyboard
                        && keyStroke(key).equals(keyboard.getFirstKeyStroke())) {
                    map.removeShortcut(conflictId, shortcut);
                }
            }
        }
        map.removeAllActionShortcuts(ACTION_ID);
        map.addShortcut(ACTION_ID, preferred);
    }

    private static KeyStroke keyStroke(String key) {
        int keyCode = KeyEvent.VK_F1 + Integer.parseInt(key.substring(1)) - 1;
        return KeyStroke.getKeyStroke(keyCode, 0);
    }
}

