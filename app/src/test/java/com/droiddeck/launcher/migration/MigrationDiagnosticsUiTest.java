package com.droiddeck.launcher.migration;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class)
public class MigrationDiagnosticsUiTest {
 @Test public void jsonParserCannotLeakDocumentContentsIntoReport() throws Exception {
  java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("json-report");
  MigrationDiagnostics log=new MigrationDiagnostics(dir,"restore-volumes");
  try{BackupFiles.readJson(new ByteArrayInputStream("{\"password\":\"PRIVATE-CONTENT-732\", broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)));fail("Expected malformed JSON");}
  catch(Exception e){log.failure(new IOException("manifest parse failed",e));}
  String report=MigrationDiagnostics.latest(dir);assertTrue(report.contains("org.json.JSONException"));assertFalse(report,report.contains("PRIVATE-CONTENT-732"));
 }
 private Button find(View view,String label){if(view instanceof Button&&((Button)view).getText().toString().equals(label))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){Button found=find(group.getChildAt(n),label);if(found!=null)return found;}}return null;}
 @Test public void reportRemainsViewableAndExportUsesDocumentPicker() throws Exception {
  MigrationService.busy.set(false);MigrationRecovery.warning="";
  try(org.robolectric.android.controller.ActivityController<MigrationActivity> controller=Robolectric.buildActivity(MigrationActivity.class).setup()){
   MigrationActivity activity=controller.get();
   MigrationDiagnostics log=new MigrationDiagnostics(new File(activity.getNoBackupFilesDir(),"migration-diagnostics").toPath(),"restore-volumes");log.stage("archive-inspect");log.failure(new IOException("bad entry ../escape"));
   Button view=find(activity.getWindow().getDecorView(),"查看上次日志");assertNotNull(view);view.performClick();
   AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());dialog.dismiss();
   find(activity.getWindow().getDecorView(),"导出诊断日志").performClick();
   Intent intent=shadowOf(activity).getNextStartedActivity();assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.getAction());assertEquals("text/plain",intent.getType());assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE).endsWith(".txt"));
  }
 }
}
