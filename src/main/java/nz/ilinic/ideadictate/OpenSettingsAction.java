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
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
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
        private final JComboBox<String> hotkey = new JComboBox<>();
        private final JComboBox<CorrectionModel> model = new JComboBox<>(CorrectionModel.values());
        private final JList<SpeechLanguages.Language> languages = new JList<>(SpeechLanguages.Language.values());
        private final JCheckBox context = new JCheckBox(I18n.text("settings.context"));
        private final JComboBox<Integer> contextTurns = new JComboBox<>(new Integer[] {2, 4, 8, 16});

        SettingsDialog(Project project, String savedKey) {
            super(project);
            this.project = project;
            originalKey = savedKey == null ? "" : savedKey;
            apiKey.setText(originalKey);

            String savedHotkey = DictationKeymap.saved();
            for (int i = 1; i <= 12; i++) hotkey.addItem("F" + i);
            hotkey.setSelectedItem(savedHotkey.isBlank() ? "F12" : savedHotkey);
            model.setSelectedItem(CorrectionModel.selected());

            languages.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            languages.setVisibleRowCount(4);
            List<SpeechLanguages.Language> selected = SpeechLanguages.selected();
            int[] selectedIndices = selected.stream().mapToInt(language -> language.ordinal()).toArray();
            languages.setSelectedIndices(selectedIndices);

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
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));

            addRow(panel, 0, I18n.text("settings.key"), apiKey);
            addRow(panel, 1, "", new JLabel(I18n.text("settings.key.note")));
            addRow(panel, 2, "", new JLabel(I18n.text("settings.key.env")));
            addRow(panel, 3, I18n.text("settings.hotkey"), hotkey);
            addRow(panel, 4, I18n.text("settings.model"), model);

            JScrollPane languageList = new JScrollPane(languages);
            languageList.setPreferredSize(new Dimension(300, 102));
            addRow(panel, 5, I18n.text("settings.languages"), languageList);
            addRow(panel, 6, "", new JLabel(I18n.text("settings.languages.help")));

            addRow(panel, 7, "", context);
            addRow(panel, 8, I18n.text("settings.context.length"), contextTurns);
            addRow(panel, 9, "", new JLabel(I18n.text("settings.context.note")));
            addRow(panel, 10, "", new JLabel(I18n.text("settings.speech")));
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
            String key = (String) hotkey.getSelectedItem();
            if (key != null && !key.equals(DictationKeymap.saved())) {
                String[] conflicts = DictationKeymap.conflicts(key);
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
            String key = (String) hotkey.getSelectedItem();
            if (key != null) DictationKeymap.assign(key);
            CorrectionModel selectedModel = (CorrectionModel) model.getSelectedItem();
            if (selectedModel != null) CorrectionModel.save(selectedModel);

            SpeechLanguages.save(languages.getSelectedValuesList());
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

