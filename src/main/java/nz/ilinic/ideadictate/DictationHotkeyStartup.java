package nz.ilinic.ideadictate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Reinstalls a chosen F-key in case the active keymap is a built-in, read-only scheme. */
public final class DictationHotkeyStartup implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(DictationKeymap::restoreSaved);
    }
}

