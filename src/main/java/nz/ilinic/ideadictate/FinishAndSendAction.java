package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Ends the active recording and submits its corrected transcript to the focused composer. */
public final class FinishAndSendAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(FinishAndSendAction.class);
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
        event.getPresentation().setText(I18n.text("menu.send"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        try {
            project.getService(VoiceDictationService.class).finishAndSend();
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Failed to stop and submit dictation", error);
            DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
            DictateNotifications.error(project, I18n.text("recording.unexpected", error.getClass().getSimpleName()));
        }
    }
}

