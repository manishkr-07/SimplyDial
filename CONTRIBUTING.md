# Contributing to SimplyDial

First off, thank you for considering contributing to **SimplyDial**! Whether you are reporting a bug, proposing a new accessibility feature, writing code, or improving translations, your contributions help make mobile communication easier and more accessible for everyone.

SimplyDial was initiated and is owned by **Piyush Kumar**, and is co-managed with **Manish Kumar**.

---

## 💬 Community & Communication

Have a question or want to discuss a new feature? Join our community:
- 💬 **Discord**: [discord.gg/hQebWS8qZ](https://discord.gg/hQebWS8qZ)
- 🔴 **Reddit**: [r/SimplyDial07](https://www.reddit.com/r/SimplyDial07/)
- ✈️ **Telegram**: [t.me/SimplyDial](https://t.me/SimplyDial)
- 🌐 **Developer Portal**: [manish.dev](https://manishkr-dev.vercel.app/)
- ✉️ **Direct Inquiries**: [manis781701@zohomail.in](mailto:manis781701@zohomail.in)

---

## 🐛 Reporting Bugs

1. Make sure the bug hasn't already been reported in the [GitHub Issues](https://github.com/manishkr-07/SimplyDial/issues) tab.
2. When creating a bug report, include:
   - Your Android version and device model.
   - The version of SimplyDial installed (found in **Settings → About SimplyDial**).
   - Clear, reproducible steps to trigger the bug.
   - Expected behavior vs. what actually occurred.
   - Relevant logcat snippets if available.

---

## 💡 Suggesting Features

We welcome ideas that align with SimplyDial's core mission:
- Senior-friendly accessibility (**EaseUI**).
- Audio clarity and high-definition call recording compliance.
- Visual comfort (Black Diamond AMOLED dark theming).
- Open communication (Jitsi Meet video calling & Gemini AI voice integration).

Please open a [Feature Request Issue](https://github.com/manishkr-07/SimplyDial/issues/new) describing your concept and why it benefits users.

---

## 🛠️ Developing & Submitting Code

1. **Fork & Branch**:
   - Fork the repository on GitHub.
   - Create a feature branch: `git checkout -b feature/my-new-feature` or `bugfix/issue-description`.

2. **Code Standards**:
   - Write clean, idiomatic **Kotlin**.
   - Adhere to Android MVVM architecture patterns.
   - Ensure layouts support both Light mode and the Black Diamond AMOLED theme.
   - Do not commit sensitive keys, keystores, or machine-specific paths (use `gemini.properties` for optional AI keys, which is ignored by Git).

3. **Building & Testing**:
   ```bash
   ./gradlew assembleCoreDebug
   ```
   Ensure the project builds cleanly without errors.

4. **Pull Requests**:
   - Submit your PR against the `main` branch.
   - Provide a concise description of changes and test steps.

---

## ⚖️ Open Source License & Attribution

By contributing to SimplyDial, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPLv3)**.

SimplyDial is built upon the open-source foundation of [Fossify Phone](https://github.com/FossifyOrg/Phone). Please respect all upstream licenses, copyrights, and open-source attributions.
