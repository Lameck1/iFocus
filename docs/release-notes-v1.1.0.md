# IFocus v1.1.0 Release Notes

## Highlights

- Upgraded task lifecycle with notes, archive state, and update timestamps.
- Added Room migration `4 -> 5` with backfill for task metadata.
- Expanded reports with Today / Week / By Task views and improved timestamp readability.
- Enhanced CSV export with `taskId` and `createdAtEpochMs`.
- Added settings-driven auto-start transitions between focus and break modes.

## Technical Changes

- Database version bumped to `5` with migration test coverage, including chain migration validation.
- Repository and UI mappings updated for task metadata fields.
- Added pre-release verification script: `scripts/pre-release-verify.ps1`.

## Upgrade Notes

- Existing installs migrate automatically from previous schemas through Room migrations.
- Archived tasks are now excluded from default active task lists.

## Validation Snapshot

- Local gates to run before tagging:
  - `:app:testDebugUnitTest`
  - `:app:lintDebug`
  - `:app:assembleRelease`
  - `:app:bundleRelease`
  - `:app:compileDebugAndroidTestKotlin`

## Rollout Plan

1. Push release commit to protected main branch.
2. Tag commit (`v1.1.0`) to trigger `.github/workflows/android-release.yml`.
3. Review uploaded release artifacts and mapping file.
4. Run `.github/workflows/android-deploy-internal.yml` for internal track validation.
5. Promote according to release policy after internal sign-off.

