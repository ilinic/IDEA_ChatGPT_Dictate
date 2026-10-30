package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/** Two independent function-key shortcuts in IntelliJ's active Keymap. Escape is reserved. */
final class DictationKeymap {
    static final String START_ACTION = "nz.ilinic.ideadictate.ToggleRecording";
    static final String SEND_ACTION = "nz.ilinic.ideadictate.FinishAndSend";
    private static final String START_KEY = "nz.ilinic.ideadictate.functionKey";
    private static final String SEND_KEY = "nz.ilinic.ideadictate.sendFunctionKey";

    private DictationKeymap() { }

    static String savedStart() {
        return PropertiesComponent.getInstance().getValue(START_KEY, "");
    }

    static String savedSend() {
        return PropertiesComponent.getInstance().getValue(SEND_KEY, "");
    }

    static KeyStroke stroke(String key) {
        if (key == null || !key.matches("F([1-9]|1[0-2])")) return null;
        int keyCode = KeyEvent.VK_F1 + Integer.parseInt(key.substring(1)) - 1;
        return KeyStroke.getKeyStroke(keyCode, 0);
    }

    static String name(KeyStroke key) {
        if (key == null || key.getModifiers() != 0) return "";
        for (int i = 1; i <= 12; i++) {
            if (key.getKeyCode() == KeyEvent.VK_F1 + i - 1) return "F" + i;
        }
        return "";
    }

    static String[] conflicts(String key, String actionId) {
        KeymapManager manager = KeymapManager.getInstance();
        KeyStroke stroke = stroke(key);
        if (manager == null || stroke == null) return new String[0];
        return Arrays.stream(manager.getActiveKeymap().getActionIds(stroke))
                .filter(id -> !START_ACTION.equals(id) && !SEND_ACTION.equals(id) && !actionId.equals(id))
                .toArray(String[]::new);
    }

    static void assign(String start, String send) {
        if (stroke(start) == null) throw new IllegalArgumentException("Start key must be F1–F12");
        if (!send.isEmpty() && stroke(send) == null) throw new IllegalArgumentException("Send key must be F1–F12");
        if (start.equals(send)) throw new IllegalArgumentException("Two actions cannot use the same function key");

        install(START_ACTION, start);
        install(SEND_ACTION, send);
        PropertiesComponent prefs = PropertiesComponent.getInstance();
        prefs.setValue(START_KEY, start);
        prefs.setValue(SEND_KEY, send);
    }

    static void restoreSaved() {
        String start = savedStart();
        String send = savedSend();
        if (stroke(start) != null && !start.equals(send)) install(START_ACTION, start);
        if (stroke(send) != null && !start.equals(send)) install(SEND_ACTION, send);
    }

    private static void install(String actionId, String key) {
        KeymapManager manager = KeymapManager.getInstance();
        if (manager == null) return;
        Keymap map = manager.getActiveKeymap();
        KeyStroke stroke = stroke(key);

        // A confirmed conflict is removed for the chosen action, as with the normal Keymap UI.
        if (stroke != null) {
            for (String conflictingId : map.getActionIds(stroke)) {
                if (actionId.equals(conflictingId)) continue;
                for (Shortcut shortcut : map.getShortcuts(conflictingId)) {
                    if (shortcut instanceof KeyboardShortcut keyboard
                            && stroke.equals(keyboard.getFirstKeyStroke())) {
                        map.removeShortcut(conflictingId, shortcut);
                    }
                }
            }
        }
        map.removeAllActionShortcuts(actionId);
        if (stroke != null) map.addShortcut(actionId, new KeyboardShortcut(stroke, null));
    }
}

