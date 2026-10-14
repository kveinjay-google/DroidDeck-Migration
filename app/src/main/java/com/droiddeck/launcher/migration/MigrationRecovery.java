package com.droiddeck.launcher.migration;
import android.content.Context;
import java.io.*;
import java.nio.file.*;
import org.json.*;
/** Called before Application creates providers or background runtime helpers. */
public final class MigrationRecovery {
 public static volatile String warning="";
 public static void recover(Context c){
  try{Path data=c.getDataDir().getCanonicalFile().toPath();if(DirectoryTransaction.recover(data)){
   Path pending=data.resolve("migration.pending-settings.json");
   if(Files.isRegularFile(pending)){JSONObject settings=BackupFiles.readJson(Files.newInputStream(pending));MigrationSettings.apply(c,settings);}
   DirectoryTransaction.complete(data);Files.deleteIfExists(pending);
  }}catch(Exception e){warning="上次恢复尚未完成："+e.getMessage();}
 }
 private MigrationRecovery(){}
}
