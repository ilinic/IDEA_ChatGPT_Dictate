package nz.ilinic.ideadictate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/** Persistent per-project status bar indicator, unaffected by unrelated IDE messages. */
final class DictateStatusWidget implements CustomStatusBarWidget {
    static final String ID = "nz.ilinic.ideadictate.status";

    private final Project project;
    private final JBLabel label = new JBLabel();

    DictateStatusWidget(Project project) {
        this.project = project;
        label.setBorder(JBUI.Borders.empty(0, 8));
        refresh();
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return label;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        refresh();
    }

    void refresh() {
        DictateNotifications.Stage stage = DictateNotifications.currentStage(project);
        label.setText(I18n.text(stage.messageKey));
        label.setToolTipText(I18n.text("status.tooltip"));
    }

    @Override
    public void dispose() { }
}
