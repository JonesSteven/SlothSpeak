# Contributing to SlothSpeak

Thank you for your interest in contributing to SlothSpeak! This document provides guidelines and information for contributors.

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/your-username/SlothSpeak.git
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Open the project in Android Studio (SDK 35 required) and let Gradle sync

## Development Setup

- **IDE**: Android Studio with Kotlin plugin
- **Min SDK**: API 26 (Android 8.0)
- **Target/Compile SDK**: 35
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3

You will need API keys from one or more providers (OpenAI, Google, Anthropic, xAI) to test the app. An OpenAI key is always required for STT and TTS.

## Making Changes

### Code Style

- Follow existing Kotlin conventions and formatting in the codebase
- Use Jetpack Compose idioms for UI code
- Keep functions focused and well-named

### Commit Messages

- Use clear, concise commit messages
- Start with a verb in the imperative mood (e.g., "Add", "Fix", "Update")
- Reference issue numbers where applicable (e.g., "Fix #42: Handle network timeout")

### What to Contribute

- Bug fixes
- Performance improvements
- New AI provider integrations
- UI/UX improvements
- Documentation improvements
- Test coverage

### What to Avoid

- Changes that break existing functionality without discussion
- Large refactors without prior discussion in an issue
- Adding dependencies without justification
- Changes to API key handling that could compromise security

## Submitting Changes

1. Ensure your code builds without errors:
   ```bash
   ./gradlew assembleDebug
   ```
2. Test your changes on a device or emulator
3. Commit your changes with a clear message
4. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
5. Open a Pull Request against the `main` branch
6. Describe your changes and the problem they solve in the PR description

## Pull Request Guidelines

- Keep PRs focused on a single change
- Include a description of what changed and why
- Note any breaking changes
- Add screenshots for UI changes
- Ensure the build passes before requesting review

## Reporting Issues

- Use the [GitHub Issues](https://github.com/JonesSteven/SlothSpeak/issues) page
- Include steps to reproduce the issue
- Include device model, Android version, and app version
- Include relevant logs or screenshots if possible
- Check existing issues before creating a new one

## License

By contributing to SlothSpeak, you agree that your contributions will be licensed under the [MIT License](LICENSE.md).
