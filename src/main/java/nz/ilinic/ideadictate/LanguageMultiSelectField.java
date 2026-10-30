package nz.ilinic.ideadictate;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Compact language selector built from standard IntelliJ popup and checkbox components. */
final class LanguageMultiSelectField extends JButton {
    private final Set<SpeechLanguages.Language> selected = new LinkedHashSet<>();

    LanguageMultiSelectField() {
        selected.addAll(SpeechLanguages.selected());
        setHorizontalAlignment(SwingConstants.LEFT);
        setMargin(JBUI.insets(5, 10));
        updateLabel();
        addActionListener(event -> openPopup());
    }

    List<SpeechLanguages.Language> getSelectedLanguages() {
        return new ArrayList<>(selected);
    }

    private void updateLabel() {
        List<String> names = selected.stream().map(SpeechLanguages.Language::toString).toList();
        String summary = names.isEmpty() ? I18n.text("settings.languages.auto")
                : names.stream().limit(2).collect(Collectors.joining(", "));
        if (names.size() > 2) summary += " +" + (names.size() - 2);
        setText(summary + "  \u25be");
        setToolTipText(names.isEmpty() ? null : String.join(", ", names));
        getAccessibleContext().setAccessibleDescription(I18n.text("settings.languages"));
    }

    private void openPopup() {
        JPanel choices = new JPanel();
        choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
        choices.setBorder(JBUI.Borders.empty(6, 8));
        JBCheckBox first = null;
        for (SpeechLanguages.Language language : SpeechLanguages.Language.values()) {
            JBCheckBox option = new JBCheckBox(language.toString(), selected.contains(language));
            option.setAlignmentX(LEFT_ALIGNMENT);
            option.addActionListener(event -> {
                if (option.isSelected()) selected.add(language);
                else selected.remove(language);
                updateLabel();
            });
            choices.add(option);
            if (first == null) first = option;
        }

        JBScrollPane scroll = new JBScrollPane(choices);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setPreferredSize(new Dimension(Math.max(getWidth(), 300), 282));
        JBPopupFactory.getInstance().createComponentPopupBuilder(scroll, first)
                .setRequestFocus(true)
                .setFocusable(true)
                .setCancelOnClickOutside(true)
                .setCancelOnWindowDeactivation(true)
                .createPopup()
                .showUnderneathOf(this);
    }
}
