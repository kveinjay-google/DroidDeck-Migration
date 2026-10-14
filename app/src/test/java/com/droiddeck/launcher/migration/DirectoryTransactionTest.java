package com.droiddeck.launcher.migration;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectoryTransactionTest {
 private Path setup()throws Exception{Path d=Files.createTempDirectory("migration-tx");Files.createDirectories(d.resolve("files"));Files.write(d.resolve("files/old"),new byte[]{1});Files.createDirectories(d.resolve("migration.stage"));Files.write(d.resolve("migration.stage/new"),new byte[]{2});return d;}
 @Test public void commitRetainsOldAndPromotesNew()throws Exception{Path d=setup();DirectoryTransaction.promote(d);assertTrue(Files.exists(d.resolve("files/new")));assertTrue(Files.exists(d.resolve("migration.previous/old")));assertTrue(DirectoryTransaction.recover(d));DirectoryTransaction.complete(d);assertFalse(Files.exists(d.resolve("migration.transaction")));}
 @Test public void interruptedAfterOldRenameRestoresOld()throws Exception{Path d=setup();Files.write(d.resolve("migration.transaction"),"prepared".getBytes());Files.move(d.resolve("files"),d.resolve("migration.previous"));assertFalse(DirectoryTransaction.recover(d));assertTrue(Files.exists(d.resolve("files/old")));}
 @Test public void interruptedAfterPromotionRollsBackUncommittedNew()throws Exception{Path d=setup();Files.write(d.resolve("migration.transaction"),"prepared".getBytes());Files.move(d.resolve("files"),d.resolve("migration.previous"));Files.move(d.resolve("migration.stage"),d.resolve("files"));assertFalse(DirectoryTransaction.recover(d));assertTrue(Files.exists(d.resolve("files/old")));assertTrue(Files.exists(d.resolve("migration.failed/new")));}
 @Test public void refusesToReplaceExistingRollbackCopy()throws Exception{Path d=setup();Files.createDirectory(d.resolve("migration.previous"));assertThrows(java.io.IOException.class,()->DirectoryTransaction.promote(d));assertTrue(Files.exists(d.resolve("files/old")));}
}
