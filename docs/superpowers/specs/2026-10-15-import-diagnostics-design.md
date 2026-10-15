# Import diagnostics

User requested actionable, detailed logs on import failure. Preserve all archive safety checks and the existing two-pass staged restore. Add a bounded local recorder in Android no-backup storage (five reports, approximately 1 MB/report), operation/device/app version, document provider and selected document ID, safe manifest metadata/part inventory, expected and reported file sizes, restore stage/progress/space, full exception cause stack and archive entry/type/link/offset. Never serialize settings/account values or file contents. Never upload automatically.

UI: view latest selectable text and export snapshot through ACTION_CREATE_DOCUMENT, available after task completion and across process restarts. Error summary points to these controls. Bump version to migration.2 using the existing signer for update installs. Logs diagnose the currently unknown customer failure; do not claim that its cause is fixed.

Acceptance: regression tests demonstrate actionable path/hash failures, persistence, bounded logs and retention. Focused JVM/Robolectric suite plus signed release build. Device test only when connected and available.
