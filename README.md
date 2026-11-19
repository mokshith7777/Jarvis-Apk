# JARVIS Alpha — Self-Contained Native Android AI Agent

JARVIS Alpha is a 100% native Android AI agent built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room + FTS4**, **WorkManager**, and **Android Keystore AES-256-GCM encryption**. Everything except external AI inference runs directly on your Android device inside a single APK—no VPS, no Docker, no PC companion, no Termux dependency, and no external backend.

## Architecture Overview

Package root: `com.mokshith.jarvis.ai`

- `app/` — `JarvisApplication` and manual DI container (`AppContainer`)
- `core/` — Dispatchers, JSON serializer, time formatters
- `security/` — Android Keystore AES-256-GCM `CryptoEngine`, `KeystoreCredentialVault`, `CredentialRedactor`, `PromptInjectionGuard`, `PermissionPolicyManager`
- `gateway/` — On-device Multi-Provider AI Gateway (Groq, OpenRouter, Google AI, Mistral, DeepSeek, Custom OpenAI-compatible) with SSE streaming, `/models` discovery, exponential backoff, automatic failover, and local telemetry
- `memory/` — Room database (`JarvisDatabase`) with 12 entities, `@Fts4` full-text memory search, relevance scoring (`MemoryRetrievalPipeline`), and DataStore preferences
- `tools/` — Native `ToolRegistry` with Web Research (Jsoup), Persistent Memory, Local Files (Android SAF), GitHub REST API v3, Task Manager, and Device Context
- `brain/` — Autonomous multi-step `AgentEngine` (`OBJECTIVE → CONTEXT → PLAN → TOOL → OBSERVATION → REPLAN → COMPLETION`) and `SkillManager`
- `tasks/` & `workers/` & `services/` — Persistent `TaskEngine` backed by Room + WorkManager + foreground service strictly during active execution
- `channels/` — `CommunicationHub` and `TelegramChannelConnector` (direct Bot API `getUpdates` long polling, persisted offsets, strict authorized user ID allowlist)
- `extensions/` — Future-ready interfaces (`VoiceModule`, `VisionModule`, `AccessibilityModule`, `LocalModelModule`, `AndroidDeviceAutomationModule`) with zero fake stubs
- `ui/` — OLED black + cyan/crimson HUD Jetpack Compose UI across 8 screens: Home, Chat, Agent Tasks, Memory & Skills, Models & Gateway, Channels, Activity Log, and Settings

## Build Instructions

### Prerequisites
- JDK 17
- Android SDK 35 (Min SDK 26)
- Gradle 8.9 (or Android Studio Ladybug / newer)

### Build via GitHub Actions (Recommended)
1. Push this repository to GitHub (`main` branch).
2. Open the **Actions** tab → **Build JARVIS Alpha APK**.
3. Wait for `testDebugUnitTest`, `lintDebug`, and `assembleDebug` to finish.
4. Download `JARVIS-Alpha-debug-apk` from the workflow Artifacts and install on Android (enable *Install unknown apps*).

### Build Locally
```bash
gradle testDebugUnitTest
gradle assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Provider Configuration (Groq & Fallbacks)

No API keys are embedded in the APK. Configure your own keys in **Gateway**:
1. **Groq (Recommended Primary)**: Select **Groq**, enter your `gsk_...` API key, set Priority `1`, tap **Save Provider**, then tap **Test & Discover** to fetch live models.
2. **Optional Fallback Providers**: Add **Google AI (Gemini)**, **OpenRouter**, **Mistral**, **DeepSeek**, or any **Custom OpenAI-Compatible** endpoint with Priority `2`, `3`, etc. If the primary provider hits HTTP 429, timeout, or 5xx, JARVIS retries with backoff and fails over automatically.

## Telegram Setup

1. Create a bot via `@BotFather` and copy the bot token.
2. Find your numeric Telegram user ID (e.g., via `@userinfobot`).
3. Open **Channels** in JARVIS, paste the bot token (stored encrypted in Android Keystore), enter your numeric Telegram user ID(s) comma-separated, toggle **Explicitly Enable Telegram Channel**, tap **Save**, and tap **Start Service**.
4. Unauthorized Telegram users are rejected automatically (empty allowlist blocks all users by default).

## GitHub Integration

- Public repositories work without a token (subject to GitHub's unauthenticated rate limit).
- For higher limits or private repos, add a minimum-scope GitHub Personal Access Token in **Settings → GitHub Integration**.

## Security

- All API keys, Telegram bot tokens, and GitHub PATs are encrypted via Android Keystore AES-256-GCM and never written to plaintext, logs, or AI prompts.
- External webpage text, SAF file contents, GitHub files, and Telegram inputs are wrapped in `<untrusted_external_data>` with prompt-injection detection.
- Every tool can be individually enabled/disabled or gated behind explicit user approval in **Settings → Tool Permissions**.

## Android Background Limitations

Android aggressively restricts background processes under Doze mode, OEM battery optimizers, and user force-stops.
- **WorkManager** tasks survive app restarts and run when battery/network constraints are met.
- **Telegram polling** runs in a visible foreground service only while explicitly started; if Android force-stops the app or kills the service under extreme memory/battery pressure, polling stops until you reopen JARVIS.
