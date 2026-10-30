package nz.ilinic.ideadictate;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** All user-facing plugin settings in one dialog. The Tools menu contains only two commands. */
public final class OpenSettingsAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        // PasswordSafe and reading JSONL sessions must stay off the EDT.
        new Task.Backgroundable(project, "Загрузка настроек диктовки", true) {
            private boolean keyAvailable;
            private List<CodexSessionScanner.Candidate> sessions = List.of();
            private Path pinnedPath;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                keyAvailable = ApiKeyStore.get() != null;
                pinnedPath = PinnedConversation.get(project);
                sessions = CodexSessionScanner.recentMatching(project.getBasePath());
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) return;
                SettingsDialog dialog = new SettingsDialog(project, keyAvailable, pinnedPath, sessions);
                if (dialog.showAndGet()) dialog.saveSettings();
            }
        }.queue();
    }

    private record ConversationChoice(String label, Path path) {
        @Override
        public String toString() { return label; }
    }

    private static final class SettingsDialog extends DialogWrapper {
        private final Project project;
        private final JPasswordField apiKey = new JPasswordField(34);
        private final JCheckBox removeKey = new JCheckBox("Удалить сохранённый API-ключ");
        private final JComboBox<String> hotkey = new JComboBox<>();
        private final JComboBox<CorrectionModel> model = new JComboBox<>(CorrectionModel.values());
        private final JComboBox<ConversationChoice> conversation = new JComboBox<>();
        private final JLabel keyStatus;

        SettingsDialog(Project project, boolean keyAvailable, Path pinnedPath,
                       List<CodexSessionScanner.Candidate> sessions) {
            super(project);
            this.project = project;
            keyStatus = new JLabel(keyAvailable
                    ? "Ключ уже доступен. Пустое поле оставит его без изменений."
                    : "Ключ пока не настроен. Вставь OpenAI Platform API key.");

            String savedHotkey = DictationKeymap.saved();
            for (int i = 1; i <= 12; i++) hotkey.addItem("F" + i);
            hotkey.setSelectedItem(savedHotkey.isBlank() ? "F12" : savedHotkey);
            model.setSelectedItem(CorrectionModel.selected());

            List<ConversationChoice> options = new ArrayList<>();
            options.add(new ConversationChoice("Не использовать историю Codex", null));
            for (CodexSessionScanner.Candidate candidate : sessions) {
                String name = candidate.display();
                options.add(new ConversationChoice(name.length() > 100 ? name.substring(0, 100) + "…" : name,
                        candidate.path()));
            }
            if (pinnedPath != null && options.stream().noneMatch(o -> pinnedPath.equals(o.path()))) {
                options.add(new ConversationChoice("Выбран ранее: " + pinnedPath.getFileName(), pinnedPath));
            }
            int selected = 0;
            for (int i = 0; i < options.size(); i++) {
                ConversationChoice option = options.get(i);
                conversation.addItem(option);
                if (pinnedPath != null && pinnedPath.equals(option.path())) selected = i;
            }
            conversation.setSelectedIndex(selected);

            removeKey.addActionListener(e -> apiKey.setEnabled(!removeKey.isSelected()));
            setTitle("Настройки диктовки");
            setOKButtonText("Сохранить");
            setCancelButtonText("Отмена");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            addRow(panel, 0, "API-ключ OpenAI:", apiKey);
            addRow(panel, 1, "", keyStatus);
            addRow(panel, 2, "", removeKey);
            addRow(panel, 3, "Горячая клавиша:", hotkey);
            addRow(panel, 4, "Модель правки текста:", model);
            addRow(panel, 5, "Контекст диалога:", conversation);
            addRow(panel, 6, "", new JLabel("Распознавание речи: gpt-transcribe. История Codex — по выбору."));
            return panel;
        }

        private static void addRow(JPanel panel, int row, String title, JComponent field) {
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = row;
            left.anchor = GridBagConstraints.WEST;
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
            String key = (String) hotkey.getSelectedItem();
            if (key != null && !key.equals(DictationKeymap.saved())) {
                String[] conflicts = DictationKeymap.conflicts(key);
                if (conflicts.length > 0) {
                    String example = String.join(", ", Arrays.copyOf(conflicts, Math.min(5, conflicts.length)));
                    int answer = Messages.showYesNoDialog(project,
                            "Клавиша " + key + " уже используется: " + example
                                    + "\nПереназначить её на диктовку?",
                            "Конфликт горячей клавиши", Messages.getWarningIcon());
                    if (answer != Messages.YES) return;
                }
            }
            super.doOKAction();
        }

        void saveSettings() {
            String key = (String) hotkey.getSelectedItem();
            if (key != null) DictationKeymap.assign(key);
            CorrectionModel selectedModel = (CorrectionModel) model.getSelectedItem();
            if (selectedModel != null) CorrectionModel.save(selectedModel);
            ConversationChoice selectedConversation = (ConversationChoice) conversation.getSelectedItem();
            PinnedConversation.set(project, selectedConversation == null ? null : selectedConversation.path());

            char[] chars = apiKey.getPassword();
            try {
                if (removeKey.isSelected() || chars.length > 0) {
                    String newKey = removeKey.isSelected() ? null : new String(chars);
                    AppExecutorUtil.getAppExecutorService().execute(() -> {
                        try {
                            ApiKeyStore.set(newKey);
                            DictateNotifications.info(project, "Настройки диктовки и API-ключ сохранены.");
                        } catch (RuntimeException error) {
                            DictateNotifications.error(project, "Не удалось сохранить API-ключ: "
                                    + error.getClass().getSimpleName());
                        }
                    });
                } else {
                    DictateNotifications.info(project, "Настройки диктовки сохранены.");
                }
            } finally {
                Arrays.fill(chars, '\0');
                apiKey.setText("");
            }
        }
    }
}
