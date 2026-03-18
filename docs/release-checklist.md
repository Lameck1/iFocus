# Release Checklist

Use this checklist before publishing a build.

## Versioning

- [x] Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
- [x] Confirm release notes are drafted.
- [ ] Confirm Git tag follows `v*` pattern for release workflow trigger.

## Version control bootstrap

- [ ] Initialize git repository (if not already initialized).
- [ ] Commit current release-ready baseline.
- [ ] Add remote origin and push default branch.
- [ ] Enable branch protection and required status checks for CI.

## Quality gates

- [ ] `:app:testDebugUnitTest` passes.
- [x] `:app:lintDebug` passes.
- [x] `:app:assembleRelease` passes with minification enabled.
- [ ] CI workflow `.github/workflows/android-ci.yml` is green on the release commit.
- [ ] Migration test (`FocusDatabaseMigrationTest`) passes on connected test lane.

## Manual verification

- [ ] App launches and timer starts/pauses/resets correctly.
- [ ] Mode switching updates durations (25/5/15).
- [ ] Completion notification/snackbar triggers.
- [ ] Accessibility labels are announced for controls.

## Security and privacy

- [ ] Manifest still has no unneeded dangerous permissions.
- [ ] Backup and data extraction rules align with product decision.
- [ ] `usesCleartextTraffic=false` remains enforced unless required.
- [ ] Review dependency update PRs (Dependabot) and block known high/critical CVEs.
- [ ] Verify release-signing secrets are configured in repository settings.
- [ ] Verify `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is configured for internal track deployment.

## Artifact checks

- [x] Verify release APK/AAB generated successfully.
- [ ] Verify release APK is signed with production keystore.
- [ ] Smoke test on at least one physical device and one emulator/API level.
- [x] Archive mapping/symbol files needed for crash deobfuscation.
- [ ] Review uploaded lint and test artifacts from CI for regressions.

## Finalization

- [ ] Tag release commit.
- [ ] Publish notes and rollout plan.
- [ ] Run `.github/workflows/android-deploy-internal.yml` for internal Play rollout when needed.

## Evidence capture

- [x] Attach unit test report path: `app/build/reports/tests/testDebugUnitTest/index.html`.
- [x] Attach lint report path: `app/build/reports/lint-results-debug.html`.
- [x] Attach signed artifact paths (`.apk`, `.aab`, `mapping.txt`).
- [ ] Record workflow runs for CI, release, and internal deploy.

