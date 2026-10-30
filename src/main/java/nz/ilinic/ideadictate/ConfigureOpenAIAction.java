package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.BoxLayout;
import javax.swing.border.EmptyBorder;
import java.util.Arrays;

public final class ConfigureOpenAIAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        KeyDialog dialog = new KeyDialog();
        if (dialog.showAndGet()) {
            char[] input = dialog.password.getPassword();
            String key;
            try {
                key = new String(input);
            } finally {
                Arrays.fill(input, '\0');
            }
            AppExecutorUtil.getAppExecutorService().execute(() -> {
                ApiKeyStore.set(key);
                if (event.getProject() != null) {
                    DictateNotifications.info(event.getProject(), "OpenAI API key saved in IDE Password Safe.");
                }
            });
        }
    }

    private static final class KeyDialog extends DialogWrapper {
        private final JPasswordField password = new JPasswordField(46);

        KeyDialog() {
            super(true);
            setTitle("OpenAI API Key — IDEA ChatGPT Dictate");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("Paste an OpenAI Platform API key (not a ChatGPT login or subscription):"));
            panel.add(password);
            panel.add(new JLabel("Leave empty to clear the saved key; OPENAI_API_KEY is a fallback."));
            return panel;
        }
    }
}

