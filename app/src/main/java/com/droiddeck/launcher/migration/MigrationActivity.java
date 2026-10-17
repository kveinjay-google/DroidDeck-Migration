package com.droiddeck.launcher.migration;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import android.view.*;
import java.io.File;
import com.droiddeck.launcher.MainActivity;

/** Phone-native gateway: restore/backup here, open the full upstream game client separately. */
public final class MigrationActivity extends Activity {
 private BackupSearchUi search;
 private String exportSnapshot;private TextView progress,settingsLabel;private Uri settings;private final Handler timer=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){if(progress!=null){String text=MigrationService.status;if(MigrationService.busy.get())text+="\n"+MigrationService.format(MigrationService.doneBytes)+(MigrationService.totalBytes>0?" / "+MigrationService.format(MigrationService.totalBytes):"");progress.setText(text);}timer.postDelayed(this,500);}};
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));return t;}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);if(saved!=null&&saved.getString("settings")!=null)settings=Uri.parse(saved.getString("settings"));
  getWindow().setStatusBarColor(Color.rgb(18,24,31));getWindow().setNavigationBarColor(Color.rgb(18,24,31));
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(18,24,31));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(20),dp(24),dp(30));scroll.addView(body);setContentView(scroll);
  body.addView(text("DroidDeck 迁移版",26,Color.WHITE));body.addView(text("手机备份与恢复 · "+com.droiddeck.launcher.BuildConfig.VERSION_NAME,14,Color.rgb(158,173,190)));
  body.addView(text("无需电脑或 Root。首次迁移先导入备份，再打开 DroidDeck。分卷备份需要把清单和所有卷放在同一个文件夹。",16,Color.rgb(210,220,232)));
  search=new BackupSearchUi(this,this::confirmSearchCandidate,()->chooseTree(101));
  button(body,"自动搜索备份并导入",()->search.begin());
  body.addView(text("优先查找夸克网盘下载目录。找到后可选择备份，再确认导入。",14,Color.rgb(158,173,190)));
  button(body,"恢复分卷备份（手动选择目录）",()->chooseTree(101));button(body,"导入旧 TAR 备份",()->chooseFile(102));
  button(body,"选择旧版设置 JSON（可选）",()->chooseFile(103));settingsLabel=text(settings==null?"旧 TAR 的设置可以另行选择；新分卷备份已包含设置。":"已选择设置 JSON",14,Color.rgb(158,173,190));body.addView(settingsLabel);
  button(body,"个人备份（包含存档，2 GB 分卷）",()->chooseTree(104));
  button(body,"导出运行环境分享包（不含账号和存档）",()->chooseTree(105));
  progress=text(MigrationService.status,16,Color.rgb(94,197,255));body.addView(progress);
  button(body,"查看上次日志",()->showReport());
  button(body,"导出诊断日志",()->{try{exportSnapshot=readReport();Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"DroidDeck-migration-diagnostic-"+System.currentTimeMillis()+".txt");startActivityForResult(save,106);}catch(Exception e){message("无法读取日志："+e.getMessage());}});
  button(body,"打开 DroidDeck",()->{if(MigrationService.busy.get()){message("请等待备份/恢复完成");return;}if(MigrationService.restartRequired){new AlertDialog.Builder(this).setTitle("恢复已完成").setMessage("为加载新的运行环境，请关闭迁移版，再从手机桌面重新打开。官方版及其数据不受影响。").setPositiveButton("关闭迁移版",(d,w)->{finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());}).setNegativeButton("稍后",null).show();return;}if(!MigrationRecovery.warning.isEmpty()){message(MigrationRecovery.warning);return;}startActivity(new Intent(this,MainActivity.class));});
  button(body,"释放上次恢复的回滚副本",()->{if(MigrationService.busy.get()){message("请等待当前任务完成");return;}File old=new File(getDataDir(),"migration.previous");if(!old.exists()){message("当前没有回滚副本");return;}new AlertDialog.Builder(this).setTitle("释放回滚副本？").setMessage("只删除迁移版上次恢复前保留的副本，不删除当前环境、备份文件或官方版数据。请先确认恢复的环境能够使用。").setNegativeButton("保留",null).setPositiveButton("释放副本",(d,w)->{if(!MigrationService.busy.compareAndSet(false,true)){message("请等待当前任务完成");return;}new Thread(()->{try{MigrationService.deletePrivate(old.toPath());runOnUiThread(()->message("已释放回滚副本"));}catch(Exception e){runOnUiThread(()->message("无法释放："+e.getMessage()));}finally{MigrationService.busy.set(false);}},"migration-cleanup").start();}).show();});
  body.addView(text("导入失败后可查看或导出最近一次诊断日志；日志包含文件路径，分享前请先检查。日志仅保存在本机，不会自动上传。",14,Color.rgb(158,173,190)));
  body.addView(text("说明：本版本与官方版并存，不能使用官方 APK 覆盖升级。个人备份包含私有环境、游戏与存档，仅供自己迁移；分享包只保留运行环境，不包含个人设置。共享存储/SD 卡中的游戏文件需要另外复制。商店登录、驱动选择和系统权限需要在新手机重新确认。",14,Color.rgb(158,173,190)));
  if(!MigrationRecovery.warning.isEmpty())body.addView(text(MigrationRecovery.warning,16,Color.rgb(255,171,110)));
 }
 private void button(LinearLayout body,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(58));layout.topMargin=dp(10);b.setLayoutParams(layout);b.setOnClickListener(v->{if(MigrationService.busy.get()||(search!=null&&search.running())){message("已有搜索/备份/恢复正在运行");return;}action.run();});body.addView(b);}
 private void chooseTree(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
 private void chooseFile(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
 @Override protected void onActivityResult(int request,int result,Intent resultData){super.onActivityResult(request,result,resultData);if(result!=RESULT_OK||resultData==null||resultData.getData()==null)return;Uri uri=resultData.getData();if(request==106){try{
   String report=exportSnapshot==null?readReport():exportSnapshot;
   try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new java.io.IOException("无法写入所选位置");out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   message("诊断日志已导出，可以把该文本文件发来排查。");
  }catch(Exception e){message("日志导出失败："+e.getMessage());}finally{exportSnapshot=null;}return;}try{getContentResolver().takePersistableUriPermission(uri,resultData.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(SecurityException ignored){}
  if(request==103){settings=uri;settingsLabel.setText("已选择设置 JSON");return;}
  String op=request==101?"restore-volumes":request==102?"restore-tar":request==105?"share-runtime":"backup";
  if(op.startsWith("restore-"))confirmRestore(op,uri,"");else start(op,uri);
 }
 private void confirmRestore(String op,Uri uri,String details){new AlertDialog.Builder(this).setTitle("恢复选中的备份？").setMessage(details+"备份会先校验，再恢复到迁移版的私有目录。现有环境保留为回滚副本；官方版及其数据不会被覆盖。恢复过程中请保持手机有电。").setNegativeButton("取消",null).setPositiveButton("开始恢复",(d,w)->start(op,uri)).show();}
 void confirmSearchCandidate(BackupSearch.Candidate candidate){confirmRestore("restore-volumes",Uri.fromFile(candidate.folder.toFile()),"找到备份："+candidate.folder+"\n"+candidate.parts+" 卷，"+MigrationService.format(candidate.bytes)+"。\n");}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(search!=null)search.permissions(request,results);}
 @Override protected void onDestroy(){if(search!=null)search.close();super.onDestroy();}
 private void start(String op,Uri uri){Intent i=new Intent(this,MigrationService.class).putExtra("operation",op).putExtra("uri",uri.toString());if(settings!=null&&op.startsWith("restore-"))i.putExtra("settings",settings.toString());try{startForegroundService(i);}catch(Exception e){message("无法开始："+e.getMessage());}}
 private String readReport()throws java.io.IOException{return MigrationDiagnostics.latest(new File(getNoBackupFilesDir(),"migration-diagnostics").toPath());}
 private void showReport(){try{TextView report=text(readReport(),12,Color.WHITE);report.setTextIsSelectable(true);ScrollView view=new ScrollView(this);view.setBackgroundColor(Color.rgb(18,24,31));view.setPadding(dp(12),dp(8),dp(12),dp(8));view.addView(report);new AlertDialog.Builder(this).setTitle("最近一次迁移诊断日志").setView(view).setPositiveButton("关闭",null).show();}catch(Exception e){message("无法读取日志："+e.getMessage());}}
 private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("知道了",null).show();}
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(settings!=null)b.putString("settings",settings.toString());}
 @Override protected void onResume(){super.onResume();timer.post(refresh);}
 @Override protected void onPause(){timer.removeCallbacks(refresh);super.onPause();}
}
