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
        String text = I18n.text(stage.messageKey);
        String detail = DictateNotifications.currentDetail(project);
        if (detail != null && !detail.isBlank()
                && (stage == DictateNotifications.Stage.ERROR
                || stage == DictateNotifications.Stage.CLIPBOARD)) {
            String preview = detail.length() > 50 ? detail.substring(0, 49) + "…" : detail;
            text += " — " + preview;
        }
        label.setText(text);
        label.setToolTipText(detail != null && !detail.isBlank() ? detail : I18n.text("status.tooltip"));
    }

    @Override
    public void dispose() { }
}
