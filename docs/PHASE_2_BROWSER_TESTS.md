# Phase 2.1 Browser Agent Loop Test Checklist

## Manual Tests

### Browser Navigation
- [ ] Open Agent Browser from MainScreen
- [ ] Browser loads https://www.google.com by default
- [ ] Browser loads custom URL from intent extra

### Agent Loop with Browser Tools
- [ ] Send: "Open google.com"
  - Expected: browser.open_url executed, browser activity launched
- [ ] Send: "Read the page metadata"
  - Expected: browser.read_metadata returns title, URL, links, buttons, inputs
- [ ] Send: "Click the Search button"
  - Expected: browser.click_text executed, element clicked
- [ ] Send: "Scroll down"
  - Expected: browser.scroll executed, page scrolls
- [ ] Send: "Go back"
  - Expected: browser.back executed, navigation back
- [ ] Send: "Reload the page"
  - Expected: browser.reload executed, page reloads
- [ ] Send: "Read the page text"
  - Expected: browser.read_page returns stripped text (max 8000 chars)

### Search Flow
- [ ] Send: "Search for 'Android Kotlin' on Google"
  - Expected loop:
    1. browser.open_url -> google.com
    2. browser.type_into_selector (or type_into_focused) -> query
    3. browser.click_text -> "Google Search"
    4. browser.read_metadata -> results summary
    5. final_answer

### Safety Confirmation Gate
- [ ] Send: "Send an email"
  - Expected: SafetyGate blocks on "send", confirmation dialog shown
- [ ] Approve action
  - Expected: tool executes, loop continues
- [ ] Cancel action
  - Expected: observation says "User canceled", model should produce final_answer
- [ ] Send: "Purchase something"
  - Expected: SafetyGate blocks on "purchase", confirmation dialog shown

### Tool Debug UI
- [ ] During any agent task, "Show steps" card appears in ChatScreen
- [ ] Tapping "Show steps" expands a list of tool calls with args and success/failure
- [ ] Error details visible for failed tool calls

### Phone Tools
- [ ] Send: "List my apps"
  - Expected: phone.list_apps returns JSON list
- [ ] Send: "Open Settings"
  - Expected: phone.open_app opens Settings app
- [ ] Send: "Show system info"
  - Expected: phone.system_info returns battery, time, Android version, model, network

### Edge Cases
- [ ] Model returns invalid JSON
  - Expected: parse error shown in steps, model retries
- [ ] Model returns plain text instead of JSON
  - Expected: treated as final_answer
- [ ] Unknown tool requested by model
  - Expected: tool_result with error "Unknown tool"
- [ ] Browser action called without active browser
  - Expected: AgentBrowserActivity launched automatically for open_url, error for others

## Build Verification
- [ ] `gradle assembleDebug` succeeds
- [ ] `gradle assembleRelease` succeeds
- [ ] APK installs on Android device
- [ ] No crashes on launch
