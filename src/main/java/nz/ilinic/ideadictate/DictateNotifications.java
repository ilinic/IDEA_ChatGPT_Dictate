package nz.ilinic.ideadictate;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

final class DictateNotifications {
    private static final String GROUP = "IDEA ChatGPT Dictate";

    private DictateNotifications() { }

    static void info(Project project, String message) {
        showStatus(project, message);
    }

    static void warning(Project project, String message) {
        showStatus(project, message);
        log(project, message, NotificationType.WARNING);
    }

    static void error(Project project, String message) {
        showStatus(project, message);
        log(project, message, NotificationType.ERROR);
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

