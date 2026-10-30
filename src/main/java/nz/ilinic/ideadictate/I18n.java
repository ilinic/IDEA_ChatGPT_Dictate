package nz.ilinic.ideadictate;

import com.intellij.DynamicBundle;

import java.util.Locale;
import java.util.ResourceBundle;

/** Use the IDE UI locale, which may differ from the Windows system locale. */
final class I18n {
    private static final String BUNDLE = "messages.DictateBundle";

    private I18n() { }

    static Locale locale() {
        return DynamicBundle.getLocale();
    }

    static String text(String key, Object... values) {
        String pattern = ResourceBundle.getBundle(BUNDLE, locale()).getString(key);
        return values.length == 0 ? pattern : String.format(locale(), pattern, values);
    }
}
