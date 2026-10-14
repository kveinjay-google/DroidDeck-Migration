# Migration verification — 2026-10-14

Focused Gradle JVM/Robolectric tests: 49 tests, zero failures/errors. Archive standalone tests: 34, including traversal, truncation, long metadata, legacy root records, hardlinks, exact POSIX modes, ARM64 client retention and privacy filters. Generic stream/transaction/guest-path tests: 12; Android preferences tests: 3.

Debug and release APKs built and signed with the migration certificate. Application ID is com.droiddeck.migration. All 23 native library bytes in both actual packaged APKs match the pinned upstream original. All 118 runtime assets also match their provenance manifest, including the preserved PulseAudio payload and deliberately updated socket shim. Obsolete original dexopt profiles are excluded and regenerated for the new APK. Source review resolved staging symlink escapes, Android canonical aliases, guest-absolute executable links, bounded version reads, package-specific Flatpak socket, installer exclusion and restart guards. Reviewer approved source-level readiness.

The cleaned sharing package contains five <=2,000,000,000-byte parts, 8,682,729,984 archive bytes and 99,760 entries. A complete streaming pass validated part hashes, total SHA256, TAR entries and required runtime files. Exact excluded privacy paths are absent; personal settings are empty. This is a software-path allowlist rather than a claim that arbitrary private backups contain no personal data. The original personal TAR was preserved.

Physical device installation returned INSTALL_FAILED_USER_RESTRICTED (installation cancelled/restricted). User clarification is pending; there was no second install attempt. Therefore physical restore, startup and gameplay acceptance have not yet been verified.

Logs and reports are retained under /Volumes/LEXA 4TB DISK/BuildOutputs/droiddeck-migration. Build concurrency was two Gradle workers with one test fork and bounded Java processor counts; output, temporary files and fresh caches are on the external volume. No upstream app or user data was deleted, and nothing was pushed to GitHub.
