# IDEA ChatGPT Dictate

An experimental IntelliJ IDEA plugin for **high-quality, project-aware OpenAI dictation into the Codex / JetBrains AI chat composer**.

**Current version: `0.1.0` — feasibility diagnostic only.** No microphone recording or OpenAI API calls are included yet. We first need to verify which integration points work in the actual IDE/composer, particularly whether the currently active Codex conversation can be identified.

## What this probe does

1. **IDE context** — detects the open project, current source file, caret, selected text preview, enclosing PSI names, and Java class/method.
2. **Codex history** — searches recent local Codex JSONL session files in IntelliJ's local data directory and in `~/.codex/sessions`. Lists session IDs, working directories, timestamps and short previews of the most recent user request. Highlights sessions associated with the current project.
3. **Composer insertion** — copies a harmless test marker to the clipboard and simulates Paste into whichever input currently has keyboard focus, including the Codex composer when it accepts OS paste.

The scanner does **not** reliably identify the active IDE chat yet. A matching project directory is evidence of relevance, not of which chat is open. JetBrains AI Assistant does not publish a stable plugin API for current conversation access. The diagnostic intentionally makes no claim to bypass this limitation.

No data leaves your computer. The plugin never submits a chat message and does not modify source files except if you deliberately run the paste test while a code editor has focus.

## Build in IntelliJ IDEA (Windows)

- Open the cloned repository as a Gradle project.
- Use **JDK 21** for the Gradle build; IntelliJ can download the requested Java toolchain. The included Gradle Wrapper downloads **Gradle 9.1.0** on first use. The target IDE is IntelliJ IDEA **2025.3.3**, and the plugin declares compatibility with build `253` and newer (actual compatibility with newer releases still needs testing).
- In the Gradle tool window, run `Tasks > intellij platform > buildPlugin`, or execute `gradlew.bat buildPlugin` from Windows Command Prompt.
- Install the ZIP in `build/distributions/` using **Settings → Plugins → ⚙ → Install Plugin from Disk**. Alternatively, run `gradle runIde` to launch a development IDE.

> The first build downloads Gradle, the IntelliJ SDK and plugin dependencies, so it requires an Internet connection and can take several minutes.

## The three tests

### Test 1 — IDE context

1. Open a source file in the project, ideally put the caret inside a method. Optionally select a fragment.
2. Choose **Tools → IDEA ChatGPT Dictate → Inspect IDE and Codex Context**.
3. Confirm the reported project, file, caret position, selection and class/method or PSI names are correct.

### Test 2 — Codex session history

1. Open a Codex chat in IntelliJ and type a **distinct, non-sensitive** phrase such as `dictation-history-probe-123` (you can submit it to the chat for a real history record).
2. Re-run **Inspect IDE and Codex Context**.
3. Look for a recent local session with the right project directory and phrase. Check whether opening a different chat changes which file is updated.
4. Please share the **counts and file paths** and whether the phrase was found; redact prompts and private paths if needed.

The result tells us whether local history can provide usable context, and whether an active-session mapping is even plausible. It does not guarantee a mapping.

### Test 3 — Paste into the chat composer

1. Click inside the **Codex prompt input**, making sure the text cursor is visible.
2. Press **Ctrl+Alt+Shift+M**. The plugin will try to paste `[IDEA_DICTATE_PASTE_TEST] ` into the prompt. **It does not press Enter.**
3. Confirm the marker appears **in the chat prompt**, and not in the source code editor. Delete the marker afterwards.
4. If the shortcut conflicts with something else, change it under **Settings → Keymap → Insert Test Marker at Caret**. The same command is in the Tools menu, but using a keyboard shortcut preserves focus more reliably.

Note: This test temporarily replaces the system clipboard contents. On Windows, automated keyboard input may be blocked by OS accessibility/security policies; if so, the marker remains available to paste manually.

## Next step (after test feedback)

If insertion works, implement `Push-to-talk → OpenAI speech-to-text → context-aware correction with GPT → paste into composer` using the plugin's editor/PSI context and whatever accessible chat-history context the diagnostic confirms. The precise speech model and context-conditioning route will be selected based on current OpenAI API capabilities. ChatGPT Plus and OpenAI API billing are distinct.

## Status / constraints

- Source written for IntelliJ Platform SDK and Gradle IntelliJ Platform Plugin `2.19.0`.
- Current build has **not yet been compiled or tested inside the user's IDE**. The purpose of this version is to perform those tests.
- The Codex JSONL locations are exploratory and may vary across IntelliJ and Codex versions; failure to find a file is an informative diagnostic outcome, not proof there is no history.
- The plugin is not affiliated with JetBrains or OpenAI.

