# PhoneAgent — Voice + Onboarding + Error Recovery Plan

**Goal:** Voice assistant flow, first-launch permission onboarding, and smarter error recovery in the agent loop.

---

## Phase A: Voice Integration

### Task A1: Connect voice to agent flow

**Files:** `agent/AgentController.kt`, `overlay/ChatOverlayController.kt`

- Add `VoiceInputManager` and `SpeechOutputManager` fields to `AgentController`
- Add `startVoiceInput()` — starts listening, sends transcribed text to `sendMessage()`
- Add callback after agent response: speak the answer via TTS
- Connect the loop: user taps mic on bubble → speech recognition → sends to agent → agent responds → TTS reads answer

### Task A2: Add mic button to overlay chat

**Files:** `res/layout/overlay_chat.xml`

- Add a microphone button next to the send button
- Wire it in `ChatOverlayController.kt` to call `agentController.startVoiceInput()`

---

## Phase B: Onboarding Wizard

### Task B1: Create onboarding flow

**Files:**
- Create: `ui/OnboardingScreen.kt`
- Create: `ui/OnboardingManager.kt` (checks first-launch flag)
- Modify: `ui/AppRoot.kt` (add onboarding route)
- Modify: `MainActivity.kt` (show onboarding on first launch)

Onboarding screens:
1. Welcome → "PhoneAgent needs permissions to control your phone"
2. Overlay → "Floating bubble for quick access" → Enable button
3. Accessibility → "Read screen content and perform gestures" → Enable button  
4. Notifications → "Read notifications for context" → Enable button
5. Microphone → "Voice commands" → Request button
6. Done → "You're all set. PhoneAgent is ready."

Store completion flag in SharedPreferences. Never show again.

---

## Phase C: Smarter Error Recovery

### Task C1: Enrich tool error results

**Files:** `agent/tools/PhoneSystemInfoTool.kt` (as example), then apply pattern

Add a `"suggestion"` field to error JSON responses that tells the LLM what went wrong and what to try. Examples:
- "Element with text 'X' not found." → suggestion: "Try accessibility.read_tree to see available elements."
- "Accessibility service not running." → suggestion: "Tell the user to enable it in Settings > Accessibility."
- "Browser not active." → suggestion: "Use browser.open_url first to launch the browser."
- "Permission denied." → suggestion: "Skip this action and tell the user what permission is missing."

### Task C2: Update prompt builder to surface suggestions

**Files:** `agent/AgentPromptBuilder.kt`

When building the loop message, include the suggestion text from the observation so the LLM can adapt its strategy.

---

## Build & Verify
