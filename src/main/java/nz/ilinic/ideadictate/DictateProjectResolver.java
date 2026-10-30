package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;

import java.awt.KeyboardFocusManager;
import java.awt.Window;

/** Resolves the owning project when a Codex/JCEF tool window has no action DataContext project. */
final class DictateProjectResolver {
    private DictateProjectResolver() { }

    static boolean hasOpenProject(AnActionEvent event) {
        return event.getProject() != null || ProjectManager.getInstance().getOpenProjects().length > 0;
    }

    static Project resolve(AnActionEvent event) {
        Project project = event.getProject();
        if (project != null && !project.isDisposed()) return project;

        Project[] open = ProjectManager.getInstance().getOpenProjects();
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        for (Project candidate : open) {
            if (candidate.isDisposed()) continue;
            Window frame = WindowManager.getInstance().getFrame(candidate);
            for (Window owner = focused; owner != null; owner = owner.getOwner()) {
                if (owner == frame && frame != null) return candidate;
            }
        }
        return open.length == 1 && !open[0].isDisposed() ? open[0] : null;
    }
}
