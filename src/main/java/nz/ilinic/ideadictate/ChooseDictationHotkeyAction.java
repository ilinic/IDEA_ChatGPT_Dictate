package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ChooseDictationHotkeyAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        String[] keys = new String[12];
        for (int i = 0; i < keys.length; i++) keys[i] = "F" + (i + 1);
        String current = DictationKeymap.saved();
        int index = Messages.showChooseDialog(event.getProject(),
                "Выбери одну клавишу F1–F12 для начала и окончания диктовки.",
                "Горячая клавиша диктовки", Messages.getQuestionIcon(), keys,
                current.isBlank() ? "F12" : current);
        if (index < 0) return;
        String key = keys[index];
        String[] conflicts = DictationKeymap.conflicts(key);
        if (conflicts.length > 0) {
            String summary = String.join(", ", Arrays.copyOf(conflicts, Math.min(conflicts.length, 5)));
            int answer = Messages.showYesNoDialog(event.getProject(),
                    "Клавиша " + key + " уже используется в IntelliJ:\n" + summary
                            + "\n\nПереназначить её на диктовку? Прежние назначения именно этой "
                            + "клавиши будут удалены из активной раскладки.",
                    "Конфликт горячей клавиши", Messages.getWarningIcon());
            if (answer != Messages.YES) return;
        }
        DictationKeymap.assign(key);
        if (event.getProject() != null) {
            DictateNotifications.info(event.getProject(), "Диктовка: " + key + " — начало / остановка записи.");
        }
    }
}

