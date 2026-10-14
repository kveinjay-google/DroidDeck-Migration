package com.droiddeck.launcher.migration;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Durable rename journal. A prepared transaction always rolls back after an interruption. */
public final class DirectoryTransaction {
 private DirectoryTransaction(){}
 private static void journal(Path data,String value)throws IOException{
  Path temp=data.resolve("migration.transaction.tmp");
  try(FileOutputStream f=new FileOutputStream(temp.toFile())){f.write(value.getBytes(StandardCharsets.UTF_8));f.getFD().sync();}
  Files.move(temp,data.resolve("migration.transaction"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
 }
 public static void promote(Path data)throws IOException{
  Path live=data.resolve("files"),stage=data.resolve("migration.stage"),old=data.resolve("migration.previous");
  if(!Files.isDirectory(stage,LinkOption.NOFOLLOW_LINKS)||Files.exists(old,LinkOption.NOFOLLOW_LINKS))throw new IOException("请先释放上次恢复的回滚副本");
  journal(data,"prepared");
  try{
   if(Files.exists(live,LinkOption.NOFOLLOW_LINKS))Files.move(live,old,StandardCopyOption.ATOMIC_MOVE);
   Files.move(stage,live,StandardCopyOption.ATOMIC_MOVE);journal(data,"committed");
  }catch(IOException failure){try{recover(data);}catch(IOException recovery){failure.addSuppressed(recovery);}throw failure;}
 }
 /** True means promoted files need their pending settings applied. */
 public static boolean recover(Path data)throws IOException{
  Path record=data.resolve("migration.transaction");if(!Files.exists(record))return false;
  String state=new String(Files.readAllBytes(record),StandardCharsets.UTF_8);
  if(state.equals("committed"))return true;
  if(!state.equals("prepared"))throw new IOException("恢复事务记录损坏，请保留数据并重试");
  Path live=data.resolve("files"),old=data.resolve("migration.previous");
  if(Files.exists(old,LinkOption.NOFOLLOW_LINKS)){
   if(Files.exists(live,LinkOption.NOFOLLOW_LINKS)){
    Path failed=data.resolve("migration.failed");if(Files.exists(failed,LinkOption.NOFOLLOW_LINKS))failed=data.resolve("migration.failed-"+System.currentTimeMillis());
    Files.move(live,failed,StandardCopyOption.ATOMIC_MOVE);
   }
   Files.move(old,live,StandardCopyOption.ATOMIC_MOVE);
  }
  Files.delete(record);return false;
 }
 public static void complete(Path data)throws IOException{Files.deleteIfExists(data.resolve("migration.transaction"));}
}
