# PhoneAgent

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
- Clean modular architecture

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

## Phase 1 Features

1. Compiling Android project
2. MainActivity with permission cockpit
3. Overlay permission request
4. Foreground OverlayService
5. Draggable floating bubble
6. Tap bubble opens compact chat overlay
7. Chat overlay supports:
   - Provider selector
   - Model selector
   - Message input
   - Send button
   - Response display
   - Loading state
   - Error display
8. CrofAI OpenAI-compatible provider
9. Non-streaming chat first
10. Streaming parser structure (toggleable)
11. Provider settings
12. Securely stored API key
13. Provider config stored locally

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

## Build Instructions

1. Install Android SDK and set `ANDROID_HOME=/opt/android-sdk`
2. Ensure Java 17 is installed
3. This repo currently uses `settings.gradle.kts` and `build.gradle.kts` and does not include a Gradle wrapper, so run:
    ```bash
    gradle assembleDebug
    ```

## Permissions

- `INTERNET` - API communication
- `SYSTEM_ALERT_WINDOW` - Overlay bubble and chat
- `FOREGROUND_SERVICE` - Keep service running
- `FOREGROUND_SERVICE_SPECIAL_USE` - Android 14+ foreground service type
- `POST_NOTIFICATIONS` - Android 13+ notification permission

## Not Implemented (Future Phases)

- MediaProjection / screen capture
- OCR
- Voice input/output
- AccessibilityService actions
- Terminal execution
- App builder automation
- Local inference
- Server backend

## License

MIT
