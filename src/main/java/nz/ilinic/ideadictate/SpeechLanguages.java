package nz.ilinic.ideadictate;

import com.intellij.ide.util.PropertiesComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Multi-language recognition hints; an empty list lets the model detect speech language. */
final class SpeechLanguages {
    private static final String KEY = "nz.ilinic.ideadictate.recognitionLanguages";

    enum Language {
        RUSSIAN("ru", "Russian", "Русский"),
        ENGLISH("en", "English", "Английский"),
        GERMAN("de", "German", "Немецкий"),
        FRENCH("fr", "French", "Французский"),
        SPANISH("es", "Spanish", "Испанский"),
        ITALIAN("it", "Italian", "Итальянский"),
        PORTUGUESE("pt", "Portuguese", "Португальский"),
        UKRAINIAN("uk", "Ukrainian", "Украинский"),
        POLISH("pl", "Polish", "Польский"),
        CHINESE("zh", "Chinese", "Китайский"),
        JAPANESE("ja", "Japanese", "Японский"),
        KOREAN("ko", "Korean", "Корейский"),
        HINDI("hi", "Hindi", "Хинди"),
        ARABIC("ar", "Arabic", "Арабский");

        final String code;
        private final String english;
        private final String russian;

        Language(String code, String english, String russian) {
            this.code = code;
            this.english = english;
            this.russian = russian;
        }

        @Override
        public String toString() {
            return I18n.locale().getLanguage().equals("ru") ? russian : english;
        }
    }

    private SpeechLanguages() { }

    static List<Language> selected() {
        String saved = PropertiesComponent.getInstance().getValue(KEY, "ru,en");
        Set<String> codes = Arrays.stream(saved.split(","))
                .map(String::trim).collect(Collectors.toSet());
        List<Language> result = new ArrayList<>();
        for (Language language : Language.values()) if (codes.contains(language.code)) result.add(language);
        return result;
    }

    static List<String> selectedCodes() {
        return selected().stream().map(language -> language.code).toList();
    }

    static void save(List<Language> languages) {
        PropertiesComponent.getInstance().setValue(KEY,
                languages.stream().map(language -> language.code).collect(Collectors.joining(",")));
    }
}
