# Release Evidence - v1.1.0

Date: 2026-03-18

## Build and quality evidence

- `:app:lintDebug` completed successfully.
- `:app:assembleRelease :app:bundleRelease` completed successfully.
- `:app:compileDebugAndroidTestKotlin` completed successfully.
- Focused JVM suite run succeeded: `:app:testDebugUnitTest --tests "com.lameck.ifocus.reports.SessionCsvFormatterTest"`.

## Known environment instability observed

- Intermittent Gradle daemon interruption (`stop command received`) during some full `:app:testDebugUnitTest` attempts.
- Intermittent Windows file-lock clean failures (`Unable to delete directory ... app/build`).
- `scripts/pre-release-verify.ps1` now includes retry logic and daemon-stop recovery to mitigate these issues.

## Generated artifacts (local)

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- Mapping: `app/build/outputs/mapping/release/mapping.txt`
- Lint report: `app/build/reports/lint-results-debug.html`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Pre-release run logs: `build-logs/pre-release-*.log`

## Release metadata

- `versionCode = 2`
- `versionName = "1.1.0"`

## Version control state

- Local repository initialized (`.git` present).
- Current branch: `main`.
- Latest commits:
  - `abb6a90` - docs: update release checklist status
  - `773df5b` - chore: release readiness baseline v1.1.0

## Migration coverage

- Added migration chain test `migrate1To5_runsFullChain_andBackfillsTaskMetadata` in `app/src/androidTest/java/com/lameck/ifocus/data/FocusDatabaseMigrationTest.kt`.

## Still required before deployment

- Confirm full `:app:testDebugUnitTest` stable completion in a clean environment.
- Run connected migration lane (`FocusDatabaseMigrationTest`) on emulator/CI.
- Complete git bootstrap in this workspace (currently no `.git` directory).
- Push to remote and verify `.github/workflows/android-ci.yml` green on release commit.
- Verify repository secrets for signing and Play internal deploy.
- Tag release commit (`v1.1.0`) and run `.github/workflows/android-release.yml` / `.github/workflows/android-deploy-internal.yml`.


