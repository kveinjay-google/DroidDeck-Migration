package com.droiddeck.launcher.migration;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class)
public class BackupSearchTest {
 @Test public void quarkSubtreeIsSearchedBeforeGeneralDownloadsAndDocuments()throws Exception{
  Path root=Files.createTempDirectory("search");Path wanted=backup(root.resolve("Download/QuarkDownloads/deep/backup"));backup(root.resolve("Documents/general"));
  BackupSearch.Result r=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(20000,200000,4096,32,1,90000),()->false,null);
  assertEquals(1,r.candidates.size());assertEquals(wanted,r.candidates.get(0).folder);
 }
 private Path backup(Path folder)throws Exception{
  Files.createDirectories(folder);Files.write(folder.resolve("part-0001.bin"),new byte[]{1,2});
  JSONObject m=new JSONObject().put("format","droiddeck-migration").put("version",1).put("bytes",2).put("sha256","0".repeat(64)).put("parts",new JSONArray().put(new JSONObject().put("name","part-0001.bin").put("bytes",2).put("sha256","0".repeat(64))));
  Files.write(folder.resolve("backup.ddbackup.json"),m.toString().getBytes(StandardCharsets.UTF_8));return folder;
 }
 @Test public void discoversArbitraryNestedNamesWithoutChangingFiles()throws Exception{
  Path root=Files.createTempDirectory("search");Path a=backup(root.resolve("Download/中文\\名字"));Path b=backup(root.resolve("Documents/nested/other"));
  BackupSearch.Result result=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->false,null);
  assertEquals(2,result.candidates.size());assertTrue(result.candidates.stream().allMatch(c->c.ready));assertTrue(Files.exists(a.resolve("part-0001.bin")));assertTrue(Files.exists(b.resolve("backup.ddbackup.json")));
 }
 @Test public void reportsMissingAndWrongLengthPartsInsteadOfImporting()throws Exception{
  Path root=Files.createTempDirectory("search");Path a=backup(root.resolve("a"));Files.delete(a.resolve("part-0001.bin"));Path b=backup(root.resolve("b"));Files.write(b.resolve("part-0001.bin"),new byte[]{1});
  BackupSearch.Result r=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->false,null);
  assertEquals(2,r.candidates.size());assertTrue(r.candidates.stream().noneMatch(c->c.ready));assertTrue(r.candidates.stream().anyMatch(c->c.reason.contains("缺少")));assertTrue(r.candidates.stream().anyMatch(c->c.reason.contains("大小")));
 }
 @Test public void neverFollowsSymlinksOrAndroidPrivateDirs()throws Exception{
  Path root=Files.createTempDirectory("search");Path outside=backup(Files.createTempDirectory("outside"));Files.createSymbolicLink(root.resolve("alias"),outside);backup(root.resolve("Android/data/private/backup"));
  BackupSearch.Result r=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->false,null);assertEquals(0,r.candidates.size());
 }
 @Test public void preferredPathCannotFollowLinkedDownloadAncestor()throws Exception{
  Path root=Files.createTempDirectory("search");backup(root.resolve("Android/data/QuarkDownloads/backup"));Files.createSymbolicLink(root.resolve("Download"),root.resolve("Android/data"));
  BackupSearch.Result r=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->false,null);assertEquals(0,r.candidates.size());
 }
 @Test public void cancellationAndLimitsAreExplicit()throws Exception{
  Path root=Files.createTempDirectory("search");backup(root.resolve("a/b"));
  assertTrue(BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->true,null).cancelled);
  assertTrue(BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(1,100,10,10,10,90000),()->false,null).limited);
 }
 @Test public void malformedManifestIsVisibleWithoutDumpingItsContents()throws Exception{
  Path root=Files.createTempDirectory("search");Files.write(root.resolve("backup.ddbackup.json"),"{\"password\":\"PRIVATE-SENTINEL\", broken".getBytes(StandardCharsets.UTF_8));
  BackupSearch.Result r=BackupSearch.scan(Collections.singletonList(root),new BackupSearch.Limits(),()->false,null);assertEquals(1,r.candidates.size());assertFalse(r.candidates.get(0).ready);assertFalse(r.candidates.get(0).reason.contains("PRIVATE-SENTINEL"));
 }
 @Test public void linkedManifestOrVolumeCannotBeSelected()throws Exception{
  Path root=Files.createTempDirectory("search");Path a=backup(root.resolve("a"));Path b=Files.createTempDirectory("linked");Files.createSymbolicLink(b.resolve("backup.ddbackup.json"),a.resolve("backup.ddbackup.json"));assertNull(BackupSearch.inspect(b));
  Files.delete(a.resolve("part-0001.bin"));Files.createSymbolicLink(a.resolve("part-0001.bin"),b.resolve("other"));assertFalse(BackupSearch.inspect(a).ready);
 }
}
