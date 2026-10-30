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

/** Avoids pasting into the source editor if the user changes focus during the API call. */
final class SafePaste {
    private SafePaste() { }

    static Component currentFocus() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    static void deliver(Project project, String text, Component expectedFocus) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            CopyPasteManager.getInstance().setContents(new StringSelection(text));

            if (expectedFocus == null || currentFocus() != expectedFocus) {
                DictateNotifications.warning(project, "Диктовка готова и скопирована в буфер. "
                        + "Фокус ввода изменился — нажми Ctrl+V в чате Codex.");
                return;
            }

            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                if (currentFocus() != expectedFocus || project.isDisposed()) {
                    DictateNotifications.warning(project, "Текст в буфере обмена. "
                            + "Поставь курсор в чат Codex и нажми Ctrl+V.");
                    return;
                }
                try {
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
                } catch (Exception error) {
                    DictateNotifications.warning(project, "Автовставка недоступна. "
                            + "Текст в буфере обмена; вставь его вручную Ctrl+V.");
                }
            }, 130, TimeUnit.MILLISECONDS);
        });
    }
}

