# AI Note Completion

## 中文使用说明

`WCR笔记` 是一个带 AI 自动补全能力的安卓笔记应用，适合用来记录笔记、整理知识卡片，并在编辑过程中调用 AI 进行续写、总结、改写和知识提炼。

首次使用时可以按这个顺序快速体验：

1. 用 Android Studio 打开项目并运行 `app`。
2. 进入设置，若只想本地体验，保持 `API Provider = Fake` 即可，不需要填 API Key。
3. 在主界面新建一篇笔记，进入编辑页后直接输入内容。
4. 停止输入片刻后，可触发自动补全；也可以通过右上角 `AI操作` 或键盘上方工具栏手动调用 AI。
5. 如果你要管理知识内容，可以在“知识库”中创建知识卡片，并在笔记中配合知识识别、提取/更新到知识库等功能使用。

项目目前主要包含这些能力：

- 笔记 / 知识双内容流
- AI 自动补全与手动 AI 操作
- 知识库识别、知识作用域与知识提炼
- 分页编辑、导出图片、封面卡片、主题与字体等显示设置
- 本地优先的数据存储，支持 `Fake` 模式离线体验交互流程

如果要接入真实模型服务，请在设置里填入兼容 OpenAI Chat Completions 的接口地址、模型名和 API Key。

Android AI writing note MVP.

## Implemented

- Kotlin + Jetpack Compose + Material 3 project structure
- MVVM layers with `data`, `domain`, `ui`, and `di`
- Room local note database
- DataStore user settings
- Note list, create, edit, delete, search, and recent-edit sorting
- Folder-style note management with folder filters, folder creation, and card-based note browsing
- Folder rename/delete management, long-press note actions, and configurable note sorting
- Editor auto-save after 800 ms, title extraction, and character count
- System dark mode support
- Fake AI completion card; tap to insert at cursor
- Editor AI action menu: continue writing, expand, make formal, make concise, turn into todos, summarize, generate title
- Manual AI result card with accept and dismiss
- Manual AI results can be inserted/replaced or copied to clipboard
- Manual AI error/status card that cannot be inserted into the note by mistake
- Failed manual AI actions can be retried from the status card
- Selected text replacement for expand, rewrite, and todo actions
- Settings for API Provider, API Key, API Base URL, Model, auto completion, delay, max completion length, and context range
- AI settings are grouped under a dedicated submenu
- Theme mode setting: light, dark, or system
- Accent color presets for primary controls and highlights
- Editor text size setting
- Optional Chinese-first automatic completion gate
- Provider presets for Fake, OpenAI, DeepSeek, and Qwen
- OpenAI-compatible Chat Completions API support
- Fake service fallback when Provider is `Fake` or API Key is empty
- API connection test in settings
- Human-readable API errors for common network, auth, billing, rate limit, and server failures
- Real API auto-completion throttling to reduce accidental rapid requests
- In-editor ghost text for completion when the cursor is at the end of a line
- Floating ghost-text controls for accept, dismiss, and retry
- Ghost text and floating controls stay within editor bounds near the right edge
- Completion card fallback when ghost text cannot be displayed safely
- Markdown document assist toolbar above the keyboard for indent, headings, bold, italic, strikethrough, and underline

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
2. Add per-provider help text and API key links.
3. Add per-action result editing before insertion.
