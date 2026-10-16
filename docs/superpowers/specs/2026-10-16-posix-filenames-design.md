# POSIX archive filename compatibility

User requires correct-directory backups to import without arbitrary filename conventions. Customer diagnostic identifies a normal literal `system-systemd\x2dcryptsetup.slice` rejected as a Windows-style separator. On Android/Linux, backslash and colon are filename characters. Remove only these Windows-style rejections; never rewrite or decode these names. Keep slash ancestry/absolute containment and link checks. A POSIX host guard prevents accidental use with Windows path semantics. Shared exporter filtering uses the same corrected name function and must retain these legal paths.

Verify exact failing filename inspect/extract, other legal POSIX names, actual slash traversal still rejected, and runtime-only export roundtrip. Validate entire original five-part archive matching the customer's manifest. Build/sign migration.3 with existing certificate and retain diagnostics. No new backup format or data migration.
