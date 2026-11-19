# JARVIS Alpha

A self-contained native Android AI agent foundation built with Kotlin and Jetpack Compose.

## Project principles

- Native Android APK; no separate backend.
- JARVIS-only user-facing identity.
- API credentials configured by the user, never embedded in source or APK.
- Local conversations, settings, memory, and task state.
- AI inference through configured external providers only.

## Current status

**Phase 1 foundation is committed.** The repository currently contains the Android project scaffold, Compose command-center UI, dependency baseline, and GitHub Actions workflow. The gateway, real chat, memory, tools, agent loop, task engine, and Telegram integration are planned implementation phases and are not yet functional.

See [PROJECT_STATUS.md](PROJECT_STATUS.md) for the detailed status.

## Build requirements

- JDK 17
- Android SDK Platform 35
- Gradle 8.9 compatible with Android Gradle Plugin 8.7.3

Build commands:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

The APK output is:

`app/build/outputs/apk/debug/app-debug.apk`

## Security

No API keys or bot tokens are included. Future credential configuration will be stored with Android Keystore-backed encryption. Never commit credentials.

## Roadmap

See [PROJECT_STATUS.md](PROJECT_STATUS.md).
