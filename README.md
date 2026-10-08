# eSIM Notification Zapper

A small Kotlin Android app for a recurring Samsung **“Check SIM card tray”** warning. Instead of trying to intercept a protected system alert, its opt-in Android Accessibility Service identifies the warning **inside the same accessible window as its OK button**, then clicks the button. No root, VPN, internet connection, or notification-listener privileges are required.

**Limitations:** This can dismiss an accessible dialog *after* it appears, so it may still interrupt full-screen games briefly. Some Samsung system dialogs do not expose a usable accessibility tree or click action; in those cases software dismissal may not work. It does not fix the physical SIM connector. The exact behavior requires real-device testing.

## Install: CI only (no developer toolchain)

1. Open **Actions → Android APK** in this GitHub repository, and select a successful workflow run from a push/PR. You can also choose **Run workflow** to build again.
2. Under **Artifacts**, download `eSIMNotificationZapper-debug-apk`; extract `app-debug.apk`.
3. Copy/install the APK on the Samsung phone (you may have to allow the app installing the APK to install unknown apps).
4. Open **eSIM Notification Zapper**; tap **Open Accessibility settings** and explicitly enable **SIM warning auto-dismiss**.
5. On some Samsung/Android versions sideloaded accessibility apps are blocked as “restricted settings.” Go to **Settings → Apps → eSIM Notification Zapper → ⋮ → Allow restricted settings**, then enable the service in Accessibility.
6. Leave **Automatically dismiss matching warning** enabled and wait for the recurring warning.

The workflow on **every push and PR** installs JDK, Android SDK and Gradle, runs Kotlin unit tests and Android lint, builds the debug APK, and uploads it as an artifact. Developers do **not** need Gradle, Android Studio, an SDK, or a local keystore. The GitHub-hosted debug APK is signed with an Android debug key and is intended for sideload testing, **not Play Store distribution**.

**Important for testing later CI builds:** each clean GitHub runner normally generates a *different debug signing key*. Android therefore may reject installing a newer CI APK over an older CI APK. Uninstall the previous build first, then install the new one and re-enable Accessibility. This removes app preferences and diagnostics. A persistent signing key stored in GitHub Actions secrets could be added later if preserving installs is necessary. Do not commit a private signing key to the public repository.

## Usage

- Default detection phrase: `Check SIM card tray`, matched case-insensitively even if inside a longer warning string.
- Default exact button label: `OK`. Change it on the home screen if the Samsung dialog uses another label.
- Automatically-dismiss toggle pauses the service without revoking accessibility permission.
- Default safety restriction: only act on windows from likely Android or Samsung system packages. The advanced option allows other sources when debugging an unusual vendor dialog; it is not recommended unless needed.
- **Diagnostics**: a local ring buffer of the most recent 120 events. It logs relevant system package/class metadata and click outcomes, but not generic screen text, typed information, or arbitrary application content. Diagnostic capture can be disabled, cleared, copied, or shared by explicit user action.

### Troubleshooting

| Symptom | What to look for |
|---|---|
| No diagnostic events | Accessibility service is off, Android blocked the service, or Samsung doesn't expose the dialog through accessibility |
| `WINDOW` lines, but no `MATCH` | Change the phrase; the dialog text may not be accessible, or the window comes from a different package |
| `MATCH` + `NO_BUTTON` | The button is named differently, not accessible, or not clickable |
| `CLICK_FAILED` | Android/Samsung rejected the click; may require hardware repair or another approach |
| `DISMISSED`, but warning returns | Expected with a persistent hardware fault; the service responds each time |

**Diagnostic note:** `WINDOW` events are deliberately limited to likely system packages unless advanced matching is enabled. The service doesn't claim to suppress, disable, or cancel privileged system warnings at their source.

## Architecture

- `SimWarningAccessibilityService`: handles window-state, windows-changed, and throttled content-change events; searches active and other accessible windows; clicks only in the same window as the matched phrase.
- `WarningMatcher`: pure Kotlin phrase normalization and conservative string matching, with JVM unit tests.
- `PackageGuard`: conservative system-package allowlist.
- `AppPrefs`: persisted settings and successful-click counter.
- `DiagnosticStore`: local-only bounded diagnostic log.
- `MainActivity`: native Android settings and diagnostic interface without third-party UI dependencies.

The application requests no INTERNET permission. An Accessibility Service is a powerful permission: review the source and disable the service in Android Settings when no longer needed.

## Build toolchain (installed by CI)

Android Gradle Plugin 8.13.2, Kotlin Gradle Plugin 2.3.20, Gradle 8.13, JDK 17, compile SDK 36, min SDK 26, target SDK 35. Build and test results should be checked in the GitHub Actions tab; no local build is assumed.
