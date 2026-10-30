package nz.ilinic.ideadictate;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

final class DictateNotifications {
    private static final String GROUP = "IDEA ChatGPT Dictate";

    private DictateNotifications() { }

    static void info(Project project, String message) {
        show(project, message, NotificationType.INFORMATION);
    }

    static void warning(Project project, String message) {
        show(project, message, NotificationType.WARNING);
    }

    static void error(Project project, String message) {
        show(project, message, NotificationType.ERROR);
    }

    private static void show(Project project, String message, NotificationType type) {
        if (project.isDisposed()) return;
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
                .createNotification(message, type).notify(project);
    }
}

