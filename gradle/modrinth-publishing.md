# Modrinth Publishing

Modrinth publishing is not implemented in this repo yet. This file captures the
rules the future pipeline should follow.

## Publishing Model

Publishing should be driven by supported Minecraft version profiles only.
Profiles in `candidate_minecraft_version_profiles` must be ignored until they
are promoted to `supported_minecraft_version_profiles`.

When only one supported profile exists, Modrinth `version_number` can be the mod
version, such as `2.1.0`.

When multiple supported profiles exist, append the profile id to keep Modrinth
version entries unique, such as:

```text
2.2.0+mc1.21.11
2.2.0+mc1.21.9-1.21.11
2.2.0+mc1.20-1.20.4
```

## Planned Tasks

These tasks are intended names and may change during implementation:

```powershell
.\gradlew.bat publishValidation
.\gradlew.bat prepareModrinthUploads
.\gradlew.bat publishModrinthDryRun
.\gradlew.bat publishModrinth -Pmodrinth_confirm_publish=true
```

- `publishValidation` should build and smoke-test supported profiles only.
- `prepareModrinthUploads` should run validation, verify upload metadata, and
  write an upload plan.
- `publishModrinthDryRun` should perform the full validation path without
  calling the Modrinth API.
- `publishModrinth` should perform the real upload and require an explicit
  confirmation property.

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
