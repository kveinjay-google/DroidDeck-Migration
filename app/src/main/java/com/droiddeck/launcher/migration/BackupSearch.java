package com.droiddeck.launcher.migration;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Read-only, bounded discovery. It never reads multi-gigabyte volume payloads. */
public final class BackupSearch {
 public static final class Limits {
  final int directories,entries,queue,depth,candidates;final long millis;
  public Limits(){this(20000,200000,4096,32,100,90000);}
  public Limits(int directories,int entries,int queue,int depth,int candidates,long millis){
   if(directories<1||directories>20000||entries<1||entries>200000||queue<1||queue>4096||depth<1||depth>32||candidates<1||candidates>100||millis<1||millis>90000)throw new IllegalArgumentException("Invalid search limits");
   this.directories=directories;this.entries=entries;this.queue=queue;this.depth=depth;this.candidates=candidates;this.millis=millis;
  }
 }
 public static final class Candidate {
  public final Path folder;public final boolean ready;public final String reason;public final int parts;public final long bytes;
  Candidate(Path folder,boolean ready,String reason,int parts,long bytes){this.folder=folder;this.ready=ready;this.reason=reason;this.parts=parts;this.bytes=bytes;}
 }
 public static final class Result {
  public final List<Candidate> candidates=new ArrayList<>();public int directories,entries,unreadable;public boolean cancelled,limited;
 }
 public interface Progress {void update(int directories,int entries);}
 private static final class Node {final Path path,boundary;final int depth;final boolean priority;Node(Path p,int d,boolean priority,Path boundary){path=p;depth=d;this.priority=priority;this.boundary=boundary;}}
 public static Result scan(List<Path> roots,Limits limits,BooleanSupplier cancelled,Progress progress){
  if(roots.size()>32)throw new IllegalArgumentException("Too many storage roots");
  Result result=new Result();Deque<Node> queue=new ArrayDeque<>(),priority=new ArrayDeque<>();Set<Path> visited=new HashSet<>(),privateDirs=new HashSet<>();
  for(Path raw:roots){Path root=raw.toAbsolutePath().normalize();privateDirs.add(root.resolve("Android/data"));privateDirs.add(root.resolve("Android/obb"));
   for(String preferred:new String[]{"Download/QuarkDownloads","Download/QuarkDownload","Download/Quark","Download/夸克网盘","QuarkDownloads","QuarkDownload","Quark/Download","夸克网盘","Download","Downloads","Documents","UCDownloads","BaiduNetdisk","QQfile_recv","tencent/QQfile_recv"}){
    if(queue.size()+priority.size()<limits.queue&&safeFolder(root.resolve(preferred),root,privateDirs)&&Files.isDirectory(root.resolve(preferred),LinkOption.NOFOLLOW_LINKS)){boolean first=quark(preferred);(first?priority:queue).add(new Node(root.resolve(preferred),0,first,root));}
   }
   if(queue.size()+priority.size()<limits.queue)queue.add(new Node(root,0,false,root));else result.limited=true;
  }
  long started=System.nanoTime();
  while(!queue.isEmpty()||!priority.isEmpty()){
   if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()){result.cancelled=true;break;}
   if(result.directories>=limits.directories||result.entries>=limits.entries||result.candidates.size()>=limits.candidates||(System.nanoTime()-started)/1000000>=limits.millis){result.limited=true;break;}
   Node node=priority.isEmpty()?queue.removeFirst():priority.removeFirst();if(!visited.add(node.path)||!safeFolder(node.path,node.boundary,privateDirs))continue;
   if(!Files.isDirectory(node.path,LinkOption.NOFOLLOW_LINKS)){if(!Files.notExists(node.path))result.unreadable++;continue;}
   result.directories++;
   try{
    Candidate candidate=inspect(node.path);if(candidate!=null)result.candidates.add(candidate);
    try(DirectoryStream<Path> children=Files.newDirectoryStream(node.path)){
     for(Path child:children){
      if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()){result.cancelled=true;break;}
      if(result.entries>=limits.entries||(System.nanoTime()-started)/1000000>=limits.millis){result.limited=true;break;}
      result.entries++;
      if(Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(child)&&!privateDirs.contains(child)&&!visited.contains(child)){
       if(node.depth>=limits.depth||queue.size()+priority.size()>=limits.queue)result.limited=true;else{boolean first=node.priority||quark(child.getFileName().toString());(first?priority:queue).addLast(new Node(child,node.depth+1,first,node.boundary));}
      }
     }
    }
   }catch(IOException|SecurityException|DirectoryIteratorException denied){result.unreadable++;}
   if(progress!=null)progress.update(result.directories,result.entries);
   if(result.cancelled)break;
  }
  return result;
 }
 private static boolean quark(String name){return name.toLowerCase(Locale.ROOT).contains("quark")||name.contains("夸克");}
 private static boolean safeFolder(Path folder,Path boundary,Set<Path> privateDirs){
  if(!folder.startsWith(boundary))return false;for(Path denied:privateDirs)if(folder.startsWith(denied))return false;
  for(Path at=folder;at!=null;at=at.getParent()){if(Files.isSymbolicLink(at))return false;if(at.equals(boundary))return true;}return false;
 }
 public static Candidate inspect(Path folder)throws IOException {
  Path manifest=folder.resolve("backup.ddbackup.json");if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS))return null;
  int count=0;long bytes=0;
  try{
   JSONObject data;
   try(InputStream in=Files.newInputStream(manifest,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException("清单超过 1 MiB");out.write(buffer,0,n);}data=new JSONObject(out.toString("UTF-8"));
   }
   if(!"droiddeck-migration".equals(data.getString("format"))||data.getInt("version")!=1)throw new IOException("备份格式不支持");
   if(!data.getString("sha256").matches("[a-f0-9]{64}"))throw new IOException("整体校验值格式无效");
   JSONArray parts=data.getJSONArray("parts");count=parts.length();if(count<1||count>128)throw new IOException("分卷数量无效");
   List<String> problems=new ArrayList<>();
   for(int i=0;i<count;i++){
    JSONObject part=parts.getJSONObject(i);String name=String.format(Locale.ROOT,"part-%04d.bin",i+1);if(!name.equals(part.getString("name")))throw new IOException("分卷顺序或清单名称无效");
    VolumeStreams.Part p=new VolumeStreams.Part(name,part.getLong("bytes"),part.getString("sha256"));bytes=Math.addExact(bytes,p.bytes);
    Path file=folder.resolve(name);
    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(file))problems.add("缺少可读取的 "+name);
    else{long actual=Files.size(file);if(actual!=p.bytes)problems.add(name+" 大小不符：应为 "+p.bytes+" 字节，实际 "+actual+" 字节，可能尚未下载完整");}
   }
   if(bytes!=data.getLong("bytes")||bytes>128L*1024*1024*1024)throw new IOException("清单总大小无效");
   if(!problems.isEmpty())return new Candidate(folder,false,String.join("；",problems.subList(0,Math.min(5,problems.size()))),count,bytes);
   return new Candidate(folder,true,"分卷齐全且大小匹配；导入时再做完整校验",count,bytes);
  }catch(JSONException|IllegalArgumentException|ArithmeticException invalid){return new Candidate(folder,false,"备份清单格式无效（未记录内容）",count,bytes);}
  catch(IOException invalid){return new Candidate(folder,false,invalid.getMessage(),count,bytes);}
 }
 private BackupSearch(){}
}
