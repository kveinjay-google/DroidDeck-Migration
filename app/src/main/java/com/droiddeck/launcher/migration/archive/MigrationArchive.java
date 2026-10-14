package com.droiddeck.launcher.migration.archive;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.*;

/** Streaming runtime archives. Restore into a fresh, private staging directory. */
public final class MigrationArchive {
    public static final long MAX_BYTES = 128L * 1024 * 1024 * 1024;
    public static final long MAX_ENTRIES = 500_000;
    private static final long MAX_METADATA_BYTES = 32L * 1024 * 1024;
    private static final Set<String> RUNTIME_SYSTEM_DIRS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("bin", "sbin", "etc", "usr", "opt", "var", "lib", "lib32", "lib64", "libx32", "boot")));
    private static final Set<String> STEAM_ROOT_BINARIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("steam", "steam.sh", "steam_msg.sh", "steamdeps.txt", "steam.signatures")));
    public interface Progress { void update(long bytes, long entries); }
    public static final class Inspection { public long unpackedBytes, entries; }
    private MigrationArchive() {}

    public static Inspection inspect(InputStream in, long maxBytes, Progress progress) throws IOException {
        return read(in, null, maxBytes, progress);
    }
    public static Inspection extract(InputStream in, Path destination, long maxBytes, Progress progress) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        checkAncestors(root);
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Staging directory is a symbolic link");
        return read(in, root, maxBytes, progress);
    }
    private static Inspection read(InputStream input, Path root, long maxBytes, Progress progress) throws IOException {
        if (maxBytes < 0 || maxBytes > MAX_BYTES) throw new IOException("Invalid archive size limit");
        CountingInput in = new CountingInput(input);
        TarArchiveInputStream tar = new BoundedTarInput(in);
        Inspection result = new Inspection();
        Map<String, Character> kinds = new HashMap<>();
        Map<String, Long> sizes = new HashMap<>();
        long metadataBytes = 0;
        List<TarArchiveEntry> links = new ArrayList<>();
        List<TarArchiveEntry> directories = new ArrayList<>();
        byte[] buffer = new byte[64 * 1024];
        long payloadEnd = 0, reportedBytes = 0, reportedEntries = 0;
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            if (!entry.isCheckSumOK() || !tar.canReadEntryData(entry)) throw new IOException("Invalid TAR entry");
            char kind = entry.isDirectory() ? 'd' : entry.isSymbolicLink() ? 's' : entry.isLink() ? 'h' : entry.isFile() ? 'f' : '?';
            if (kind == '?') throw new IOException("Unsupported archive entry: " + entry.getName());
            metadataBytes += textBytes(entry.getName());
            if (kind == 'h' || kind == 's') metadataBytes += textBytes(entry.getLinkName());
            if (metadataBytes > MAX_METADATA_BYTES) throw new IOException("Archive metadata exceeds limit");
            boolean rootEntry = entry.getName().equals(".") || entry.getName().equals("./");
            String name = rootEntry && kind == 'd' ? "" : name(entry.getName());
            Character old = kinds.putIfAbsent(name, kind);
            if (old != null && !(old == 'd' && kind == 'd')) throw new IOException("Duplicate archive path: " + name);
            if (++result.entries > MAX_ENTRIES) throw new IOException("Too many archive entries");
            long size = entry.getSize();
            if (size < 0 || size > maxBytes - result.unpackedBytes || (kind != 'f' && size != 0)) throw new IOException("Archive exceeds payload limit");
            if (name.equals("linuxfs/.version") && kind == 'f' && size > 64) throw new IOException("Runtime version file exceeds 64 bytes");
            result.unpackedBytes += size;
            if (rootEntry) { payloadEnd = in.count; continue; }
            if (kind == 'f') sizes.put(name, size);
            if (kind == 'h') name(entry.getLinkName());
            if (kind == 'h' || kind == 's') links.add(entry);
            if (kind == 's' && (entry.getLinkName().isEmpty() || entry.getLinkName().indexOf('\0') >= 0)) throw new IOException("Invalid symlink target");
            Path target = root == null ? null : root.resolve(name);
            if (target != null) {
                checkAncestors(target);
                if (kind == 'd') { Files.createDirectories(target); directories.add(entry); }
                else if (kind == 'f') {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { copy(tar, out, size, buffer, progress, result.unpackedBytes - size, result.entries); }
                    mode(target, entry.getMode());
                }
            }
            if (root == null || kind != 'f') copy(tar, null, size, buffer, progress, result.unpackedBytes - size, result.entries);
            payloadEnd = in.count;
            if (progress != null && (result.unpackedBytes - reportedBytes >= 1024 * 1024 || result.entries - reportedEntries >= 256)) {
                progress.update(result.unpackedBytes, result.entries); reportedBytes = result.unpackedBytes; reportedEntries = result.entries;
            }
        }
        long trailerStart = ((payloadEnd + 511) / 512) * 512;
        if (in.count < trailerStart + 1024) throw new IOException("Incomplete TAR trailer");
        for (Map.Entry<String, Character> item : kinds.entrySet()) {
            String parent = item.getKey();
            while (parent.contains("/")) { parent = parent.substring(0, parent.lastIndexOf('/')); Character k = kinds.get(parent); if (k != null && k != 'd') throw new IOException("Non-directory ancestor: " + parent); }
        }
        Map<String, String> hardTargets = new HashMap<>();
        for (TarArchiveEntry e : links) if (e.isLink()) hardTargets.put(name(e.getName()), name(e.getLinkName()));
        Map<String, String> resolved = new HashMap<>();
        for (String link : hardTargets.keySet()) {
            Set<String> chain = new LinkedHashSet<>(); String next = link;
            while (!resolved.containsKey(next) && kinds.get(next) != Character.valueOf('f')) {
                if (!chain.add(next) || !hardTargets.containsKey(next)) throw new IOException("Invalid hardlink target");
                next = hardTargets.get(next);
            }
            String terminal = resolved.getOrDefault(next, next);
            for (String part : chain) resolved.put(part, terminal);
        }
        String version = "linuxfs/.version";
        String session = "linuxfs/usr/local/bin/droiddeck-session";
        String legacySession = "linuxfs/usr/local/bin/bannerlator-session";
        Map<String, String> symbolicTargets = new HashMap<>();
        for (TarArchiveEntry e : links) if (e.isSymbolicLink()) symbolicTargets.put(name(e.getName()), e.getLinkName());
        if (sizes.getOrDefault(resolved.getOrDefault(version, version), 0L) > 64 || !nonemptyFile(version, kinds, sizes, resolved) ||
            (!nonemptyFile(session, kinds, sizes, resolved) && !nonemptyFile(legacySession, kinds, sizes, resolved)) ||
            !runtimeFile("linuxfs/usr/bin/gamescope", kinds, sizes, resolved, symbolicTargets))
            throw new IOException("Archive lacks required runtime files");
        if (root != null) {
            for (TarArchiveEntry e : links) if (e.isLink()) {
                Path source = root.resolve(resolved.get(name(e.getName()))), target = root.resolve(name(e.getName()));
                checkAncestors(source); checkAncestors(target);
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unresolved hardlink target");
                Files.createDirectories(target.getParent());
                try { Files.createLink(target, source); } catch (UnsupportedOperationException | FileSystemException ex) { Files.copy(source, target); }
                mode(target, e.getMode());
            }
            for (TarArchiveEntry e : links) if (e.isSymbolicLink()) {
                Path target = root.resolve(name(e.getName())); checkAncestors(target); Files.createDirectories(target.getParent()); Files.createSymbolicLink(target, Paths.get(e.getLinkName()));
            }
            directories.sort(Comparator.comparingInt((TarArchiveEntry e) -> e.getName().length()).reversed());
            for (TarArchiveEntry e : directories) mode(root.resolve(name(e.getName())), e.getMode());
        }
        if (progress != null) progress.update(result.unpackedBytes, result.entries);
        return result;
    }
    private static boolean nonemptyFile(String path, Map<String, Character> kinds, Map<String, Long> sizes, Map<String, String> resolved) {
        Character kind = kinds.get(path);
        return kind != null && (kind == 'f' || kind == 'h') && sizes.getOrDefault(resolved.getOrDefault(path, path), 0L) > 0;
    }
    private static boolean runtimeFile(String path, Map<String, Character> kinds, Map<String, Long> sizes, Map<String, String> resolved, Map<String, String> symbolicTargets) {
        Set<String> seen = new HashSet<>();
        while (symbolicTargets.containsKey(path)) {
            if (!seen.add(path) || seen.size() > 128) return false;
            String target = symbolicTargets.get(path);
            Path next = target.startsWith("/") ? Paths.get("linuxfs" + target) : Paths.get(path).getParent().resolve(target);
            path = next.normalize().toString();
            if (!path.startsWith("linuxfs/")) return false;
        }
        return nonemptyFile(path, kinds, sizes, resolved);
    }
    private static int textBytes(String text) throws IOException {
        if (text == null || text.length() > 4096 || text.indexOf('\0') >= 0) throw new IOException("Archive name exceeds limit");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4096 || text.split("/", -1).length > 128) throw new IOException("Archive name exceeds limit");
        return bytes.length;
    }
    private static String name(String raw) throws IOException {
        textBytes(raw);
        if (raw == null || raw.isEmpty() || raw.startsWith("/") || raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe archive path");
        StringBuilder normalized = new StringBuilder();
        for (String part : raw.split("/")) { if (part.equals("..")) throw new IOException("Unsafe archive path"); if (part.isEmpty() || part.equals(".")) continue; if (normalized.length() > 0) normalized.append('/'); normalized.append(part); }
        if (normalized.length() == 0) throw new IOException("Empty archive path");
        return normalized.toString();
    }
    private static void checkAncestors(Path path) throws IOException { for (Path p = path; p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Symbolic link in staging path"); }
    private static void copy(InputStream in, OutputStream out, long size, byte[] buffer, Progress progress, long base, long entries) throws IOException {
        long done = 0, reported = 0;
        while (size > 0) {
            int count = in.read(buffer, 0, (int)Math.min(size, buffer.length));
            if (count <= 0) throw new EOFException("Truncated TAR payload");
            if (out != null) out.write(buffer, 0, count);
            size -= count; done += count;
            if (progress != null && done - reported >= 1024 * 1024) { progress.update(base + done, entries); reported = done; }
        }
    }
    private static void mode(Path path, int mode) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return;
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] bits = {PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ};
        for (int i=0;i<bits.length;i++) if ((mode & (1<<i)) != 0) permissions.add(bits[i]); view.setPermissions(permissions);
    }
    /** Reject oversized extension payloads before Commons allocates their text buffers. */
    private static final class BoundedTarInput extends TarArchiveInputStream {
        private long extensionBytes;
        BoundedTarInput(InputStream in) { super(in); }
        @Override protected byte[] readRecord() throws IOException {
            byte[] record = super.readRecord();
            if (record != null) {
                byte type = record[156];
                if (type == TarConstants.LF_GNUTYPE_LONGNAME || type == TarConstants.LF_GNUTYPE_LONGLINK ||
                    type == TarConstants.LF_PAX_EXTENDED_HEADER_LC || type == TarConstants.LF_PAX_EXTENDED_HEADER_UC ||
                    type == TarConstants.LF_PAX_GLOBAL_EXTENDED_HEADER) {
                    long size;
                    try { size = TarUtils.parseOctalOrBinary(record, 124, 12); }
                    catch (IllegalArgumentException e) { throw new IOException("Invalid metadata size", e); }
                    if (size < 0 || size > MAX_METADATA_BYTES - extensionBytes) throw new IOException("Archive extension metadata exceeds limit");
                    extensionBytes += size;
                }
            }
            return record;
        }
    }
    private static final class CountingInput extends FilterInputStream {
        long count; CountingInput(InputStream in) { super(in); }
        public int read() throws IOException { int v=in.read(); if(v>=0)count++;return v; }
        public int read(byte[] b,int o,int n)throws IOException {int v=in.read(b,o,n);if(v>0)count+=v;return v;}
        public long skip(long n)throws IOException {long v=in.skip(n);count+=v;return v;}
    }
    public static void write(Path filesRoot, OutputStream output, byte[] settingsJson, Progress progress) throws IOException {
        write(filesRoot, output, settingsJson, progress, false);
    }
    public static void write(Path filesRoot, OutputStream output, byte[] settingsJson, Progress progress, boolean runtimeOnly) throws IOException {
        Path root=filesRoot.toAbsolutePath().normalize(); checkAncestors(root);
        TarArchiveOutputStream tar=new TarArchiveOutputStream(output); tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        long[] counts={0,0,0,0,0}; byte[] buffer=new byte[64*1024];
        Map<Object, String> fileKeys = new HashMap<>();
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException { if(dir.equals(root))return FileVisitResult.CONTINUE;String n=root.relativize(dir).toString();if(!shouldExport(n,true,false,runtimeOnly))return FileVisitResult.SKIP_SUBTREE;add(dir,attrs,n);return FileVisitResult.CONTINUE; }
            public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {String n=root.relativize(file).toString();if(shouldExport(n,false,attrs.isSymbolicLink(),runtimeOnly)&&(attrs.isRegularFile()||attrs.isSymbolicLink()))add(file,attrs,n);return FileVisitResult.CONTINUE;}
            private void add(Path path,BasicFileAttributes attrs,String n)throws IOException {
                n = name(n);
                String hardTarget = attrs.isRegularFile() && attrs.fileKey() != null ? fileKeys.get(attrs.fileKey()) : null;
                counts[4] += textBytes(n);
                if(attrs.isSymbolicLink())counts[4] += textBytes(Files.readSymbolicLink(path).toString());
                if(hardTarget != null)counts[4] += textBytes(hardTarget);
                if(counts[4]>MAX_METADATA_BYTES)throw new IOException("Export metadata exceeds limit");
                if(++counts[1]>MAX_ENTRIES)throw new IOException("Too many export entries");
                TarArchiveEntry e=attrs.isSymbolicLink()?new TarArchiveEntry(n,TarConstants.LF_SYMLINK):hardTarget!=null?new TarArchiveEntry(n,TarConstants.LF_LINK):new TarArchiveEntry(n+(attrs.isDirectory()?"/":""));
                if(hardTarget!=null)e.setLinkName(hardTarget);
                if(attrs.isSymbolicLink())e.setLinkName(Files.readSymbolicLink(path).toString());
                if(attrs.isRegularFile() && hardTarget==null){if(attrs.size()>MAX_BYTES-counts[0])throw new IOException("Export exceeds payload limit");e.setSize(attrs.size());}
                e.setMode(exportMode(path, attrs));e.setModTime(attrs.lastModifiedTime().toMillis());tar.putArchiveEntry(e);
                if(attrs.isRegularFile() && hardTarget==null)try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                    long remaining=attrs.size();
                    while(remaining>0){int count=in.read(buffer,0,(int)Math.min(remaining,buffer.length));if(count<=0)throw new EOFException("Export source truncated");tar.write(buffer,0,count);remaining-=count;counts[0]+=count;
                        if(progress!=null&&counts[0]-counts[2]>=1024*1024){progress.update(counts[0],counts[1]);counts[2]=counts[0];counts[3]=counts[1];}
                    }
                }tar.closeArchiveEntry();
                if(attrs.isRegularFile() && attrs.fileKey()!=null && hardTarget==null)fileKeys.put(attrs.fileKey(),n);
                if(progress!=null&&(counts[0]-counts[2]>=1024*1024||counts[1]-counts[3]>=256)){progress.update(counts[0],counts[1]);counts[2]=counts[0];counts[3]=counts[1];}
            }
        });
        TarArchiveEntry settings=new TarArchiveEntry("migration-settings.json");settings.setSize(settingsJson.length);tar.putArchiveEntry(settings);tar.write(settingsJson);tar.closeArchiveEntry();tar.finish();tar.flush();if(progress!=null)progress.update(counts[0]+settingsJson.length,counts[1]+1);
    }
    private static int exportMode(Path path, BasicFileAttributes attrs) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return attrs.isDirectory() ? 0755 : Files.isExecutable(path) ? 0755 : 0644;
        Set<PosixFilePermission> permissions;
        try { permissions = view.readAttributes().permissions(); }
        catch (UnsupportedOperationException unsupported) { return attrs.isDirectory() ? 0755 : Files.isExecutable(path) ? 0755 : 0644; }
        PosixFilePermission[] bits = {PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ};
        int value = 0;
        for (int i = 0; i < bits.length; i++) if (permissions.contains(bits[i])) value |= 1 << i;
        return value;
    }
    /** Shared allow-list for host sanitizers. Paths are relative to the app files directory. */
    public static boolean shouldExport(String relativePath, boolean directory, boolean symlink, boolean runtimeOnly) {
        String n;
        try { n = name(relativePath); } catch (IOException invalid) { return false; }
        if (excluded(n)) return false;
        String steam = "linuxfs/root/.local/share/Steam";
        if (under(n, steam + "/config") || under(n, steam + "/logs") ||
            under(n, "linuxfs/etc/machine-id") || under(n, "linuxfs/var/lib/dbus/machine-id") ||
            under(n, "linuxfs/var/log") || under(n, "linuxfs/root/.cache")) return false;
        String leaf = n.substring(n.lastIndexOf('/') + 1);
        if (leaf.startsWith("ssfn")) return false;
        if (under(n, "linuxfs/root/.steam")) return directory || symlink;
        if (!runtimeOnly) return true;
        if (n.equals("linuxfs/etc/shadow") || n.equals("linuxfs/etc/shadow-") ||
            n.equals("linuxfs/etc/gshadow") || n.equals("linuxfs/etc/gshadow-") ||
            under(n, "linuxfs/etc/ssl/private") || under(n, "linuxfs/etc/pacman.d/gnupg/private-keys-v1.d") ||
            (n.startsWith("linuxfs/etc/ssh/ssh_host_") && (n.endsWith("_key") || n.endsWith("_key.pub")))) return false;
        if (under(n, "graphics_driver") || under(n, "components")) return true;
        if (n.equals("linuxfs")) return directory;
        if (!n.startsWith("linuxfs/")) return false;
        if (n.equals("linuxfs/.version") || n.equals("linuxfs/.droiddeck-pkg-proton-arm64")) return !directory && !symlink;
        if (n.equals("linuxfs/root") || n.equals("linuxfs/root/.local") || n.equals("linuxfs/root/.local/share") || n.equals(steam)) return directory;
        if (n.startsWith("linuxfs/root/")) {
            if (!n.startsWith(steam + "/")) return false;
            String tail = n.substring(steam.length() + 1);
            if (tail.equals("steamapps/appmanifest_4427310.acf")) return !directory && !symlink;
            if (tail.equals("steamapps") || tail.equals("steamapps/common")) return directory;
            if (tail.startsWith("steamapps/common/")) {
                String software = tail.substring("steamapps/common/".length()).split("/", 2)[0];
                return software.startsWith("Proton");
            }
            for (String installed : new String[]{"ubuntu12_32", "ubuntu12_64", "linux32", "linux64", "bin", "public", "package", "resource", "clientui", "steam-runtime", "compatibilitytools.d", "steamrtarm64", "steamrtarm32", "steamrt32", "steamrt64", "linuxarm64", "androidarm64", "steamui", "steam", "tenfoot", "controller_base", "graphics", "legacycompat"})
                if (under(tail, installed)) return true;
            return !directory && !symlink && STEAM_ROOT_BINARIES.contains(tail);
        }
        String system = n.substring("linuxfs/".length()).split("/", 2)[0];
        return RUNTIME_SYSTEM_DIRS.contains(system);
    }
    private static boolean under(String path, String root) { return path.equals(root) || path.startsWith(root + "/"); }
    private static boolean excluded(String n) {
        if(n.equals("migration-settings.json"))return true;
        for(String prefix:new String[]{"session","logs",".wayland-rt","agent-inbox","directaudio","pulseaudio",".proton-install-rt","gpu-clock-pinned","linuxfs/data","linuxfs/storage","linuxfs/dev","linuxfs/proc","linuxfs/sys","linuxfs/run","linuxfs/tmp","linuxfs/root/Storage"})if(n.equals(prefix)||n.startsWith(prefix+"/"))return true;
        String lower=n.toLowerCase(Locale.ROOT);return lower.startsWith("stores/")&&(lower.endsWith("/credentials.json")||lower.contains("token")||lower.endsWith("/cookies.json"));
    }
}
