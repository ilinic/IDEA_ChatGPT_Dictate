package nz.ilinic.ideadictate;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One active recording per IDE project. WAV data stays in memory until an explicit transcription request. */
public final class VoiceDictationService implements Disposable {
    private static final Logger LOG = Logger.getInstance(VoiceDictationService.class);
    private static final int MAX_RECORD_SECONDS = 120;
    private static final int MIN_RECORD_MILLIS = 250;

    private final Project project;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean pendingDelivery = new AtomicBoolean(false);
    private volatile boolean cancelled;
    private volatile boolean sendWhenDone;
    private volatile TargetDataLine microphone;
    private volatile Thread activeThread;

    public VoiceDictationService(@NotNull Project project) {
        this.project = project;
        // Escape takes priority only while this project's dictation is active.
        IdeEventQueue.getInstance().addDispatcher(event -> {
            if (!(event instanceof KeyEvent key) || key.getID() != KeyEvent.KEY_PRESSED
                    || key.getKeyCode() != KeyEvent.VK_ESCAPE || (!busy.get() && !pendingDelivery.get())) {
                return false;
            }
            Window frame = WindowManager.getInstance().getFrame(project);
            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (frame == null || !belongsToWindow(activeWindow, frame)) return false;
            cancel();
            return true;
        }, this);
    }

    private static boolean belongsToWindow(Window window, Window owner) {
        for (Window current = window; current != null; current = current.getOwner()) {
            if (current == owner) return true;
        }
        return false;
    }

