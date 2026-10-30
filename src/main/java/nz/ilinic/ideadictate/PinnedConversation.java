package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Explicit user opt-in: never guess the active Codex chat from timestamps. */
final class PinnedConversation {
    private static final String KEY = "nz.ilinic.ideadictate.pinnedCodexPath";

    private PinnedConversation() { }

    static void set(Project project, Path path) {
        PropertiesComponent.getInstance(project).setValue(KEY, path == null ? null : path.toString());
    }

    static Path get(Project project) {
        String configured = PropertiesComponent.getInstance(project).getValue(KEY);
        if (configured == null || configured.isBlank()) return null;
        try {
            Path path = Path.of(configured);
            return CodexSessionScanner.belongsToProject(path, project.getBasePath()) ? path : null;
        } catch (InvalidPathException | SecurityException ex) {
            return null;
        }
    }

    static String context(Project project) {
        Path path = get(project);
        return path == null ? "" : CodexSessionScanner.excerptFromPinned(path);
    }
}

