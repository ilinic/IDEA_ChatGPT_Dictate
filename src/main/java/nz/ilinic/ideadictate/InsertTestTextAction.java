package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

/** Focused-input test. The action pastes text but never presses Enter or sends a chat turn. */
public final class InsertTestTextAction extends AnAction implements DumbAware {
    public static final String MARKER = "[IDEA_DICTATE_PASTE_TEST] ";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        CopyPasteManager.getInstance().setContents(new StringSelection(MARKER));

        // The IDE action keeps focus in the original chat input when invoked by shortcut.
        // Delay allows the Tools menu to close when invoked with the mouse.
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
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
            } catch (AWTException | SecurityException failure) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showWarningDialog(event.getProject(),
                                "Could not synthesize paste. The test marker is on your clipboard; "
                                        + "focus the Codex composer and press Ctrl+V manually.\n"
                                        + failure.getMessage(),
                                "IDEA ChatGPT Dictate"));
            }
        }, 250, TimeUnit.MILLISECONDS);
    }
}

