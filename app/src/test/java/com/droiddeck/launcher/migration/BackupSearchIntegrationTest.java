package com.droiddeck.launcher.migration;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.Manifest;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class)
public class BackupSearchIntegrationTest {
 @Test public void singleDiscoveredBackupStillRequiresRestoreConfirmation()throws Exception{
  MigrationService.busy.set(false);
  try(org.robolectric.android.controller.ActivityController<MigrationActivity> controller=Robolectric.buildActivity(MigrationActivity.class).setup()){
   MigrationActivity activity=controller.get();BackupSearchUi ui=new BackupSearchUi(activity,activity::confirmSearchCandidate,()->{});
   BackupSearch.Result result=new BackupSearch.Result();Path folder=Files.createTempDirectory("chosen-backup");result.candidates.add(new BackupSearch.Candidate(folder,true,"ready",5,100));ui.showResults(result);
   AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains(folder.toString()));assertNull(shadowOf(activity).getNextStartedService());
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();Intent restore=shadowOf(activity).getNextStartedService();assertNotNull(restore);assertEquals("restore-volumes",restore.getStringExtra("operation"));assertEquals(Uri.fromFile(folder.toFile()).toString(),restore.getStringExtra("uri"));
  }
 }
 private Context storage(Path root){return new ContextWrapper(RuntimeEnvironment.getApplication()){@Override public File[] getExternalFilesDirs(String type){return new File[]{root.resolve("Android/data/com.droiddeck.migration/files").toFile()};}};}
 private JSONObject writeBackup(Path folder)throws Exception {
  Files.createDirectories(folder);VolumeStreams.Writer out=new VolumeStreams.Writer(2,name->Files.newOutputStream(folder.resolve(name)));out.write(new byte[]{1,2,3});out.close();JSONObject m=BackupFiles.manifest(out,new JSONObject(),3);Files.write(folder.resolve("backup.ddbackup.json"),m.toString().getBytes(StandardCharsets.UTF_8));return m;
 }
 @Test public void discoveredPublicFolderFeedsOriginalVerifiedVolumeReader()throws Exception{
  Path root=Files.createTempDirectory("public-storage");Path folder=root.resolve("Download/QuarkDownloads/backup");JSONObject manifest=writeBackup(folder);assertTrue(BackupSearch.inspect(folder).ready);
  BackupFiles files=new BackupFiles(storage(root),Uri.fromFile(folder.toFile()));Map<String,Uri> children=files.children();
  try(InputStream in=files.openVolumes(manifest,children)){assertArrayEquals(new byte[]{1,2,3},in.readAllBytes());}
  Files.write(folder.resolve("part-0002.bin"),new byte[]{9});
  try(InputStream in=files.openVolumes(manifest,files.children())){assertThrows(IOException.class,()->in.readAllBytes());}
 }
 @Test public void localRestoreRejectsPrivateOrChangedSymlinkSource()throws Exception{
  Path root=Files.createTempDirectory("public-storage"),folder=root.resolve("backup");writeBackup(folder);Context c=storage(root);
  assertThrows(IOException.class,()->new BackupFiles(c,Uri.fromFile(Files.createTempDirectory("outside-public").toFile())));
  BackupFiles files=new BackupFiles(c,Uri.fromFile(folder.toFile()));Map<String,Uri> children=files.children();Path outside=Files.createTempFile("outside-content","bin");Files.delete(folder.resolve("part-0001.bin"));Files.createSymbolicLink(folder.resolve("part-0001.bin"),outside);
  assertThrows(IOException.class,()->files.open(children.get("part-0001.bin")));
 }
 private Button find(View view,String label){if(view instanceof Button&&((Button)view).getText().toString().equals(label))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){Button found=find(group.getChildAt(n),label);if(found!=null)return found;}}return null;}
 @Test public void searchExplainsPermissionAndRetainsManualPicker()throws Exception{
  MigrationService.busy.set(false);shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
  try(org.robolectric.android.controller.ActivityController<MigrationActivity> controller=Robolectric.buildActivity(MigrationActivity.class).setup()){
   MigrationActivity activity=controller.get();find(activity.getWindow().getDecorView(),"自动搜索备份并导入").performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
   assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("夸克"));dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();
   Intent manual=shadowOf(activity).getNextStartedActivity();assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,manual.getAction());
  }
 }
}
