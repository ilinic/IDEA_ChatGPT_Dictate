# IDEA ChatGPT Dictate

IntelliJ IDEA plugin for **Russian voice dictation into the Codex chat composer**, with OpenAI speech recognition and project-aware correction of technical terminology.

**Version 0.2.0:** voice dictation, configurable F1–F12 hotkey, optional explicit Codex chat context, and the original diagnostics.

## Quick start on Windows

1. Clone or update the repository, then run `.\gradlew.bat buildPlugin` from PowerShell (or `gradlew.bat buildPlugin` from `cmd.exe`).
2. In IntelliJ IDEA open **Settings → Plugins → ⚙ → Install Plugin from Disk** and select the ZIP in `build/distributions/`. Restart if requested.
3. Choose **Tools → IDEA ChatGPT Dictate → Configure OpenAI API Key** and paste your **OpenAI Platform API key**. The key is saved in IntelliJ Password Safe. Alternatively, set the `OPENAI_API_KEY` environment variable before launching IDEA.
4. Choose **Tools → IDEA ChatGPT Dictate → Choose Dictation Key (F1–F12)** and select a single F key. If the key is already assigned to an IDE command, the plugin asks before replacing that assignment.
5. Open your project and a source file, then put the caret inside the **Codex chat input**.
6. Press your chosen F key **once to start recording**, speak in Russian, and press the **same F key to stop**. Wait for the transcription and correction. The text is inserted into the prompt **without sending it**.

When focus moves away from the original prompt during the OpenAI request, the plugin copies the result to the clipboard instead of pasting into another component. Put the caret back into Codex and press `Ctrl+V`.

### Optional: use the current Codex conversation as context

The source directory `~/.codex/sessions` may contain **many unrelated sessions** from the same project. The plugin deliberately does not assume that the newest modified file is the active chat.

Use **Tools → IDEA ChatGPT Dictate → Choose Codex Conversation for Context** to select the matching conversation **by its session ID, timestamp and most recent actual user message**. This choice is stored per project. Only a **manually selected** conversation can contribute recent turns to the correction model. You can select `[Не использовать историю диалога]` at any time. If you change chats, select the new conversation manually.

If you do not select a conversation, dictation still uses the current project name, source file, caret, selection, and available class/method names. History entries with internal approval-assessment instructions are excluded from the extracted user prompts.

## Models and processing

1. **Microphone:** local Java Sound recording of mono PCM to an in-memory WAV file; up to 120 seconds. No temporary audio files are created.
2. **Speech recognition:** `gpt-transcribe` via `POST /v1/audio/transcriptions`, with Russian/English language hints, project-aware keywords and an IDE-context prompt. This is the accuracy-focused stage.
3. **Text correction:** `gpt-6-luna` via `POST /v1/responses`, reasoning effort `none`, and `store: false`. It corrects obvious recognition errors and technical identifiers without changing the user's meaning or responding to the request. If correction fails but transcription worked, the plugin falls back to the original recognized text.
4. **Delivery:** paste into the original focused component when still active; otherwise copy to clipboard for manual insertion. No Enter key is sent.

The second model is intentionally the **low-cost model** because punctuation and terminology correction are simple. Exact model names are constants in `OpenAIClient.java` for easy adjustments.

**Billing:** ChatGPT Plus does not include OpenAI Platform API usage; the two API requests are billed separately to your Platform account. The plugin does not use ChatGPT cookies or credentials.

**Privacy:** When you stop recording, microphone audio and context (project path, selected text preview, PSI names, plus recent turns of a specifically selected conversation, if any) are sent directly to OpenAI. No code repository is uploaded wholesale. The API key is never included in logs or version control. The plugin does not send data in the background when you are not dictating.

## Existing diagnostics

- **Inspect IDE and Codex Context** — shows current file/selection/PSI and discovers local Codex JSONL sessions, including `~/.codex/sessions`.
- **Insert Test Marker at Caret** — `Ctrl+Alt+Shift+M` to paste `[IDEA_DICTATE_PASTE_TEST]` for verifying the composer without API calls. This is only a diagnostic shortcut; the main dictation action uses your selected F1–F12 key.

The diagnostics list all project-matching sessions but **do not prove which chat is active**. A verified project directory is only a useful candidate filter.

## Build requirements

- Gradle Wrapper **9.1.0** included (`.\gradlew.bat`).
- Java source target **JDK 21**. Foojay toolchain resolver can download JDK 21 automatically if Gradle is already running on Java 17+ and has network access.
- IntelliJ Platform Gradle Plugin **2.19.0** and target IntelliJ IDEA **2025.3.3**, supporting build `253` and newer in the plugin descriptor. Newer versions still require practical compatibility testing.
- The ZIP appears under `build/distributions/` after `buildPlugin`.

**Status:** The original 0.1.0 diagnostics were built and confirmed working on Windows with IntelliJ IDEA 2026.2. Version 0.2.0 is new functionality that needs a local build and microphone/API integration test. An OpenAI API key and microphone access are required.

This plugin is not affiliated with JetBrains or OpenAI.

