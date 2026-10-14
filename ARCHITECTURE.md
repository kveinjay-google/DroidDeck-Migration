# Architecture

MigrationActivity is the portrait launcher gateway. The complete upstream MainActivity remains the game UI. A single MigrationService foreground worker owns backup/restore and streams SAF documents.

BackupFiles reads/writes directory documents and JSON manifests; VolumeStreams concatenates verified parts without a merged temporary archive. MigrationArchive bounds TAR metadata/payloads, postpones links, preserves permissions/hardlinks, and offers distinct personal/runtime-only policies.

Restore checks all parts and archive entries before allocation, checks free space, then extracts a second pass into `migration.stage` under the canonical app data directory. DirectoryTransaction fsyncs a prepared journal before renaming current files to `migration.previous`, then promotes the stage. A committed journal causes MigrationRecovery to apply portable pending preferences before application providers/helpers start. A prepared interrupted journal restores the previous directory. A completed rollback copy is retained until the user explicitly releases it.

RuntimePaths resolves absolute Linux links inside the guest root and bounds version reads. Mount directories are re-created without following imported links. Paths referring to the old application files directory are remapped. The Flatpak abstract socket and shim are separate from the official edition.

Migration exclusion is shared with session start, runtime installation, maintenance and removal. New work uses one migration thread and one optional cleanup thread guarded by the same atomic reservation. A 30-minute deadline and a bounded wake lock protect long jobs. No new game-engine worker pool is introduced.
