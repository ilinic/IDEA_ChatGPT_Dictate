package nz.ilinic.ideadictate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Restores both configured function-key actions in the active IntelliJ keymap. */
public final class DictationHotkeyStartup implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(DictationKeymap::restoreSaved);
    }
}

