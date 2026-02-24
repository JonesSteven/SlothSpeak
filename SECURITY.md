# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |

## Reporting a Vulnerability

If you discover a security vulnerability in SlothSpeak, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please report vulnerabilities by emailing the maintainers or using [GitHub's private vulnerability reporting](https://github.com/JonesSteven/SlothSpeak/security/advisories/new).

Please include the following in your report:

- A description of the vulnerability
- Steps to reproduce the issue
- The potential impact
- Any suggested fixes (if applicable)

## Response Timeline

- **Acknowledgment**: We will acknowledge receipt of your report within 72 hours
- **Assessment**: We will assess the vulnerability and determine its severity within 7 days
- **Fix**: Critical vulnerabilities will be prioritized for a fix as soon as possible
- **Disclosure**: We will coordinate with you on public disclosure timing

## Security Considerations

### API Key Storage

SlothSpeak stores API keys locally on the device using Android's `EncryptedSharedPreferences` with AES-256-GCM encryption. API keys are never transmitted to any server other than the respective AI provider's API endpoint.

### Data Handling

- All API calls go directly from the device to the provider (OpenAI, Google, Anthropic, xAI)
- No intermediate server, analytics, or telemetry
- Conversation history and audio files are stored locally on the device
- The app sets `android:allowBackup="false"` to prevent backup of sensitive data

### Audio Recording

- The microphone is only activated when the user initiates recording or during interactive voice mode follow-up listening
- Audio files are stored in the app's private directory
- Users can delete all audio files from Settings

### Permissions

The app requests only the permissions necessary for its functionality:

- `RECORD_AUDIO` — Voice recording
- `INTERNET` — API calls to AI providers
- `FOREGROUND_SERVICE` — Background pipeline processing
- `WAKE_LOCK` — Prevent CPU sleep during long API calls
- `BLUETOOTH_CONNECT` — Optional Bluetooth headset routing
- `POST_NOTIFICATIONS` — Pipeline status notifications

## Best Practices for Users

- Set spending limits on your AI provider accounts
- Revoke and rotate API keys if you suspect they have been compromised
- Keep your device secure with a screen lock
- Update to the latest version of SlothSpeak for security fixes
