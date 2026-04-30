# PhoneAgent Privacy

## Data on device (never leaves the phone)

| Data | Storage | Encryption |
|------|---------|-----------|
| API keys | Android Keystore | AES-256-GCM, hardware-backed |
| Chat history | Room SQLite DB | Plaintext (local only) |
| Task history + steps | Room SQLite DB | Plaintext (local only) |
| Provider config (URLs, models) | DataStore | Plaintext (API keys excluded) |
| Screenshots | RAM only | Never persisted to disk |
| OCR results | RAM only | Never persisted |
| Notifications | RAM (max 50 entries) | Cleared on service stop |

## Data sent off-device

| Data | Destination | When |
|------|------------|------|
| Chat messages + step history | Your configured AI provider (CrofAI, OpenAI, Ollama, etc.) | Every agent step |
| Screenshots (image only) | Your configured AI provider | First step and after UI changes (OCR-first, image-as-fallback) |
| API key | Your configured AI provider | As Bearer token header on every request |

### What is NEVER sent off-device

- No analytics, no telemetry, no crash reporting
- No advertising ID, no device fingerprinting
- No contacts, no calendar, no app usage statistics
- No location data
- Notification content (only read locally by the agent, kept in RAM)

## App denylist

PhoneAgent's accessibility service refuses to read or interact with:

- Banking apps (Chase, Bank of America, Wells Fargo, Monzo, Revolut, etc.)
- Payment apps (PayPal, Venmo, Cash App, Stripe, Google Pay, etc.)
- Cryptocurrency apps (Coinbase, Binance, MetaMask, Trust Wallet, etc.)
- Password managers (1Password, LastPass, Bitwarden, Dashlane, etc.)
- Authentication apps (Google Authenticator, Microsoft Authenticator, Authy, Duo, etc.)
- System settings

This is enforced at the tool level — the accessibility service will return an error if the foreground app matches any denylist entry.

## Data retention

- Chat messages and task history persist until manually cleared
- Notifications are cleared when the notification listener service stops
- Screenshots and OCR results exist only in RAM during agent steps
- Provider configuration persists until changed

## Your responsibilities

- Review task history periodically and clear sensitive data
- Use a dedicated API key with usage limits
- Do not grant accessibility access if you regularly handle sensitive data on this device
- Be aware that AI providers receive your prompts, step history, and occasional screenshots — choose one you trust

## Disclosure

This app is intended for power users and developers who understand the implications of granting accessibility + overlay + SMS + phone permissions to an AI agent. It is not distributed through Google Play. Use at your own risk.
