import com.droiddeck.launcher.migration.VolumeStreams;
import com.droiddeck.launcher.migration.archive.MigrationArchive;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class InspectBackup {
 public static void main(String[] args)throws Exception{
  Path folder=Paths.get(args[0]);List<VolumeStreams.Part> parts=new ArrayList<>();for(int i=2;i<args.length;i++){String[] f=args[i].split(":");parts.add(new VolumeStreams.Part(f[0],Long.parseLong(f[1]),f[2]));}
  try(InputStream in=new VolumeStreams.Reader(parts,n->new BufferedInputStream(Files.newInputStream(folder.resolve(n)),65536),args[1])){
   MigrationArchive.Inspection result=MigrationArchive.inspect(in,MigrationArchive.MAX_BYTES,null);byte[] b=new byte[65536];while(in.read(b)!=-1){}
   System.out.println("Verified parts+TAR+runtime: "+result.entries+" entries, "+result.unpackedBytes+" payload bytes");
  }
 }
}
