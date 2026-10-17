package com.droiddeck.launcher.migration;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
/** SAF directory access plus split-volume manifest. No broad storage permission is required. */
public final class BackupFiles {
 private final ContentResolver resolver;private final Uri tree,root;private final Context context;private final Path local;
 public BackupFiles(Context c,Uri tree)throws IOException{context=c;resolver=c.getContentResolver();this.tree=tree;
  if("file".equals(tree.getScheme())){if(tree.getPath()==null)throw new IOException("本地备份目录无效");local=BackupStorage.requirePublicFolder(c,Paths.get(tree.getPath()));root=tree;}
  else{local=null;root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
 }
 public Uri createFolder(String name)throws IOException{try{Uri uri=DocumentsContract.createDocument(resolver,root,DocumentsContract.Document.MIME_TYPE_DIR,name);if(uri==null)throw new IOException("无法创建备份目录");return uri;}catch(Exception e){throw new IOException("无法创建备份目录",e);}}
 public OutputStream create(Uri folder,String name,String mime)throws IOException{
  try{Uri uri=DocumentsContract.createDocument(resolver,folder,mime,name);if(uri==null)throw new IOException("无法创建文件："+name);OutputStream out=resolver.openOutputStream(uri,"w");if(out==null)throw new IOException("无法写入文件："+name);return new BufferedOutputStream(out,65536);}catch(Exception e){throw new IOException("写入备份失败："+name,e);}
 }
 public Map<String,Uri> children()throws IOException{return children(root);}
 public Map<String,Uri> children(Uri folder)throws IOException{
  if(local!=null){BackupStorage.requirePublicFolder(context,local);Map<String,Uri> result=new HashMap<>();int entries=0;
   try(DirectoryStream<Path> files=Files.newDirectoryStream(local)){for(Path file:files){if(++entries>200000)throw new IOException("备份目录条目过多，请仅保留备份文件");String name=file.getFileName().toString();if((name.equals("backup.ddbackup.json")||name.matches("part-[0-9]{4}\\.bin"))&&Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))result.put(name,Uri.fromFile(file.toFile()));}}
   return result;
  }
  Map<String,Uri> result=new HashMap<>();Uri listing=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(folder));
  try(Cursor cursor=resolver.query(listing,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
   if(cursor==null)throw new IOException("无法读取备份文件夹");while(cursor.moveToNext()){String name=cursor.getString(1);if(result.put(name,DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)))!=null)throw new IOException("备份文件夹存在重名文件");}
  }return result;
 }
 public InputStream open(Uri uri)throws IOException{if(local!=null){BackupStorage.requirePublicFolder(context,local);Path file=Paths.get(uri.getPath()).toAbsolutePath().normalize();if(!local.equals(file.getParent()))throw new IOException("备份文件不在所选目录中");return new BufferedInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS),65536);}InputStream in=resolver.openInputStream(uri);if(in==null)throw new IOException("无法读取备份文件");return new BufferedInputStream(in,65536);}
 public void describe(Uri document,MigrationDiagnostics log){
  if(local!=null){try{Path file=Paths.get(document.getPath());log.event("documentMetadata","name="+file.getFileName()+"; actualSize="+Files.size(file));}catch(Exception e){log.event("documentMetadata","unavailable: "+e.getClass().getSimpleName());}return;}
  try(Cursor c=resolver.query(document,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
   if(c!=null&&c.moveToFirst())log.event("documentMetadata","name="+c.getString(0)+"; actualSize="+(c.isNull(1)?"unknown":Long.toString(c.getLong(1))));
  }catch(Exception e){log.event("documentMetadata","unavailable: "+e.getClass().getSimpleName());}
 }
 public static JSONObject readJson(InputStream input)throws IOException,JSONException{
  try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>1024*1024)throw new IOException("清单/设置文件超过 1 MiB");out.write(buf,0,n);}return new JSONObject(out.toString("UTF-8"));}
 }
 public InputStream openVolumes(JSONObject manifest,Map<String,Uri> children)throws IOException,JSONException{
  if(!"droiddeck-migration".equals(manifest.getString("format"))||manifest.getInt("version")!=1)throw new IOException("不支持这个备份格式");
  JSONArray array=manifest.getJSONArray("parts");if(array.length()==0||array.length()>128)throw new IOException("分卷数量无效");List<VolumeStreams.Part> parts=new ArrayList<>();long total=0;
  try{for(int i=0;i<array.length();i++){JSONObject item=array.getJSONObject(i);String expected=String.format(Locale.ROOT,"part-%04d.bin",i+1);if(!expected.equals(item.getString("name")))throw new IOException("分卷顺序无效");VolumeStreams.Part p=new VolumeStreams.Part(expected,item.getLong("bytes"),item.getString("sha256"));if(!children.containsKey(p.name))throw new IOException("缺少分卷："+p.name);parts.add(p);total+=p.bytes;}
   if(total!=manifest.getLong("bytes"))throw new IOException("清单总大小不一致");return new VolumeStreams.Reader(parts,name->open(children.get(name)),manifest.getString("sha256"));
  }catch(IllegalArgumentException e){throw new IOException("分卷清单格式无效",e);}
 }
 public static JSONObject manifest(VolumeStreams.Writer writer,JSONObject settings,long unpacked)throws JSONException{
  JSONArray parts=new JSONArray();long total=0;for(VolumeStreams.Part p:writer.parts){parts.put(new JSONObject().put("name",p.name).put("bytes",p.bytes).put("sha256",p.sha256));total+=p.bytes;}
  return new JSONObject().put("format","droiddeck-migration").put("version",1).put("bytes",total).put("unpackedBytes",unpacked).put("sha256",writer.sha256()).put("parts",parts).put("settings",settings).put("sourcePackage","com.droiddeck.migration");
 }
}
