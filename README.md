# PhoneAgent

> **APK-only distribution. Sideload via ADB or direct APK install.** This app requests permissions (accessibility service, overlay, SMS, phone) that Google Play does not approve for general-purpose apps. This is intentional — it needs these permissions to function as a phone-native AI agent.

PhoneAgent is a native Android Kotlin app that acts as a phone-native AI agent shell. It runs entirely on the user's phone with no VPS, no server backend, and no local model inference in Phase 1. External APIs only provide model completions.

## Core Principle

The phone owns the agent runtime, UI, memory, tools, routing, settings, and screen context. External APIs only provide model completions.

## Target Stack

- Kotlin
- Jetpack Compose for app screens
- Foreground Service for always-on assistant
- WindowManager overlay with TYPE_APPLICATION_OVERLAY
- OkHttp for networking and SSE streaming
- Kotlin Coroutines + Flow
- Room for task history and memories
- DataStore for settings
- Android Keystore for API-key protection
- ML Kit for on-device OCR
- Clean modular architecture

## Why These Permissions

| Permission | Why | Safety |
|-----------|-----|--------|
| **SYSTEM_ALERT_WINDOW** | Floating bubble for quick agent access from any screen | Toggle on/off in-app |
| **BIND_ACCESSIBILITY_SERVICE** | Read screen content and tap/type on user's behalf | Must enable manually in Settings; banking/payment/password apps are denylisted |
| **FOREGROUND_SERVICE** | Keep overlay and agent running | Notification shown while running |
| **QUERY_ALL_PACKAGES** | List installed apps for the "open app" tool | Read-only |
| **SEND_SMS** | Send SMS on user's behalf | Confirmation dialog before every send |
| **CALL_PHONE** | Open dialer (does not auto-call) | Confirmation dialog before every call |
| **RECORD_AUDIO** | Voice input for agent commands | Mic button only; not always-on |
| **POST_NOTIFICATIONS** | Required for foreground service notification on Android 13+ | System requirement |

## Security

- **API keys**: encrypted with Android Keystore (AES-256-GCM, hardware-backed where available)
- **App denylist**: accessibility service refuses to interact with banking, payment, cryptocurrency, password manager, and authentication apps
- **Safety gate**: MEDIUM/HIGH risk actions require user confirmation
- **URL sanitization**: browser blocks javascript:, data:, file:, and SSRF to internal IPs
- **WebView**: SafeBrowsing enabled, mixed content blocked, JavaScript from LLM sanitized
- **Destructive action detection**: multi-step patterns (read notification → type → click) flagged with warnings
- **OCR-first vision**: screenshots processed locally via ML Kit; images only sent to LLM for visual layout context
- See [PRIVACY.md](docs/PRIVACY.md) for details

## Build Instructions

1. Install Android SDK (API 34+)
2. Install Java 17
3. Generate Gradle wrapper: `gradle wrapper --gradle-version 8.4`
4. Run: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
5. APK output at `app/build/outputs/apk/debug/app-debug.apk`

Install via: `adb install app-debug.apk`

## Default Provider: CrofAI

- Base URL: `https://crof.ai/v1`
- Endpoint: `/chat/completions`
- Mode: OPENAI_COMPATIBLE

### Available Models

- deepseek-v4-pro
- deepseek-v3.2
- glm-5.1
- glm-5.1-precision
- kimi-k2.6
- kimi-k2.6-precision
- kimi-k2.5
- kimi-k2.5-lightning
- gemma-4-31b-it
- minimax-m2.5
- qwen3.6-27b
- qwen3.5-397b-a17b
- qwen3.5-9b
- qwen3.5-9b-chat

## Package Structure

```
com.phoneagent
  MainActivity.kt

  overlay/
    OverlayService.kt
    OverlayPermissionManager.kt
    FloatingBubbleController.kt
    ChatOverlayController.kt
    OverlayState.kt

  agent/
    AgentController.kt
    AgentMode.kt
    AgentRequest.kt
    AgentResponse.kt
    ModelRouter.kt
    RoutingPolicy.kt
    ToolRegistry.kt
    TaskHistoryManager.kt

  providers/
    AiProvider.kt
    ProviderType.kt
    ProviderConfig.kt
    ProviderRepository.kt
    OpenAiCompatibleProvider.kt
    OllamaProvider.kt
    CrofAiDefaults.kt
    StreamingParser.kt
    ProviderError.kt

  memory/
    MemoryRepository.kt
    MemoryEntity.kt
    TaskEntity.kt
    AppDatabase.kt

  security/
    SecretStore.kt
    AndroidKeystoreSecretStore.kt

  perception/
    ScreenCaptureManager.kt
    OcrManager.kt
    VisionPayloadBuilder.kt

  voice/
    VoiceInputManager.kt
    SpeechOutputManager.kt

  ui/
    AppRoot.kt
    MainScreen.kt
    ProviderSettingsScreen.kt
    PermissionScreen.kt
    ChatScreen.kt
```

## Not Implemented (Future)

- Local model inference
- Terminal execution
- App builder automation
- Calendar/alarm scheduling
- GPS/location tools

## License

MIT
