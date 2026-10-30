package nz.ilinic.ideadictate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

/** Registers the always-visible dictation state widget in each project window. */
public final class DictateStatusWidgetFactory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
        return DictateStatusWidget.ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "ChatGPT Dictate";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new DictateStatusWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        Disposer.dispose(widget);
    }
}
