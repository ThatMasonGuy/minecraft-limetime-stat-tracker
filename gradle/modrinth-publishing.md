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

- `publishValidation` builds and smoke-tests supported profiles only, including
  both client launch smoke and dedicated-server smoke.
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

The workflow installs `xvfb`, runs supported-profile client smoke under a
virtual display, runs dedicated-server smoke without `xvfb`, and captures
`build/modrinth/upload-plan.json`, `build/release/`, `build/smoke-logs/`,
`build/smoke-mod-lists/`, and `build/smoke-run/` as artifacts.

## Git Tags And GitHub Releases

After a real Modrinth publish succeeds, create an annotated Git tag for the
released `mod_version`:

```powershell
git tag -a "v2.7.0" <publish-workflow-head-sha> -m "Lifetime Stat Tracker 2.7.0"
git push origin "v2.7.0"
```

The tag must point at the exact commit used by the successful publish workflow.
For a GitHub Actions publish, use the workflow run's `headSha`. Do not tag a
later docs-only, project-page, or release-record commit as the released source.

Then create one GitHub Release for that tag:

```powershell
gh release create "v2.7.0" --verify-tag --title "Lifetime Stat Tracker 2.7.0" --notes "<release notes>"
```

GitHub Releases are an archive and source checkpoint. Modrinth remains the
primary download surface. Link the Modrinth project and the uploaded Modrinth
version ids in the GitHub Release notes. When multiple supported Minecraft
profiles are published for one `mod_version`, keep one GitHub Release and list
each profile-suffixed Modrinth version under it.

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

## Project Page Copy

Modrinth project summaries and long descriptions are tracked in:

```text
gradle/modrinth-project-pages.md
```

Update this file before changing live Modrinth project metadata. The publishing
tasks upload version files and per-version changelogs only; they do not update
the project summary, project body, gallery, categories, or other page-level
metadata.

For local project metadata updates, provide `MODRINTH_TOKEN` from a non-repo
location such as `.env`, PowerShell environment variables, or a user-level
secret store. Do not print or commit token values. Save before/after API
snapshots under ignored `build/modrinth/` artifacts when updating the live page.
