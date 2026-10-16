# POSIX filename regression verification — 2026-10-16

Customer report identified a literal backslash in a legitimate systemd slice filename rejected during first-pass archive inspection. RED tests reproduced failure for the exact path and Windows-looking POSIX names. All 61 focused migration JVM/Robolectric tests now pass (40 archive tests), including inspect/extract preservation, real slash traversal rejection and runtime-only export roundtrip without silent omission. Source reviewer found no containment blockers.

The customer's original five-volume manifest SHA256 2404d68f8fea78acb87dc9ab8570ee7358346e11720f7dae3f03b132b253b4bf was validated using the actual updated engine. All part/whole hashes, 100934 TAR entries, 8700470730 unpacked payload bytes and required runtime structure passed. This was full streaming validation, not an Android-device extraction.

Focused Gradle tests and assembleRelease succeeded (build-posix-filenames.log). APK SHA256 9531b9ad8c2044f3f449eddc022f9668861cf941c66ba9ca4ed9baf0e66d9938. Package/versionCode/signature checked separately; original 23 native libraries and 118 runtime assets match provenance. No connected Android device; migration.3 on-device install/restore is not claimed. Logs retained on external BuildOutputs.
