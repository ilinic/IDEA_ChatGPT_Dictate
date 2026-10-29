package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;

public final class ContextDiagnosticsAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        String editorReport = ReadAction.compute(() -> EditorContext.capture(project));
        new Task.Backgroundable(project, "Inspecting local Codex sessions", true) {
            private String report;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                report = editorReport + "\n" + CodexSessionScanner.buildReport(project.getBasePath());
            }

            @Override
            public void onSuccess() {
                if (!project.isDisposed()) new DiagnosticDialog(project, report).show();
            }
        }.queue();
    }

    private static final class DiagnosticDialog extends DialogWrapper {
        private final String report;

        DiagnosticDialog(Project project, String report) {
            super(project);
            this.report = report;
            setTitle("IDEA ChatGPT Dictate — Diagnostics");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JTextArea area = new JTextArea(report, 26, 100);
            area.setEditable(false);
            area.setCaretPosition(0);
            JScrollPane scrollPane = new JScrollPane(area);
            scrollPane.setPreferredSize(new Dimension(900, 600));
            return scrollPane;
        }
    }
}

