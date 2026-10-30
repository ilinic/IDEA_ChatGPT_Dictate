# IDEA ChatGPT Dictate

[![Build IntelliJ Plugin](https://github.com/ilinic/IDEA_ChatGPT_Dictate/actions/workflows/build.yml/badge.svg)](https://github.com/ilinic/IDEA_ChatGPT_Dictate/actions/workflows/build.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-253%2B-blue)

**Voice dictation for Codex inside IntelliJ IDEA — optimized for Russian speech and programming terminology.**

IDEA ChatGPT Dictate is a lightweight IntelliJ IDEA plugin that turns spoken Russian into an editable prompt in the **Codex chat input**. It uses OpenAI speech-to-text, then optionally corrects transcription mistakes using the source code context from your current IDE project. The plugin **never presses Enter or sends the chat message for you**.

[Русская версия README](README.ru.md) · [Download a build](https://github.com/ilinic/IDEA_ChatGPT_Dictate/actions/workflows/build.yml) · [Source code](https://github.com/ilinic/IDEA_ChatGPT_Dictate)

## Why use it?

- **Speak naturally while coding.** Dictate questions, feature requests and bug reports in Russian, mixing in English API names, classes and libraries.
- **Context-aware technical correction.** The correction step can see the project name, open file, caret position, Java class/method and a short selection preview.
- **Optional Codex conversation context.** Explicitly choose a local Codex session to help resolve project terminology; the plugin never guesses which chat is active.
- **One configurable F1–F12 key.** Press once to start recording, once more to stop.
- **One settings window.** API key, keyboard shortcut, correction model and Codex conversation are configured together.
- **Safe insertion.** When the original input loses focus, transcription goes to the clipboard instead of being pasted into source code.

## Quick start

### 1. Install the plugin

**Download the ready-made ZIP:**

1. Open [Build IntelliJ Plugin — GitHub Actions](https://github.com/ilinic/IDEA_ChatGPT_Dictate/actions/workflows/build.yml).
2. Open the latest **successful** workflow run and download the `idea-chatgpt-dictate` artifact.
3. Extract the downloaded artifact archive. It contains the actual installable plugin ZIP.
4. In IntelliJ IDEA go to **Settings → Plugins → ⚙ → Install Plugin from Disk**, select the plugin ZIP and restart IDEA if prompted.

Or build it yourself with **JDK 21**:

```powershell
git clone https://github.com/ilinic/IDEA_ChatGPT_Dictate.git
cd IDEA_ChatGPT_Dictate
.\gradlew.bat buildPlugin
```

The installable ZIP is produced in `build/distributions/`. Set `JAVA_HOME` to the JDK directory (not the `bin` subdirectory).

### 2. Configure the plugin

Open **Tools → IDEA ChatGPT Dictate → Настройки…**.

| Setting | What it does |
| --- | --- |
| **API-ключ OpenAI** | OpenAI Platform API key, saved using IntelliJ Password Safe. Leave blank to keep the existing key. |
| **Горячая клавиша** | Choose one key from **F1–F12**. If another IDE command already uses it, you are asked before replacing that shortcut. |
| **Модель правки текста** | Choose the model used to fix recognized text. **GPT-6 Luna** is the default low-cost option. |
| **Контекст диалога** | Optionally select a Codex conversation from local, project-matching sessions, or disable history entirely. |

Available correction models: `gpt-6-luna` (default), `gpt-6.1-terra`, `gpt-6.1-sol` and `gpt-6-astra`. Speech recognition uses `gpt-transcribe` independently of this selection.

**Note:** The OpenAI Platform API is billed separately from ChatGPT Plus. A ChatGPT subscription is not an API key.

### 3. Dictate into Codex

1. Open your project and, optionally, a relevant source file.
2. Place the cursor in the **Codex chat composer**.
3. Press your chosen F key to **start** speaking.
4. Press the same key to **stop**; wait for transcription and correction.
5. Review or edit the inserted text, then send it yourself when ready.

You can also use **Tools → IDEA ChatGPT Dictate → Начать / остановить диктовку**. Each recording is limited to **120 seconds**.

## How it works

```mermaid
flowchart LR
    Mic["Microphone"] --> STT["OpenAI speech-to-text"]
    STT --> Fix["Text correction"]
    IDE["Editor and optional Codex context"] --> Fix
    Fix --> Paste["Codex input or clipboard"]
```

1. Java Sound captures mono audio into an in-memory WAV file.
2. `gpt-transcribe` recognizes Russian speech, with English and project-specific technical hints.
3. The selected GPT text model corrects obvious transcription errors, punctuation and identifiers. It is instructed **not** to answer or expand the prompt.
4. The result is pasted into the original focused component. If focus changed, the plugin puts the text on the clipboard and asks you to paste it manually.

If correction fails while speech recognition succeeded, the **raw transcript** is retained rather than discarded.

## Privacy and permissions

- **No background recording.** Microphone capture begins only when explicitly activated.
- **No temporary audio files.** Audio is buffered in memory and sent to OpenAI only for transcription.
- **Limited code context.** The plugin may send the current project path, source-file location, PSI class/method information and a short selection preview, **not the whole repository**.
- **Codex history is opt-in.** Only a conversation selected in Settings may contribute recent messages to the correction request. You can turn this off at any time.
- **No automatic chat submission.** The plugin does not press Enter.
- **Key storage.** The API key is stored in IntelliJ Password Safe; an `OPENAI_API_KEY` environment variable can be used as a fallback. The key is not committed to Git.
- **API setting.** Text-correction requests use `store: false`. OpenAI's separate API data handling policies still apply.

## Compatibility and development

| Component | Requirement |
| --- | --- |
| IDE | IntelliJ IDEA, platform build **253+** (target **2025.3.3**) |
| Build JDK | Java **21** |
| Build system | Gradle Wrapper **9.1.0** |
| Language | Java, IntelliJ Platform SDK |
| Operating system | Developed and exercised on **Windows**; other platforms are not yet verified |
| API | OpenAI Platform key and network access |

The core dictation workflow has been tested with IntelliJ IDEA 2026.2 on Windows. Newer plugin changes still warrant an installation check.

```bash
./gradlew buildPlugin
```

GitHub Actions builds and uploads an installable ZIP on every push to `main`. Source files are under `src/main/java/nz/ilinic/ideadictate/`.

### Troubleshooting

- **No microphone input:** check the default Windows recording device and microphone privacy permissions for IntelliJ IDEA.
- **No transcription:** configure an OpenAI Platform API key and check API billing and network access.
- **The text appeared only on the clipboard:** put the cursor back into the Codex prompt and press `Ctrl+V`. The focus guard prevents accidental insertion into another editor.
- **F-key conflicts:** choose another F1–F12 key in Settings; IntelliJ warns before replacing a conflicting binding.
- **Wrong project terms:** open the relevant source file and optionally choose the correct Codex session in Settings.

## Project status

This is a working, independently developed IntelliJ IDEA plugin, not an official JetBrains or OpenAI product. Bug reports and reproducible issues are welcome via [GitHub Issues](https://github.com/ilinic/IDEA_ChatGPT_Dictate/issues).
