package com.droiddeck.launcher.migration;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import com.droiddeck.launcher.migration.archive.MigrationArchive;
import com.droiddeck.launcher.session.SessionState;
import com.droiddeck.launcher.runtime.LinuxRuntimeInstaller;

public final class MigrationService extends Service {
 public static final AtomicBoolean busy=new AtomicBoolean();
 public static volatile String status="请选择备份，或备份当前环境。";
 public static volatile long doneBytes,totalBytes;public static volatile boolean restartRequired;
 private Thread worker;private PowerManager.WakeLock wake;private long lastNotice,deadline;
 private static final int NOTICE=7401;
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if(!busy.compareAndSet(false,true))return START_NOT_STICKY;
  try{
   if(SessionState.INSTANCE.getRunning()||(SessionState.INSTANCE.getPhase()!=com.droiddeck.launcher.session.SessionPhase.IDLE&&SessionState.INSTANCE.getPhase()!=com.droiddeck.launcher.session.SessionPhase.FAILED)||LinuxRuntimeInstaller.isBusy())throw new IOException("请先结束游戏和运行时安装，再进行备份/恢复");
   if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("migration","备份与恢复",NotificationManager.IMPORTANCE_LOW));
   status="正在准备…";doneBytes=0;totalBytes=0;deadline=System.currentTimeMillis()+30*60*1000;
   startForeground(NOTICE,notification());
   wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DroidDeck:Migration");wake.acquire(30*60*1000L);
   String action=intent.getStringExtra("operation");Uri uri=Uri.parse(intent.getStringExtra("uri"));String settingsUri=intent.getStringExtra("settings");
   worker=new Thread(()->{
    try{if("backup".equals(action)||"share-runtime".equals(action))backup(uri,"share-runtime".equals(action));else if("restore-volumes".equals(action)||"restore-tar".equals(action))restore(uri,"restore-volumes".equals(action),settingsUri);else throw new IOException("未知任务");}
    catch(Exception e){status="操作未完成："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    finally{busy.set(false);if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);stopSelf();}
   },"migration-worker");worker.start();
  }catch(Exception e){status="无法开始："+e.getMessage();busy.set(false);stopSelf();}
  return START_NOT_STICKY;
 }
 private Notification notification(){Intent open=new Intent(this,MigrationActivity.class);PendingIntent p=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"migration"):new Notification.Builder(this);
  b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("DroidDeck 迁移版").setContentText(status).setContentIntent(p).setOngoing(true);
  if(totalBytes>0)b.setProgress(100,(int)Math.min(100,doneBytes*100/totalBytes),false);else b.setProgress(0,0,true);return b.build();
 }
 private void progress(long bytes,long entries){
  if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>deadline)throw new java.util.concurrent.CancellationException("任务已取消或超过 30 分钟，可重新开始");
  doneBytes=bytes;if(System.currentTimeMillis()-lastNotice>500){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notification());lastNotice=System.currentTimeMillis();}
 }
 private InputStream open(Uri uri)throws IOException{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException("无法读取选中的文件");return new BufferedInputStream(in,65536);}
 private interface InputFactory{InputStream open()throws Exception;}
 private void finishRead(InputStream in)throws IOException{byte[] b=new byte[65536];while(in.read(b)!=-1)progress(doneBytes,0);}
 private void restore(Uri uri,boolean volumes,String settingsUri)throws Exception{
  Path data=getDataDir().getCanonicalFile().toPath(),stage=data.resolve("migration.stage");
  if(Files.exists(data.resolve("migration.transaction")))throw new IOException("请关闭并重新打开应用，以完成上次恢复");
  if(Files.exists(data.resolve("migration.previous")))throw new IOException("请先释放上次恢复的回滚副本");
  InputFactory input;JSONObject settings=new JSONObject();
  if(volumes){BackupFiles files=new BackupFiles(this,uri);Map<String,Uri> children=files.children();Uri index=children.get("backup.ddbackup.json");if(index==null)throw new IOException("请选择含 backup.ddbackup.json 的备份文件夹");JSONObject manifest=BackupFiles.readJson(files.open(index));settings=manifest.optJSONObject("settings");if(settings==null)settings=new JSONObject();input=()->files.openVolumes(manifest,children);}
  else input=()->open(uri);
  if(settingsUri!=null)settings=BackupFiles.readJson(open(Uri.parse(settingsUri)));
  settings=MigrationSettings.validate(settings);
  status="检查备份完整性（第一遍）…";
  MigrationArchive.Inspection scan;try(InputStream in=input.open()){scan=MigrationArchive.inspect(in,MigrationArchive.MAX_BYTES,this::progress);finishRead(in);}
  long required=Math.addExact(scan.unpackedBytes,Math.max(512L*1024*1024,Math.multiplyExact(scan.entries,8192L)));
  if(getDataDir().getUsableSpace()<required)throw new IOException("空间不足：需至少 "+format(required)+" 空闲空间（保留现有环境）");
  totalBytes=scan.unpackedBytes;doneBytes=0;status="恢复到临时目录（第二遍）…";
  deletePrivate(stage);Files.createDirectory(stage);
  try(InputStream in=input.open()){MigrationArchive.extract(in,stage,scan.unpackedBytes,this::progress);finishRead(in);}
  Path embedded=stage.resolve("migration-settings.json");
  if(settings.length()==0&&Files.isRegularFile(embedded,LinkOption.NOFOLLOW_LINKS))settings=MigrationSettings.validate(BackupFiles.readJson(Files.newInputStream(embedded)));
  Files.deleteIfExists(embedded);
  Path runtime=stage.resolve("linuxfs");String version=RuntimePaths.version(runtime);
  if(!version.matches("r[0-9]{1,4}"))throw new IOException("运行时版本标记无效");
  if(!Files.isExecutable(RuntimePaths.guestTarget(runtime,"usr/bin/gamescope")))throw new IOException("运行时缺少可执行权限");
  status="调整设备路径与运行目录…";prepareRuntime(stage);
  // Sync portable settings before the durable directory switch. Recovery reapplies them on boot.
  Path pending=data.resolve("migration.pending-settings.json");try(FileOutputStream out=new FileOutputStream(pending.toFile())){out.write(settings.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
  status="启用恢复的环境…";DirectoryTransaction.promote(data);restartRequired=true;
  MigrationSettings.apply(this,settings);DirectoryTransaction.complete(data);Files.deleteIfExists(pending);
  restartRequired=true;status="恢复完成（"+version+"）。请关闭应用并从手机桌面重新打开；账号可能需要重新登录。";
 }
 private void prepareRuntime(Path stage)throws IOException{
  Path root=stage.resolve("linuxfs");for(String name:new String[]{"dev","proc","sys","run","tmp","storage","data","root"})RuntimePaths.directory(root,name);
  String old="/data/user/0/com.droiddeck.launcher/files",newPath=getFilesDir().getAbsolutePath();
  Files.walkFileTree(stage,new SimpleFileVisitor<Path>(){
   @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{if(attrs.isSymbolicLink()){
    String target=Files.readSymbolicLink(file).toString();if(target.startsWith(old+"/")||target.equals(old)){Files.delete(file);Files.createSymbolicLink(file,Paths.get(newPath+target.substring(old.length())));}
   }return FileVisitResult.CONTINUE;}
  });
  RuntimePaths.directory(root,"usr/local/bin");RuntimePaths.directory(root,"etc");
  try(InputStream helper=getAssets().open("linuxfs/usr/local/bin/droiddeck-bwrap")){Files.copy(helper,root.resolve("usr/local/bin/droiddeck-bwrap"),StandardCopyOption.REPLACE_EXISTING);root.resolve("usr/local/bin/droiddeck-bwrap").toFile().setExecutable(true,false);}
  Path machineId=root.resolve("etc/machine-id");if(!Files.exists(machineId,LinkOption.NOFOLLOW_LINKS))Files.write(machineId,(java.util.UUID.randomUUID().toString().replace("-","")+"\n").getBytes(StandardCharsets.UTF_8));
  Path storage=root.resolve("root/Storage");if(!Files.exists(storage,LinkOption.NOFOLLOW_LINKS))Files.createSymbolicLink(storage,Paths.get("/storage/emulated/0"));
  // Stale credential envelopes cannot be opened using this device's Keystore.
  Path stores=stage.resolve("stores");if(Files.isDirectory(stores,LinkOption.NOFOLLOW_LINKS))Files.walkFileTree(stores,new SimpleFileVisitor<Path>(){@Override public FileVisitResult visitFile(Path f,BasicFileAttributes a)throws IOException{if(f.getFileName().toString().equals("credentials.json"))Files.delete(f);return FileVisitResult.CONTINUE;}});
 }
 private void backup(Uri tree,boolean runtimeOnly)throws Exception{
  if(!Files.isExecutable(RuntimePaths.guestTarget(getFilesDir().getCanonicalFile().toPath().resolve("linuxfs"),"usr/bin/gamescope")))throw new IOException("当前迁移版还没有运行环境，请先恢复或安装 Linux");
  status=runtimeOnly?"导出运行环境分享包（不含账号/存档）…":"个人备份（保留游戏及存档），每卷最多 2 GB…";BackupFiles files=new BackupFiles(this,tree);Uri folder=files.createFolder("DroidDeck-Backup-"+System.currentTimeMillis());JSONObject settings=runtimeOnly?new JSONObject():MigrationSettings.capture(this);
  VolumeStreams.Writer out=new VolumeStreams.Writer(VolumeStreams.DEFAULT_PART_BYTES,name->files.create(folder,name,"application/octet-stream"));
  try{MigrationArchive.write(getFilesDir().getCanonicalFile().toPath(),out,settings.toString().getBytes(StandardCharsets.UTF_8),this::progress,runtimeOnly);out.close();}
  catch(Exception e){try{out.close();}catch(IOException ignored){}throw e;}
  JSONObject index=BackupFiles.manifest(out,settings,doneBytes).put("kind",runtimeOnly?"runtime-only":"personal");try(OutputStream stream=files.create(folder,"backup.ddbackup.json","application/json")){stream.write(index.toString(2).getBytes(StandardCharsets.UTF_8));}
  status="备份完成，共 "+out.parts.size()+" 卷。请把整个备份文件夹一起传到新手机。";
 }
 public static void deletePrivate(Path path)throws IOException{
  if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return;
  Files.walkFileTree(path,new SimpleFileVisitor<Path>(){public FileVisitResult visitFile(Path f,BasicFileAttributes a)throws IOException{Files.delete(f);return FileVisitResult.CONTINUE;}public FileVisitResult postVisitDirectory(Path d,IOException e)throws IOException{if(e!=null)throw e;Files.delete(d);return FileVisitResult.CONTINUE;}});
 }
 public static String format(long bytes){return String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0);}
 @Override public void onDestroy(){if(worker!=null&&worker.isAlive()&&Thread.currentThread()!=worker)worker.interrupt();super.onDestroy();}
}
