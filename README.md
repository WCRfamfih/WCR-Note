# AI Note Completion

Android AI writing note MVP.

## Implemented

- Kotlin + Jetpack Compose + Material 3 project structure
- MVVM layers with `data`, `domain`, `ui`, and `di`
- Room local note database
- DataStore user settings
- Note list, create, edit, delete, search, and recent-edit sorting
- Editor auto-save after 800 ms, title extraction, and character count
- System dark mode support
- Fake AI completion card; tap to insert at cursor
- Editor AI action menu: continue writing, expand, make formal, make concise, turn into todos, summarize, generate title
- Manual AI result card with accept and dismiss
- Selected text replacement for expand, rewrite, and todo actions
- Settings for API Provider, API Key, API Base URL, Model, auto completion, delay, max completion length, and context range
- Provider presets for Fake, OpenAI, DeepSeek, and Qwen
- OpenAI-compatible Chat Completions API support
- Fake service fallback when Provider is `Fake` or API Key is empty
- API connection test in settings
- Human-readable API errors for common network, auth, billing, rate limit, and server failures
- Real API auto-completion throttling to reduce accidental rapid requests
- In-editor ghost text for completion when the cursor is at the end of a line
- Completion card fallback when ghost text cannot be displayed safely

## Run

Open the project root in Android Studio, sync Gradle, then run the `app` configuration on an emulator or Android device.

## Current API Setup

Use `Fake` as API Provider to keep everything local.

For a real OpenAI-compatible provider:

- API Provider: any non-`Fake` value, for example `OpenAI`
- API Base URL: provider chat completions endpoint, for example `https://api.openai.com/v1/chat/completions`
- Model: the model name supported by that provider
- API Key: your provider API key

You can also tap a provider preset in Settings. Presets update Provider, Base URL, and Model without changing your API Key.

## Next Priorities

1. Improve ghost text positioning when there is text after the cursor on the same line.
2. Add an in-editor error/status surface for manual AI actions.
3. Add per-provider help text and API key links.
