# Changelog

## 0.4.0-migration.1 — 2026-10-14

Adds Android-only offline backup/restore, a separate package/signing identity, <=2 GB split volumes with SHA256 verification, legacy TAR support, portable settings, private staging and interruption recovery. Personal backups retain private games/saves; sharing packages omit account/browser/log/identity files and saves. Original native payload comes from the same upstream source commit and is verified by hash.


## 0.4.0-migration.2 — 2026-10-15
- Persist bounded local migration diagnostics with stage, document/part metadata, space and exception context.
- Include rejected archive path/rule/entry/offset and expected/actual size and SHA256 on volume failures.
- Add latest-report viewer and Android document-picker export; hide JSON contents in exception reports.
- Retain archive safeguards and the existing transactional restore format; no connected-device acceptance for this version.


## 0.4.0-migration.3 — 2026-10-16
- Fix false rejection of valid Linux filenames containing literal backslashes, including systemd's escaped slice names.
- Preserve POSIX names containing colons and other Windows-looking characters without renaming/decoding.
- Keep real slash traversal, absolute-path and symlink-ancestor containment checks.
- Ensure runtime export no longer silently omits legal escaped filenames. Diagnostic log/export remains available.

## 0.4.0-migration.4 — 2026-10-17
- Add cancellable bounded public-storage backup discovery, prioritizing Quark download subtrees.
- Show complete/incomplete candidates and precise missing-volume/size reasons; require confirmation before restore.
- Add permission guidance, manual picker fallback and search diagnostics.
- Reuse verified transactional restore through a confined local-file adapter; reject symlink/private-path traversal.
- Validation: 73 JVM/Robolectric tests pass and release build succeeds; physical-device acceptance pending.
