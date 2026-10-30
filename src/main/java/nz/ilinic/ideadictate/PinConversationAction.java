package nz.ilinic.ideadictate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PinConversationAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        new Task.Backgroundable(project, "Finding Codex conversations", true) {
            private List<CodexSessionScanner.Candidate> candidates;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                candidates = CodexSessionScanner.recentMatching(project.getBasePath());
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) return;
                String[] options = new String[candidates.size() + 1];
                options[0] = "[Не использовать историю диалога]";
                for (int i = 0; i < candidates.size(); i++) {
                    options[i + 1] = candidates.get(i).display();
                }
                int selected = Messages.showChooseDialog(project,
                        "Укажи ОТКРЫТЫЙ диалог Codex по последним сообщениям. "
                                + "Время изменения само по себе не определяет активную сессию. "
                                + "Выбранная история будет отправляться в OpenAI только во время диктовки.",
                        "Контекст текущего диалога Codex", Messages.getQuestionIcon(), options, options[0]);
                if (selected < 0) return;
                if (selected == 0) {
                    PinnedConversation.set(project, null);
                    DictateNotifications.info(project, "История чата отключена для диктовки.");
                } else {
                    PinnedConversation.set(project, candidates.get(selected - 1).path());
                    DictateNotifications.info(project, "Контекст выбранного диалога Codex подключён.");
                }
            }
        }.queue();
    }
}

