package nz.ilinic.ideadictate;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.border.EmptyBorder;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Objects;

/** One settings dialog for the plugin, localized to IntelliJ's UI language. */
public final class OpenSettingsAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
        event.getPresentation().setText(I18n.text("menu.settings"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        // IntelliJ Password Safe may prompt or block. Never read it on the UI thread.
        new Task.Backgroundable(project, I18n.text("settings.loading"), true) {
            private String savedKey;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                savedKey = ApiKeyStore.getSaved();
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) return;
                SettingsDialog dialog = new SettingsDialog(project, savedKey);
                if (dialog.showAndGet()) dialog.saveSettings();
            }
        }.queue();
    }

    private static final class SettingsDialog extends DialogWrapper {
        private final Project project;
        private final String originalKey;
        private final JPasswordField apiKey = new JPasswordField(34);
        private final ShortcutCaptureField hotkey = new ShortcutCaptureField();
        private final ShortcutCaptureField sendHotkey = new ShortcutCaptureField();
        private final JComboBox<CorrectionModel> model = new JComboBox<>(CorrectionModel.values());
        private final LanguageMultiSelectField languages = new LanguageMultiSelectField();
        private final JCheckBox context = new JCheckBox(I18n.text("settings.context"));
        private final JComboBox<Integer> contextTurns = new JComboBox<>(new Integer[] {2, 4, 8, 16});

        SettingsDialog(Project project, String savedKey) {
            super(project);
            this.project = project;
            originalKey = savedKey == null ? "" : savedKey;
            apiKey.setText(originalKey);

            hotkey.setShortcut(DictationKeymap.savedStart().isBlank() ? "F12" : DictationKeymap.savedStart());
            sendHotkey.setShortcut(DictationKeymap.savedSend());
            model.setSelectedItem(CorrectionModel.selected());

            context.setSelected(SettingsPrefs.useCodexContext());
            contextTurns.setSelectedItem(SettingsPrefs.contextTurns());
            contextTurns.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                    new JLabel(value == null ? "" : I18n.text("settings.context.count", value)));
            contextTurns.setEnabled(context.isSelected());
            context.addActionListener(e -> contextTurns.setEnabled(context.isSelected()));

            setTitle(I18n.text("settings.title"));
            setOKButtonText(I18n.text("settings.save"));
            setCancelButtonText(I18n.text("settings.cancel"));
            init();
            // Intercept function keys before the IDE keymap dispatches them as actions.
            IdeEventQueue.getInstance().addDispatcher(event -> {
                if (!(event instanceof KeyEvent keyEvent)) return false;
                Object focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus == hotkey) return hotkey.capture(keyEvent);
                if (focus == sendHotkey) return sendHotkey.capture(keyEvent);
                return false;
            }, getDisposable());
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));

            addRow(panel, 0, I18n.text("settings.key"), apiKey);
            addRow(panel, 1, "", new JLabel(I18n.text("settings.key.note")));
            addRow(panel, 2, "", new JLabel(I18n.text("settings.key.env")));
            addRow(panel, 3, I18n.text("settings.hotkey"), hotkey);
            addRow(panel, 4, I18n.text("settings.send.hotkey"), sendHotkey);
            addRow(panel, 5, "", new JLabel(I18n.text("settings.shortcut.tip")));
            addRow(panel, 6, I18n.text("settings.model"), model);

            addRow(panel, 7, I18n.text("settings.languages"), languages);
            addRow(panel, 8, "", new JLabel(I18n.text("settings.languages.help")));

            addRow(panel, 9, "", context);
            addRow(panel, 10, I18n.text("settings.context.length"), contextTurns);
            addRow(panel, 11, "", new JLabel(I18n.text("settings.context.note")));
            addRow(panel, 12, "", new JLabel(I18n.text("settings.speech")));
            return panel;
        }

        private static void addRow(JPanel panel, int row, String title, JComponent field) {
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = row;
            left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(5, 0, 5, 16);
            panel.add(new JLabel(title), left);

            GridBagConstraints right = new GridBagConstraints();
            right.gridx = 1;
            right.gridy = row;
            right.weightx = 1.0;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.insets = new Insets(5, 0, 5, 0);
            panel.add(field, right);
        }

        @Override
        protected void doOKAction() {
            String start = hotkey.getShortcut();
            String send = sendHotkey.getShortcut();
            if (start.isBlank()) {
                Messages.showErrorDialog(project, I18n.text("settings.shortcut.required"),
                        I18n.text("settings.conflict.title"));
                return;
            }
            if (!send.isBlank() && start.equals(send)) {
                Messages.showErrorDialog(project, I18n.text("settings.shortcut.duplicate"),
                        I18n.text("settings.conflict.title"));
                return;
            }
            for (String[] entry : new String[][] {
                    {start, DictationKeymap.savedStart(), DictationKeymap.START_ACTION},
                    {send, DictationKeymap.savedSend(), DictationKeymap.SEND_ACTION}
            }) {
                String key = entry[0];
                if (key.isBlank() || key.equals(entry[1])) continue;
                String[] conflicts = DictationKeymap.conflicts(key, entry[2]);
                if (conflicts.length > 0) {
                    String example = String.join(", ", Arrays.copyOf(conflicts, Math.min(5, conflicts.length)));
                    int answer = Messages.showYesNoDialog(project,
                            I18n.text("settings.conflict", key, example),
                            I18n.text("settings.conflict.title"), Messages.getWarningIcon());
                    if (answer != Messages.YES) return;
                }
            }
            super.doOKAction();
        }

        void saveSettings() {
            DictationKeymap.assign(hotkey.getShortcut(), sendHotkey.getShortcut());
            CorrectionModel selectedModel = (CorrectionModel) model.getSelectedItem();
            if (selectedModel != null) CorrectionModel.save(selectedModel);

            SpeechLanguages.save(languages.getSelectedLanguages());
            SettingsPrefs.useCodexContext(context.isSelected());
            Integer count = (Integer) contextTurns.getSelectedItem();
            if (count != null) SettingsPrefs.contextTurns(count);

            char[] chars = apiKey.getPassword();
            try {
                String newKey = new String(chars).trim();
                if (!Objects.equals(originalKey, newKey)) {
                    AppExecutorUtil.getAppExecutorService().execute(() -> {
                        try {
                            ApiKeyStore.set(newKey.isEmpty() ? null : newKey);
                            DictateNotifications.info(project, I18n.text("settings.saved.key"));
                        } catch (RuntimeException error) {
                            DictateNotifications.error(project,
                                    I18n.text("settings.key.error", error.getClass().getSimpleName()));
                        }
                    });
                } else {
                    DictateNotifications.info(project, I18n.text("settings.saved"));
                }
            } finally {
                Arrays.fill(chars, '\0');
                apiKey.setText("");
            }
        }
    }
}

