package com.droiddeck.launcher.migration;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuntimePathsTest {
 @Test public void resolvesAbsoluteGuestLinkInsideRuntime()throws Exception{Path p=Files.createTempDirectory("guest-links");Files.createDirectories(p.resolve("usr/bin"));Files.write(p.resolve("usr/bin/game-real"),new byte[]{1});Files.createSymbolicLink(p.resolve("usr/bin/gamescope"),Paths.get("/usr/bin/game-real"));assertEquals(p.resolve("usr/bin/game-real"),RuntimePaths.guestTarget(p,"usr/bin/gamescope"));}
 @Test public void rejectsLinkEscapingRuntime()throws Exception{Path p=Files.createTempDirectory("guest-escape");Files.createSymbolicLink(p.resolve("bad"),Paths.get("../../outside"));assertThrows(java.io.IOException.class,()->RuntimePaths.guestTarget(p,"bad"));}
 @Test public void mountPreparationCannotFollowArchiveLink()throws Exception{Path p=Files.createTempDirectory("guest-mount"),outside=Files.createTempDirectory("outside");Files.createSymbolicLink(p.resolve("root"),outside);assertThrows(java.io.IOException.class,()->RuntimePaths.directory(p,"root/Storage"));assertFalse(Files.exists(outside.resolve("Storage")));}
 @Test public void versionReadIsBounded()throws Exception{Path p=Files.createTempDirectory("guest-version");Files.write(p.resolve(".version"),new byte[65]);assertThrows(java.io.IOException.class,()->RuntimePaths.version(p));Files.write(p.resolve(".version"),"r9".getBytes());assertEquals("r9",RuntimePaths.version(p));}
}
