package nz.ilinic.ideadictate;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;

final class DictateNotifications {
    private static final String GROUP = "IDEA ChatGPT Dictate";
    private static final Key<Stage> STAGE_KEY = Key.create("nz.ilinic.ideadictate.status.stage");
    private static final Key<String> DETAIL_KEY = Key.create("nz.ilinic.ideadictate.status.detail");

    enum Stage {
        READY("status.ready"), STARTING("status.starting"), RECORDING("status.recording"),
        TRANSCRIBING("status.transcribing"), CORRECTING("status.correcting"),
        INSERTING("status.inserting"), SENDING("status.sending"),
        DONE("status.done"), CLIPBOARD("status.clipboard"),
        CANCELLED("status.cancelled"), ERROR("status.error");

        final String messageKey;
        Stage(String messageKey) { this.messageKey = messageKey; }
    }

    private DictateNotifications() { }

    static Stage currentStage(Project project) {
        Stage stage = project.getUserData(STAGE_KEY);
        return stage == null ? Stage.READY : stage;
    }

    static String currentDetail(Project project) {
        return project.getUserData(DETAIL_KEY);
    }

    static void stage(Project project, Stage stage) {
        if (project.isDisposed()) return;
        project.putUserData(STAGE_KEY, stage);
        project.putUserData(DETAIL_KEY, null);
        refreshWidget(project);
    }

    private static void refreshWidget(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            var bar = WindowManager.getInstance().getStatusBar(project);
            if (bar != null && bar.getWidget(DictateStatusWidget.ID) instanceof DictateStatusWidget widget) {
                widget.refresh();
            }
        });
    }

    static void info(Project project, String message) {
        showStatus(project, message);
    }

    static void warning(Project project, String message) {
        detail(project, message);
        showStatus(project, message);
        log(project, message, NotificationType.WARNING);
    }

    static void error(Project project, String message) {
        detail(project, message);
        showStatus(project, message);
        log(project, message, NotificationType.ERROR);
    }

    private static void detail(Project project, String message) {
        if (project.isDisposed()) return;
        project.putUserData(DETAIL_KEY, message);
        refreshWidget(project);
    }

    private static void showStatus(Project project, String message) {
        if (project.isDisposed()) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) StatusBar.Info.set(message, project, GROUP);
        });
    }

    private static void log(Project project, String message, NotificationType type) {
        if (project.isDisposed()) return;
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
                .createNotification(message, type).notify(project);
    }
}

