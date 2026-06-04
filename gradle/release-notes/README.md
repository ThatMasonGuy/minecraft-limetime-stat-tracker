# Release Notes

Modrinth-facing release notes should live in this directory as:

```text
gradle/release-notes/<mod_version>.md
```

Create the file when preparing a user-facing release. Keep it concise and
focused on what players or server owners need to know:

- newly supported Minecraft versions
- install or compatibility notes
- command changes
- data migration or backup notes
- visible fixes or behavior changes

Do not use these files for internal-only Gradle rewiring, CI plumbing, docs
cleanup, or implementation detail. Put that history in `CHANGELOG.md` and
`TODO.md`.
