import com.droiddeck.launcher.migration.VolumeStreams;
import com.droiddeck.launcher.migration.archive.MigrationArchive;
import org.apache.commons.compress.archivers.tar.*;
import java.nio.file.*;import java.io.*;import java.util.*;
/** Build share-safe volumes directly from the legacy archive, preserving executable payloads. */
public final class SanitizeBackup {
 public static void main(String[] args)throws Exception{
  Path original=Paths.get(args[0]),folder=Paths.get(args[1]);Files.createDirectory(folder);
  VolumeStreams.Writer volumes=new VolumeStreams.Writer(VolumeStreams.DEFAULT_PART_BYTES,n->new BufferedOutputStream(Files.newOutputStream(folder.resolve(n)),65536));
  List<String> names=new ArrayList<>();long unpacked=0;
  try(InputStream input=new BufferedInputStream(Files.newInputStream(original),65536);TarArchiveInputStream in=new TarArchiveInputStream(input)){
   TarArchiveOutputStream out=new TarArchiveOutputStream(volumes);out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);TarArchiveEntry e;byte[] b=new byte[65536];
   while((e=in.getNextEntry())!=null){String name=e.getName();while(name.startsWith("./"))name=name.substring(2);while(name.endsWith("/"))name=name.substring(0,name.length()-1);if(name.isEmpty()||name.equals("."))continue;
    if(!MigrationArchive.shouldExport(name,e.isDirectory(),e.isSymbolicLink(),true))continue;
    names.add(name);out.putArchiveEntry(e);if(e.isFile()){long left=e.getSize();unpacked+=left;while(left>0){int n=in.read(b,0,(int)Math.min(left,b.length));if(n<0)throw new EOFException();out.write(b,0,n);left-=n;}}out.closeArchiveEntry();
   }
   out.finish();out.flush();volumes.close();
  }
  StringBuilder parts=new StringBuilder("[");long size=0;for(VolumeStreams.Part p:volumes.parts){if(size>0)parts.append(',');parts.append("{\"name\":\"").append(p.name).append("\",\"bytes\":").append(p.bytes).append(",\"sha256\":\"").append(p.sha256).append("\"}");size+=p.bytes;}
  String manifest="{\"format\":\"droiddeck-migration\",\"version\":1,\"kind\":\"runtime-only\",\"bytes\":"+size+",\"unpackedBytes\":"+unpacked+",\"sha256\":\""+volumes.sha256()+"\",\"parts\":"+parts+"],\"settings\":{}}";
  Files.write(folder.resolve("backup.ddbackup.json"),manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(folder.resolve("README.txt"),"运行环境分享包：请把本文件夹内所有分卷和 backup.ddbackup.json 一起传到手机，使用 DroidDeck 迁移版选择本文件夹恢复。不含账号、浏览器历史、日志和存档。".getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(Paths.get(args[2]),names);
  System.out.println("Sanitized runtime archive: "+size+" bytes, "+volumes.parts.size()+" parts; SHA256 "+volumes.sha256());
 }
}
