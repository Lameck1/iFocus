# IFocus

A focused, single-screen Pomodoro timer app built with Jetpack Compose.

This project now targets professionals with a lightweight deep-work workflow:

- plan today\'s priority tasks
- run focused work sessions
- log session outcomes (`Done`, `Partial`, `Blocked`)
- track weekly focus insights
- persist tasks and session history locally (Room)

## Current architecture

- `MainActivity` hosts Compose content.
- `FocusScreen` renders timer, task plan, session wrap-up, and insights.
- `FocusViewModel` owns timer orchestration, professional task state, and completion events.
- `TimerUiState` models render state.

## Project structure

- `app/src/main/java/com/lameck/ifocus/` app entry points and UI.
- `app/src/main/res/` string/theme/resources.
- `app/src/test/` JVM unit tests.
- `app/src/androidTest/` device/emulator instrumentation and Compose UI tests.
- `.github/workflows/android-ci.yml` CI quality gates.

## Requirements

- Android Studio (latest stable).
- JDK 17+ (CI uses JDK 21).
- Android SDK matching `compileSdk` and build tools.

## Local commands

```powershell
Set-Location "C:\Users\lamec\AndroidStudioProjects\IFocus"
cmd /c gradlew.bat :app:testDebugUnitTest --console=plain
cmd /c gradlew.bat :app:lintDebug --console=plain
cmd /c gradlew.bat :app:assembleRelease --console=plain
```

Use the consolidated pre-release verifier to run the same baseline gates in one pass:

```powershell
Set-Location "C:\Users\lamec\AndroidStudioProjects\IFocus"
powershell -ExecutionPolicy Bypass -File .\scripts\pre-release-verify.ps1
```

## Testing strategy

- Unit tests validate timer behavior, elapsed-time logic, and event emission.
- Instrumented Compose tests validate critical user flows:
  - app launch controls
  - mode switching updates timer state
  - play/pause semantics

## Dependency update policy

- Prefer small, regular updates over infrequent large jumps.
- Dependabot is configured at `.github/dependabot.yml` for weekly Gradle updates.
- Update Compose BOM first, then AndroidX families, then third-party libs.
- After each update batch, run:
  - `:app:testDebugUnitTest`
  - `:app:lintDebug`
  - `:app:assembleRelease`
- Document notable behavioral changes in PR notes.

## CI gates

- Workflow: `.github/workflows/android-ci.yml`.
- Verifies Gradle wrapper integrity before build execution.
- Runs unit tests, lint, and release assemble on PRs and mainline pushes.
- Runs emulator-backed `connectedDebugAndroidTest` on pull requests.
- Uploads lint and unit-test reports as workflow artifacts for triage.

## Deployment

- Tag-based release workflow: `.github/workflows/android-release.yml`.
- Manual internal-track deploy workflow: `.github/workflows/android-deploy-internal.yml`.
- Trigger release builds by pushing a tag like `v1.0.0`.
- Trigger internal deployment from GitHub Actions `workflow_dispatch`.
- Required GitHub repository secrets:
  - `IFOCUS_RELEASE_STORE_FILE_BASE64`
  - `IFOCUS_RELEASE_STORE_PASSWORD`
  - `IFOCUS_RELEASE_KEY_ALIAS`
  - `IFOCUS_RELEASE_KEY_PASSWORD`
  - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (for internal track deployment)
- Local release builds can use env vars with the same names. If they are missing, `release` defaults to debug signing for local verification only.

```powershell
Set-Location "C:\Users\lamec\AndroidStudioProjects\IFocus"
$env:IFOCUS_RELEASE_STORE_FILE="C:\path\to\ifocus-release.jks"
$env:IFOCUS_RELEASE_STORE_PASSWORD="***"
$env:IFOCUS_RELEASE_KEY_ALIAS="***"
$env:IFOCUS_RELEASE_KEY_PASSWORD="***"
cmd /c gradlew.bat :app:assembleRelease --console=plain
```

## Version control setup (first-time)

If this workspace is not already a git repository, bootstrap it before CI/deployment:

```powershell
Set-Location "C:\Users\lamec\AndroidStudioProjects\IFocus"
git init
git add .
git commit -m "chore: release readiness baseline v1.1.0"
git branch -M main
git remote add origin <your-repository-url>
git push -u origin main
```

Then configure branch protection to require `.github/workflows/android-ci.yml` before merge.

## Release

See `docs/release-checklist.md` for the release gate checklist.
Current target version is `1.1.0` (`versionCode` `2`) in `app/build.gradle.kts`.

