package nz.ilinic.ideadictate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One active recording per IDE project. WAV data stays in memory until an explicit transcription request. */
public final class VoiceDictationService implements Disposable {
    private static final int MAX_RECORD_SECONDS = 120;
    private static final int MIN_RECORD_MILLIS = 250;

    private final Project project;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private volatile TargetDataLine microphone;

    public VoiceDictationService(@NotNull Project project) {
        this.project = project;
    }

    public void toggle() {
        if (recording.get()) {
            recording.set(false);
            TargetDataLine active = microphone;
            if (active != null) {
                try { active.stop(); } catch (RuntimeException ignored) { }
                active.close();
            }
            DictateNotifications.info(project, I18n.text("recording.stop"));
            return;
        }

        if (!busy.compareAndSet(false, true)) {
            DictateNotifications.info(project, I18n.text("recording.wait"));
            return;
        }
        // Snapshot the original prompt focus, preventing accidental paste into source code later.
        Component expectedFocus = SafePaste.currentFocus();
        String editor = ReadAction.compute(() -> EditorContext.capture(project));
        recording.set(true);
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            String apiKey = ApiKeyStore.get();
            if (apiKey == null) {
                recording.set(false);
                busy.set(false);
                DictateNotifications.warning(project, I18n.text("recording.noKey"));
                return;
            }
            if (!recording.get()) {
                busy.set(false);
                return;
            }
            DictateNotifications.info(project, "🎙 " + I18n.text("recording.start"));
            captureAndTranscribe(apiKey, editor, expectedFocus);
        });
    }

    private void captureAndTranscribe(String apiKey, String editorContext, Component expectedFocus) {
        try {
            performCaptureAndTranscription(apiKey, editorContext, expectedFocus);
        } finally {
            busy.set(false);
        }
    }

    private void performCaptureAndTranscription(String apiKey, String editorContext, Component expectedFocus) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        TargetDataLine line = null;
        AudioFormat format = null;
        try {
            for (int sampleRate : new int[] { 16000, 48000, 44100 }) {
                AudioFormat candidate = new AudioFormat(sampleRate, 16, 1, true, false);
                try {
                    line = AudioSystem.getTargetDataLine(candidate);
                    line.open(candidate, sampleRate / 2);
                    format = candidate;
                    break;
                } catch (LineUnavailableException | IllegalArgumentException unsupported) {
                    if (line != null) line.close();
                    line = null;
                }
            }
            if (line == null || format == null) {
                DictateNotifications.error(project, I18n.text("recording.noMic"));
                return;
            }

            microphone = line;
            if (!recording.get()) return;
            line.start();
            byte[] buffer = new byte[8192];
            long start = System.nanoTime();
            int maxBytes = MAX_RECORD_SECONDS * (int) format.getSampleRate() * format.getFrameSize();

            while (recording.get() && pcm.size() < maxBytes && !project.isDisposed()) {
                int count = line.read(buffer, 0, Math.min(buffer.length, maxBytes - pcm.size()));
                if (count > 0) pcm.write(buffer, 0, count);
                else if (count < 0) break;
            }
            recording.set(false);
            if (pcm.size() >= maxBytes) {
                DictateNotifications.info(project, I18n.text("recording.maxTime"));
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (durationMs < MIN_RECORD_MILLIS || pcm.size() < format.getFrameSize() * 100) {
                DictateNotifications.warning(project, I18n.text("recording.short"));
                return;
            }
        } catch (RuntimeException error) {
            if (recording.get() || pcm.size() == 0) {
                DictateNotifications.error(project, I18n.text("recording.error", safeMessage(error)));
                return;
            }
            // Some Windows audio drivers throw when stop() closes a blocking read().
        } finally {
            recording.set(false);
            microphone = null;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) { }
                line.close();
            }
        }

        try {
            byte[] wav = makeWav(pcm.toByteArray(), format);
            String history = SettingsPrefs.useCodexContext()
                    ? CodexSessionScanner.currentContext(project.getBasePath(), SettingsPrefs.contextTurns()) : "";
            String context = editorContext + (history.isBlank() ? "" : "\nRECENT CODEX CHAT:\n" + history);
            String speechHints = editorContext + (history.isBlank() ? "" : "\nRecent conversation:\n"
                    + history.substring(Math.max(0, history.length() - 1100)));
            OpenAIClient client = new OpenAIClient(apiKey);

            DictateNotifications.info(project, I18n.text("recording.transcribe"));
            String raw = client.transcribe(wav, speechHints, SpeechLanguages.selectedCodes());
            String result = raw;
            try {
                DictateNotifications.info(project, I18n.text("recording.correct"));
                result = client.correct(raw, context, CorrectionModel.selected());
            } catch (IOException | InterruptedException correctionError) {
                if (correctionError instanceof InterruptedException) Thread.currentThread().interrupt();
                DictateNotifications.warning(project, I18n.text("recording.fallback"));
            }
            SafePaste.deliver(project, result, expectedFocus);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            DictateNotifications.error(project, I18n.text("recording.interrupted"));
        } catch (IOException error) {
            DictateNotifications.error(project, I18n.text("recording.apiError", safeMessage(error)));
        }
    }

    private static byte[] makeWav(byte[] pcm, AudioFormat format) throws IOException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(pcm);
             AudioInputStream audio = new AudioInputStream(bytes, format, pcm.length / format.getFrameSize());
             ByteArrayOutputStream wav = new ByteArrayOutputStream(pcm.length + 64)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav);
            return wav.toByteArray();
        }
    }

    private static String safeMessage(Exception error) {
        String text = error.getMessage();
        return text == null ? error.getClass().getSimpleName() : text.substring(0, Math.min(text.length(), 240));
    }

    @Override
    public void dispose() {
        recording.set(false);
        TargetDataLine line = microphone;
        if (line != null) line.close();
    }
}

