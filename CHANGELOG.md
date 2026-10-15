# Changelog

## 0.4.0-migration.1 — 2026-10-14

Adds Android-only offline backup/restore, a separate package/signing identity, <=2 GB split volumes with SHA256 verification, legacy TAR support, portable settings, private staging and interruption recovery. Personal backups retain private games/saves; sharing packages omit account/browser/log/identity files and saves. Original native payload comes from the same upstream source commit and is verified by hash.


## 0.4.0-migration.2 — 2026-10-15
- Persist bounded local migration diagnostics with stage, document/part metadata, space and exception context.
- Include rejected archive path/rule/entry/offset and expected/actual size and SHA256 on volume failures.
- Add latest-report viewer and Android document-picker export; hide JSON contents in exception reports.
- Retain archive safeguards and the existing transactional restore format; no connected-device acceptance for this version.
