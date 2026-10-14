# DroidDeck Migration Implementation Plan

> For agentic workers: use subagent-driven-development for archive implementation and focused independent review.

**Goal:** An Android-only offline backup/restore edition, installed alongside upstream.
**Architecture:** JVM archive engine, Android foreground job/service and picker activity, restart-recoverable directory transaction, typed settings migration.
**Tech Stack:** Existing Kotlin/Compose Android app, Java NIO and Commons Compress, Gradle/JUnit.

- [ ] Establish independent repository, source provenance, external build directories and distinct application ID/signing identity.
- [ ] Archive engine: first write real TAR tests for traversal, deferred links, truncated payloads, execute bits, bounded sizes and backups. Implement streaming inspect/extract/write with no-follow traversal and reserved metadata. Use a single worker.
- [ ] Settings: test conversion and device-private filters; accept existing JSON and new embedded metadata.
- [ ] Transaction: test staging promotion failure and restart recovery; journal before renames and retain old files until accepted restart.
- [ ] Android service/activity: SAF input and output, progress notification, space preflight, prevent jobs during gameplay or another migration, inspection errors and safe retry. Launcher gateway forwards to upstream game UI when user presses Open DroidDeck.
- [ ] Build release with original exact-version native libraries checked by SHA256 and GPL source attribution; keep native gameplay unchanged. Limit JVM processors/workers to two and avoid new native compilers while other builds active.
- [ ] Run archive/settings/transaction tests, independent review, APK signature/package checks and physical-device new-package installation.
- [ ] Validate small rollback/corruption tests and full existing TAR restore on the migration package only; preserve official application and archives.
- [ ] Document verified outcome, build/install logs and phone-only instructions; deliver APK and source.
