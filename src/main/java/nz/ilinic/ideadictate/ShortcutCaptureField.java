package nz.ilinic.ideadictate;

import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.components.JBTextField;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/**
 * Shortcut recorder styled as a native IntelliJ input.
 * The internal JetBrains ShortcutTextField is not a public plugin API.
 */
final class ShortcutCaptureField extends JBTextField {
    private KeyStroke shortcut;

    ShortcutCaptureField() {
        setEditable(false);
        setFocusable(true);
        setColumns(14);
        setToolTipText(I18n.text("settings.shortcut.tip"));
    }

    void setShortcut(String value) {
        shortcut = DictationKeymap.stroke(value);
        setText(shortcut == null ? "" : KeymapUtil.getKeystrokeText(shortcut));
    }

    String getShortcut() {
        return DictationKeymap.name(shortcut);
    }

    boolean capture(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_TAB || key == KeyEvent.VK_ENTER) {
            return false; // Keep IDE/dialog navigation intact.
        }
        if (key == KeyEvent.VK_DELETE || key == KeyEvent.VK_BACK_SPACE) {
            setShortcut("");
            return true;
        }
        if (key >= KeyEvent.VK_F1 && key <= KeyEvent.VK_F12 && event.getModifiersEx() == 0) {
            shortcut = KeyStroke.getKeyStrokeForEvent(event);
            setText(KeymapUtil.getKeystrokeText(shortcut));
            return true;
        }
        // The dialog intentionally accepts only bare F1-F12 keys.
        return true;
    }
}

