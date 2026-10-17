package com.droiddeck.launcher.migration;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One cancellable worker across activity recreation; no automatic overwrite on discovery. */
final class BackupSearchUi {
 static final int PERMISSION=107;
 private static final AtomicBoolean ACTIVE=new AtomicBoolean();
 private final Activity activity;private final Consumer<BackupSearch.Candidate> selected;private final Runnable manual;
 private volatile boolean destroyed,cancelled;private Thread worker;private AlertDialog dialog;
 BackupSearchUi(Activity activity,Consumer<BackupSearch.Candidate> selected,Runnable manual){this.activity=activity;this.selected=selected;this.manual=manual;}
 boolean running(){return worker!=null&&worker.isAlive();}
 void begin(){
  if(destroyed||activity.isFinishing())return;
  if(activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
   new AlertDialog.Builder(activity).setTitle("允许查找备份文件").setMessage("需要读取手机文件，才能自动查找夸克网盘下载的分卷备份。只读取目录和备份清单，不读取照片内容，也不会上传文件。").setPositiveButton("允许并搜索",(d,w)->activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION)).setNegativeButton("手动选择目录",(d,w)->manual.run()).show();return;
  }
  search();
 }
 void permissions(int request,int[] results){if(request!=PERMISSION)return;
  if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){search();return;}
  new AlertDialog.Builder(activity).setTitle("未获得文件读取权限").setMessage("自动搜索需要文件访问权限。允许后再次点击自动搜索；也可以直接手动选择备份目录。").setPositiveButton("应用权限设置",(d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){MigrationService.status="无法打开设置，请在系统设置中允许文件访问。";}}).setNeutralButton("手动选择目录",(d,w)->manual.run()).setNegativeButton("取消",null).show();
 }
 private void search(){
  if(destroyed||activity.isFinishing())return;
  if(MigrationService.busy.get()||!ACTIVE.compareAndSet(false,true)){message("已有搜索或导入正在运行，请等待结束。");return;}
  cancelled=false;dialog=new AlertDialog.Builder(activity).setTitle("正在自动查找备份").setMessage("优先查找夸克网盘下载目录…").setNegativeButton("停止搜索",(d,w)->cancel()).create();dialog.setOnCancelListener(d->cancel());dialog.show();
  worker=new Thread(()->{
   MigrationDiagnostics log=null;
   try{
    log=new MigrationDiagnostics(new File(activity.getNoBackupFilesDir(),"migration-diagnostics").toPath(),"search-volumes");log.stage("只读搜索：优先夸克下载目录");
    List<Path> roots=BackupStorage.roots(activity);for(Path root:roots)log.event("publicSearchRoot",root.toString());
    final long[] last={0};
    BackupSearch.Result result=BackupSearch.scan(roots,new BackupSearch.Limits(),()->cancelled,(dirs,entries)->{
     if(SystemClock.elapsedRealtime()-last[0]>500){last[0]=SystemClock.elapsedRealtime();post(()->{if(dialog!=null&&dialog.isShowing())dialog.setMessage("优先查找夸克网盘下载目录\n已检查 "+dirs+" 个目录、"+entries+" 个条目");});}
    });
    log.event("searchSummary","directories="+result.directories+"; entries="+result.entries+"; candidates="+result.candidates.size()+"; unreadable="+result.unreadable+"; cancelled="+result.cancelled+"; limited="+result.limited);
    for(BackupSearch.Candidate candidate:result.candidates)log.event("candidate","folder="+candidate.folder+"; ready="+candidate.ready+"; parts="+candidate.parts+"; bytes="+candidate.bytes+"; status="+candidate.reason);
    log.success();post(()->showResults(result));
   }catch(Exception e){if(log!=null)log.failure(e);post(()->{dismiss();MigrationService.status="自动搜索未完成，可查看或导出诊断日志。";message("自动搜索未完成："+e.getClass().getSimpleName()+"。可以手动选择目录，或导出诊断日志排查。");});}
   finally{ACTIVE.set(false);}
  },"migration-backup-search");worker.start();
 }
 void showResults(BackupSearch.Result result){
  dismiss();if(result.cancelled){MigrationService.status="搜索已取消，未执行导入。";return;}
  String coverage=(result.limited?"达到搜索上限，结果可能不完整。\n":"")+(result.unreadable>0?result.unreadable+" 个目录无法读取，结果可能不完整。\n":"");
  MigrationService.status="搜索完成：找到 "+result.candidates.size()+" 份备份。";
  if(result.candidates.isEmpty()){
   new AlertDialog.Builder(activity).setTitle("未找到分卷备份").setMessage(coverage+"已检查 "+result.directories+" 个目录。请确认夸克已把所有分卷和 backup.ddbackup.json 下载到手机，并放在同一个文件夹；也可以手动选择目录。").setPositiveButton("手动选择目录",(d,w)->manual.run()).setNegativeButton("关闭",null).show();return;
  }
  if(!coverage.isEmpty())MigrationService.status=coverage+MigrationService.status;
  if(result.candidates.size()==1&&result.candidates.get(0).ready){selected.accept(result.candidates.get(0));return;}
  String[] labels=new String[result.candidates.size()];for(int i=0;i<labels.length;i++){BackupSearch.Candidate c=result.candidates.get(i);labels[i]=(c.ready?"可导入":"待补齐/检查")+" · "+c.parts+" 卷 · "+MigrationService.format(c.bytes)+"\n"+c.folder+"\n"+c.reason;}
  AlertDialog.Builder list=new AlertDialog.Builder(activity).setTitle("找到 "+labels.length+" 份备份，请选择");
  list.setItems(labels,(d,which)->{BackupSearch.Candidate c=result.candidates.get(which);if(!c.ready){message(c.folder+"\n"+c.reason+"\n请下载完整后重新搜索。");return;}selected.accept(c);}).setNeutralButton("手动选择目录",(d,w)->manual.run()).setNegativeButton("取消",null).show();
 }
 private void post(Runnable action){activity.runOnUiThread(()->{if(!destroyed&&!activity.isFinishing())action.run();});}
 private void message(String message){new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("知道了",null).show();}
 private void dismiss(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();dialog=null;}
 private void cancel(){cancelled=true;if(worker!=null)worker.interrupt();}
 void close(){destroyed=true;cancel();dismiss();}
}
