package nz.ilinic.ideadictate;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;

final class ApiKeyStore {
    private static final CredentialAttributes ATTRIBUTES =
            new CredentialAttributes("nz.ilinic.ideadictate.openai.api-key");

    private ApiKeyStore() { }

    static String getSaved() {
        return PasswordSafe.getInstance().getPassword(ATTRIBUTES);
    }

    static String get() {
        String configured = getSaved();
        if (configured != null && !configured.isBlank()) return configured.trim();
        String environment = System.getenv("OPENAI_API_KEY");
        return environment == null || environment.isBlank() ? null : environment.trim();
    }

    static void set(String key) {
        PasswordSafe.getInstance().setPassword(ATTRIBUTES, key == null || key.isBlank() ? null : key.trim());
    }
}