    public void toggle() {
        if (recording.get()) {
            finish(false);
            return;
        }

        if (pendingDelivery.get() || !busy.compareAndSet(false, true)) {
            DictateNotifications.info(project, I18n.text("recording.wait"));
            return;
        }
        cancelled = false;
        sendWhenDone = false;
        DictateNotifications.stage(project, DictateNotifications.Stage.STARTING);
        // Snapshot the original prompt focus, preventing accidental paste into source code later.
        Component expectedFocus = SafePaste.currentFocus();
        String editor;
        try {
            editor = ReadAction.compute(() -> EditorContext.capture(project));
        } catch (RuntimeException ignored) {
            // PSI context is optional; it must never prevent microphone recording.
            LOG.warn("Source context unavailable; proceeding without it", ignored);
            editor = "";
        }
        String contextSnapshot = editor;
        recording.set(true);
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            activeThread = Thread.currentThread();
            try {
                if (cancelled) return;
                String apiKey = ApiKeyStore.get();
                if (cancelled) return;
                if (apiKey == null) {
                    recording.set(false);
                    DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                    DictateNotifications.warning(project, I18n.text("recording.noKey"));
                    return;
                }
                DictateNotifications.info(project, "🎙 " + I18n.text("recording.start"));
                performCaptureAndTranscription(apiKey, contextSnapshot, expectedFocus);
            } catch (RuntimeException error) {
                if (!cancelled) {
                    LOG.warn("Unexpected error while recording or transcribing", error);
                    DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                    DictateNotifications.error(project, I18n.text("recording.unexpected", safeMessage(error)));
                }
            } finally {
                recording.set(false);
                activeThread = null;
                busy.set(false);
            }
        });
    }

    /** The second function key stops recording and sends the result instead of leaving it in the composer. */
    public void finishAndSend() {
        if (!recording.get()) {
            DictateNotifications.info(project, I18n.text("recording.sendIdle"));
            return;
        }
        finish(true);
    }

    private void finish(boolean submit) {
        if (!recording.get()) return;
        sendWhenDone = submit;
        recording.set(false);
        stopMicrophone();
        DictateNotifications.stage(project, DictateNotifications.Stage.TRANSCRIBING);
        DictateNotifications.info(project, I18n.text(submit ? "recording.stopSend" : "recording.stop"));
    }

    public void cancel() {
        if (!busy.get() && !pendingDelivery.get()) return;
        cancelled = true;
        sendWhenDone = false;
        recording.set(false);
        stopMicrophone();
        Thread running = activeThread;
        if (running != null) running.interrupt();
        DictateNotifications.stage(project, DictateNotifications.Stage.CANCELLED);
        DictateNotifications.info(project, I18n.text("recording.cancelled"));
    }

    private void stopMicrophone() {
        TargetDataLine active = microphone;
        if (active != null) {
            try { active.stop(); } catch (RuntimeException ignored) { }
            active.close();
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
                DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                DictateNotifications.error(project, I18n.text("recording.noMic"));
                return;
            }

            microphone = line;
            if (!recording.get() || cancelled) {
                if (!cancelled) DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                return;
            }
            line.start();
            DictateNotifications.stage(project, DictateNotifications.Stage.RECORDING);
            byte[] buffer = new byte[8192];
            long start = System.nanoTime();
            int maxBytes = MAX_RECORD_SECONDS * (int) format.getSampleRate() * format.getFrameSize();

            while (recording.get() && pcm.size() < maxBytes && !project.isDisposed()) {
                int count = line.read(buffer, 0, Math.min(buffer.length, maxBytes - pcm.size()));
                if (count > 0) pcm.write(buffer, 0, count);
                else if (count < 0) break;
            }
            recording.set(false);
            if (pcm.size() >= maxBytes && !cancelled) {
                DictateNotifications.stage(project, DictateNotifications.Stage.TRANSCRIBING);
                DictateNotifications.info(project, I18n.text("recording.maxTime"));
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (cancelled) return;
            if (durationMs < MIN_RECORD_MILLIS || pcm.size() < format.getFrameSize() * 100) {
                DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                DictateNotifications.warning(project, I18n.text("recording.short"));
                return;
            }
        } catch (RuntimeException error) {
            if (cancelled) return;
            if (recording.get() || pcm.size() == 0) {
                DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
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

        if (cancelled) return;
        try {
            byte[] wav = makeWav(pcm.toByteArray(), format);
            String history = SettingsPrefs.useCodexContext()
                    ? CodexSessionScanner.currentContext(project.getBasePath(), SettingsPrefs.contextTurns()) : "";
            String context = editorContext + (history.isBlank() ? "" : "\nRECENT CODEX CHAT:\n" + history);
            String speechHints = editorContext + (history.isBlank() ? "" : "\nRecent conversation:\n"
                    + history.substring(Math.max(0, history.length() - 1100)));
            OpenAIClient client = new OpenAIClient(apiKey);

            DictateNotifications.stage(project, DictateNotifications.Stage.TRANSCRIBING);
            DictateNotifications.info(project, I18n.text("recording.transcribe"));
            String raw = client.transcribe(wav, speechHints, SpeechLanguages.selectedCodes());
            if (cancelled) return;
            String result = raw;
            try {
                DictateNotifications.stage(project, DictateNotifications.Stage.CORRECTING);
                DictateNotifications.info(project, I18n.text("recording.correct"));
                result = client.correct(raw, context, CorrectionModel.selected());
            } catch (IOException | InterruptedException correctionError) {
                if (correctionError instanceof InterruptedException) Thread.currentThread().interrupt();
                if (!cancelled) DictateNotifications.warning(project, I18n.text("recording.fallback"));
            }
            if (cancelled) return;
            pendingDelivery.set(true);
            DictateNotifications.stage(project, sendWhenDone
                    ? DictateNotifications.Stage.SENDING : DictateNotifications.Stage.INSERTING);
            SafePaste.deliver(project, result, expectedFocus, sendWhenDone, () -> !cancelled,
                    () -> pendingDelivery.set(false));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!cancelled) {
                DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                DictateNotifications.error(project, I18n.text("recording.interrupted"));
            }
        } catch (IOException error) {
            if (!cancelled) {
                DictateNotifications.stage(project, DictateNotifications.Stage.ERROR);
                DictateNotifications.error(project, I18n.text("recording.apiError", safeMessage(error)));
            }
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
        cancelled = true;
        Thread running = activeThread;
        if (running != null) running.interrupt();
        stopMicrophone();
    }
}

