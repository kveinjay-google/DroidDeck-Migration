package com.droiddeck.launcher.migration;
import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Public mounted roots only. No private app directories or all-files permission. */
public final class BackupStorage {
 public static List<Path> roots(Context context){
  LinkedHashSet<Path> roots=new LinkedHashSet<>();
  add(roots,Environment.getExternalStorageDirectory());
  File[] volumes=context.getExternalFilesDirs(null);
  if(volumes!=null)for(File volume:volumes)if(volume!=null){String path=volume.getAbsolutePath();int marker=path.indexOf("/Android/data/");if(marker>0)add(roots,new File(path.substring(0,marker)));}
  return new ArrayList<>(roots);
 }
 private static void add(Set<Path> roots,File file){if(roots.size()>=32)return;try{roots.add(file.getCanonicalFile().toPath());}catch(IOException ignored){}}
 public static Path requirePublicFolder(Context context,Path folder)throws IOException {
  Path absolute=folder.toAbsolutePath().normalize(),canonical=absolute.toFile().getCanonicalFile().toPath();
  if(!canonical.equals(absolute)||!Files.isDirectory(absolute,LinkOption.NOFOLLOW_LINKS))throw new IOException("备份目录已移动或是链接，请重新搜索");
  for(Path root:roots(context))if(canonical.startsWith(root)&&!canonical.startsWith(root.resolve("Android/data"))&&!canonical.startsWith(root.resolve("Android/obb")))return canonical;
  throw new IOException("仅可读取用户存储中的备份目录，请通过文件选择器授权其他位置");
 }
 private BackupStorage(){}
}
