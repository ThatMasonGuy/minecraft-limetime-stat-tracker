# Modrinth Publishing

Modrinth publishing is implemented through guarded Gradle tasks and a manual
GitHub Actions workflow.

## Publishing Model

Publishing is driven by supported Minecraft version profiles only.
Profiles in `candidate_minecraft_version_profiles` must be ignored until they
are promoted to `supported_minecraft_version_profiles`.

Modrinth project id: `rJCvFZKh`.

Fabric API dependency project id: `P7dR8mSH`.

When only one supported profile exists, Modrinth `version_number` can be the mod
version, such as `2.1.0`.

When multiple supported profiles exist, append the profile id to keep Modrinth
version entries unique, such as:

```text
2.2.0+mc1.21.11
2.2.0+mc1.21.9-1.21.11
2.2.0+mc1.20-1.20.4
```

## Gradle Tasks

```powershell
.\gradlew.bat publishValidation
.\gradlew.bat prepareModrinthUploads
.\gradlew.bat publishModrinthDryRun
.\gradlew.bat publishModrinth -Pmodrinth_confirm_publish=true
```

- `publishValidation` builds and smoke-tests supported profiles only.
- `prepareModrinthUploads` runs validation, verifies upload metadata, and writes
  `build/modrinth/upload-plan.json`.
- `publishModrinthDryRun` performs the full validation path without
  calling the Modrinth API.
- `publishModrinth` performs the real upload and requires
  `-Pmodrinth_confirm_publish=true`.

The upload plan includes:

- project id `rJCvFZKh`
- loader `fabric`
- exact game versions from the supported profile's `modrinth_game_versions`
- required Fabric API dependency `P7dR8mSH`
- release notes from `gradle/release-notes/<mod_version>.md`
- the packaged jar from `build/release/<profile_id>/`

## Secrets

Real uploads require a Modrinth personal access token with version-create
permission. Provide it through a non-repo location:

```powershell
$env:MODRINTH_TOKEN="..."
.\gradlew.bat publishModrinth -Pmodrinth_confirm_publish=true
```

or a user-level Gradle property such as `%USERPROFILE%\.gradle\gradle.properties`:

```properties
modrinth_token=...
```

Do not store tokens in this repository.

GitHub publishing should read the repository secret named `MODRINTH_TOKEN`.

## GitHub Workflow

Use the manual `modrinth publish` workflow in `.github/workflows/`.

Inputs:

- `dry_run`: keep this enabled to validate and print the upload plan without
  publishing.
- `version_type`: `release`, `beta`, or `alpha`.
- `requested_status`: `listed`, `unlisted`, or `draft`.

The workflow installs `xvfb`, runs the supported-profile smoke launcher under a
virtual display, and captures `build/modrinth/upload-plan.json` plus
`build/release/` as artifacts.

## Release Notes

Modrinth changelogs should come from a concise per-version release note file:

```text
gradle/release-notes/<mod_version>.md
```

For example, `mod_version=2.2.0` should require:

```text
gradle/release-notes/2.2.0.md
```

The publish tasks should fail if the release note file is missing or blank. This
keeps Modrinth uploads focused on what changed in that release instead of
reposting the entire project changelog.

Update the active release note file as user-facing changes accumulate for the
release being prepared. Examples include newly supported Minecraft versions,
install notes, command changes, data migration notes, and user-visible fixes.
Do not include internal-only build rewiring, CI plumbing, docs cleanup, or
implementation details unless they change what users should know before
installing the release.

Use `CHANGELOG.md` for broad repo history and
`gradle/release-notes/<version>.md` for the exact Modrinth-facing notes.
