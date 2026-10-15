package com.droiddeck.launcher.migration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Local, bounded reports. Never serialize account preferences or archive file contents. */
public final class MigrationDiagnostics {
 private static final long LIMIT=900_000;
 private final Path file;
 private final long started=System.currentTimeMillis();
 private String stage="preflight";
 private boolean truncated;
 private volatile boolean available=true;
 public MigrationDiagnostics(Path directory,String operation)throws IOException {
  Files.createDirectories(directory);
  file=directory.resolve("migration-"+started+"-"+UUID.randomUUID()+".log");
  Files.createFile(file);
  List<Path> files=reports(directory);files.remove(file);
  for(int i=4;i<files.size();i++)Files.deleteIfExists(files.get(i));
  event("report", "DroidDeck migration diagnostic v1");event("operation",operation);
  event("privacy", "Local report; paths may contain filenames. No account settings/file contents are collected. Review before sharing.");
 }
 public Path path(){return file;}
 public boolean available(){return available;}
 public synchronized void stage(String value){stage=value;event("stage",value);}
 public synchronized void event(String key,String value){
  try{
   if(Files.size(file)>LIMIT){if(!truncated){truncated=true;append("DETAIL LIMIT: further routine events omitted\n");}return;}
   String detail=clean(value);if(detail.length()>4096)detail=detail.substring(0,4096)+"[truncated]";
   append(Instant.now()+" "+clean(key)+"="+detail+"\n");
  }catch(IOException ignored){available=false;/* Diagnostics must not break or weaken restore. */}
 }
 public synchronized void failure(Throwable error){
  StringBuilder trace=new StringBuilder();render(error,trace,"",Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>()));
  String stack=trace.toString();if(stack.length()>48000)stack=stack.substring(0,48000)+"\n[stack truncated]";
  try{append("\nFAILED stage="+clean(stage)+" elapsedMs="+(System.currentTimeMillis()-started)+"\n"+clean(stack)+"\n");sync();}catch(IOException ignored){available=false;}
 }
 private static void render(Throwable error,StringBuilder out,String prefix,Set<Throwable> seen){
  if(error==null||out.length()>48000)return;
  if(!seen.add(error)||seen.size()>32){out.append("[exception chain bounded]\n");return;}
  // Android JSON exceptions may embed the whole input document, including settings.
  String detail=error.getClass().getName().equals("org.json.JSONException")?"[JSON value/content hidden; inspect JSON format]":clean(error.getMessage());
  out.append(prefix).append(error.getClass().getName()).append(": ").append(detail).append('\n');
  for(StackTraceElement frame:error.getStackTrace()){if(out.length()>48000)break;out.append("\tat ").append(frame).append('\n');}
  for(Throwable suppressed:error.getSuppressed())render(suppressed,out,"Suppressed: ",seen);
  render(error.getCause(),out,"Caused by: ",seen);
 }
 public synchronized void success(){event("result","SUCCESS elapsedMs="+(System.currentTimeMillis()-started));try{sync();}catch(IOException ignored){}}
 private void sync()throws IOException{try(FileOutputStream out=new FileOutputStream(file.toFile(),true)){out.getFD().sync();}}
 private void append(String text)throws IOException{Files.write(file,text.getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);}
 private static String clean(String value){
  if(value==null)return "<null>";
  String text=value.length()>60000?value.substring(0,60000)+"[truncated]":value;
  text=text.replaceAll("(?i)((?:access_token|refresh_token|token|password|authorization|cookie|api_key)\\s*[=:]\\s*)[^\\s&]+","$1[REDACTED]");
  return text.replace("\r","\\r").replace("\u0000","\\0");
 }
 private static List<Path> reports(Path directory)throws IOException{
  if(!Files.isDirectory(directory))return new ArrayList<>();
  try(java.util.stream.Stream<Path> stream=Files.list(directory)){
   return stream.filter(p->p.getFileName().toString().startsWith("migration-")&&p.toString().endsWith(".log"))
    .sorted(Comparator.<Path>comparingLong(p->p.toFile().lastModified()).reversed().thenComparing(Path::toString))
    .collect(Collectors.toList());
  }
 }
 public static String latest(Path directory)throws IOException{
  List<Path> files=reports(directory);if(files.isEmpty())return "尚无迁移日志。";
  Path file=files.get(0);if(Files.size(file)>1100000)throw new IOException("诊断日志超过读取上限");
  return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
 }
}
