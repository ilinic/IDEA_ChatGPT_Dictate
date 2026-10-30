package nz.ilinic.ideadictate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Avoids pasting into the source editor if the user changes focus during the API call. */
final class SafePaste {
    private SafePaste() { }

    static Component currentFocus() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    static void deliver(Project project, String text, Component expectedFocus,
                        boolean submit, BooleanSupplier active, Runnable completed) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !active.getAsBoolean()) {
                completed.run();
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(text));

            if (expectedFocus == null || currentFocus() != expectedFocus) {
                DictateNotifications.warning(project, I18n.text("paste.focusChanged"));
                completed.run();
                return;
            }
            if (submit && isEditorOrTerminal(expectedFocus)) {
                DictateNotifications.warning(project, I18n.text("paste.sendUnsupported"));
                completed.run();
                return;
            }

            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                try {
                    if (!active.getAsBoolean() || project.isDisposed()) return;
                    if (currentFocus() != expectedFocus) {
                        DictateNotifications.warning(project, I18n.text("paste.focusLost"));
                        return;
                    }
                    Robot robot = new Robot();
                    robot.setAutoDelay(30);
                    int modifier = System.getProperty("os.name", "").toLowerCase().contains("mac")
                            ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
                    robot.keyPress(modifier);
                    try {
                        robot.keyPress(KeyEvent.VK_V);
                        robot.keyRelease(KeyEvent.VK_V);
                    } finally {
                        robot.keyRelease(modifier);
                    }
                    if (submit) {
                        // Wait until the composer applies Ctrl+V before pressing Enter.
                        robot.delay(160);
                        if (!active.getAsBoolean()) return;
                        if (currentFocus() != expectedFocus) {
                            DictateNotifications.warning(project, I18n.text("paste.sendFocusLost"));
                            return;
                        }
                        robot.keyPress(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_ENTER);
                        DictateNotifications.info(project, I18n.text("recording.sent"));
                    } else {
                        DictateNotifications.info(project, I18n.text("recording.ready"));
                    }
                } catch (Exception error) {
                    DictateNotifications.warning(project, I18n.text("paste.unavailable"));
                } finally {
                    completed.run();
                }
            }, 130, TimeUnit.MILLISECONDS);
        });
    }

    private static boolean isEditorOrTerminal(Component focus) {
        for (Component part = focus; part != null; part = part.getParent()) {
            String type = part.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (type.contains("terminal") || type.contains("editorcomponentimpl")) return true;
        }
        return false;
    }
}

