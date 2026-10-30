# IDEA ChatGPT Dictate

An IntelliJ IDEA plugin for **Russian voice dictation into the Codex chat composer** with OpenAI speech recognition and project-aware correction of technical terms.

**Version 0.3.0:** one settings dialog instead of separate configuration commands. The Tools menu now contains just **Start / Stop Dictation** and **Settings** (displayed in Russian).

## Installation on Windows

1. Install JDK 21, setting `JAVA_HOME` to the JDK root directory, not `bin`.
2. Pull the latest repository files. From the repository directory run `gradlew.bat buildPlugin` in Command Prompt, or `.\gradlew.bat buildPlugin` in PowerShell.
3. In IntelliJ IDEA: **Settings → Plugins → ⚙ → Install Plugin from Disk**. Select the ZIP under `build/distributions/`, then restart if prompted.

Alternatively, download a successfully built ZIP from **GitHub Actions → Build IntelliJ Plugin → Artifacts**.

## Controls

Open **Tools → IDEA ChatGPT Dictate**. There are only two commands:

- **Начать / остановить диктовку** — start or stop recording. The configured F-key does the same thing.
- **Настройки…** — a single dialog containing every configuration option.

The settings dialog contains:

| Option | Description |
|---|---|
| **API-ключ OpenAI** | Paste an OpenAI Platform API key. Leave blank to retain the existing key; select the clear checkbox to delete the saved key. Keys are stored in IntelliJ Password Safe. `OPENAI_API_KEY` can also supply a key. |
| **Горячая клавиша** | Choose exactly one of **F1–F12**. Existing keyboard assignments are replaced only after confirmation. |
| **Модель правки текста** | Choose **GPT-6 Luna** (default and cheapest), **GPT-6.1 Terra**, **GPT-6.1 Sol**, or **GPT-6 Astra**. |
| **Контекст диалога** | Optionally select a local Codex conversation (project-matching session). **Не использовать историю Codex** disables history context. |

The speech-recognition model is `gpt-transcribe` and does not change when you select a text-correction model.

### Dictation

1. Open the project and a relevant source file to supply project, PSI/class/method, and caret context.
2. Focus the **Codex chat input**.
3. Press the chosen F key to start recording, speak, then press it again to stop.
4. Wait for transcription and correction. Text is pasted **without pressing Enter or sending a message**.

If focus changed during the OpenAI request, the result is copied to the clipboard. Focus the chat input and press `Ctrl+V` manually. A single recording is limited to 120 seconds and stored in memory as WAV until the user stops it.

## Models and privacy

- **Speech recognition:** `gpt-transcribe` via `/v1/audio/transcriptions` using Russian and English language hints and technical terms from the IDE context.
- **Text correction:** the chosen model via `/v1/responses`, with `store: false`. Reasoning effort is `none` for Luna/Terra and `low` for Sol/Astra, per their supported parameters. It corrects recognizer mistakes without answering the request.
- **History:** only the explicitly chosen Codex conversation is included; the plugin never guesses which session is currently active merely from the last-modified timestamp. History can be disabled in the settings dialog.
- **Fallback:** if text correction fails but transcription succeeds, the original transcript is delivered.

The OpenAI API receives audio and project context **only when dictating**, including optional recent turns of the selected Codex conversation. The complete repository is not uploaded. API requests use your own OpenAI Platform key. ChatGPT Plus does not include the separate OpenAI API billing.

## Development

- Gradle Wrapper **9.1.0**, JDK **21**, IntelliJ Platform Gradle Plugin **2.19.0**.
- Target IntelliJ IDEA **2025.3.3**, compatible with IntelliJ Platform build `253` and newer in the plugin descriptor. Compatibility with newer versions should still be tested on the user's workstation.
- `./gradlew buildPlugin` packages the installable ZIP. GitHub Actions automatically builds and uploads the ZIP on pushes to `main`.

The original 0.1.0 diagnostic actions were used to verify editor PSI, local Codex session discovery, and safe paste behavior. They are now hidden from the menu to keep the user interface simple. Actual microphone and OpenAI integration must be tested on the user's computer.

This plugin is not affiliated with JetBrains or OpenAI.
