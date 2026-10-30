package nz.ilinic.ideadictate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Pastes dictation into whichever input is focused when delivery completes. */
final class SafePaste {
    private SafePaste() { }

    static void deliver(Project project, String text, boolean submit,
                        BooleanSupplier active, Runnable completed) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !active.getAsBoolean()) {
                completed.run();
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(text));

            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                try {
                    if (!active.getAsBoolean() || project.isDisposed()) return;
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
                        robot.keyPress(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_ENTER);
                        DictateNotifications.stage(project, DictateNotifications.Stage.DONE);
                        DictateNotifications.info(project, I18n.text("recording.sent"));
                    } else {
                        DictateNotifications.stage(project, DictateNotifications.Stage.DONE);
                        DictateNotifications.info(project, I18n.text("recording.ready"));
                    }
                } catch (Exception error) {
                    DictateNotifications.stage(project, DictateNotifications.Stage.CLIPBOARD);
                    DictateNotifications.warning(project, I18n.text("paste.unavailable"));
                } finally {
                    completed.run();
                }
            }, 130, TimeUnit.MILLISECONDS);
        });
    }
}

