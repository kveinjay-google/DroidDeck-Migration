package com.droiddeck.launcher.migration;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Resolve Linux-absolute links as guest paths, never as Android host paths. */
public final class RuntimePaths {
 public static Path guestTarget(Path runtime,String relative)throws IOException{
  Path base=runtime.toAbsolutePath().normalize(),target=base.resolve(relative).normalize();
  for(int hops=0;hops<40;hops++){
   if(!target.startsWith(base))throw new IOException("运行时链接越界");Path cursor=base;boolean changed=false;
   Path rel=base.relativize(target);for(int i=0;i<rel.getNameCount();i++){
    cursor=cursor.resolve(rel.getName(i));if(Files.isSymbolicLink(cursor)){
     Path link=Files.readSymbolicLink(cursor);Path replacement=link.isAbsolute()?base.resolve(link.toString().substring(1)):cursor.getParent().resolve(link);
     for(int j=i+1;j<rel.getNameCount();j++)replacement=replacement.resolve(rel.getName(j));target=replacement.normalize();changed=true;break;
    }
   }if(!changed)return target;
  }throw new IOException("运行时链接循环");
 }
 public static Path directory(Path base,String name)throws IOException{
  Path current=base;for(Path part:Paths.get(name)){
   if(part.toString().equals(".."))throw new IOException("目录路径越界");current=current.resolve(part);
   if(Files.isSymbolicLink(current))throw new IOException("运行目录不能是链接："+name);
   Files.createDirectories(current);
  }return current;
 }
 public static String version(Path runtime)throws IOException{
  Path file=guestTarget(runtime,".version");if(Files.size(file)>64)throw new IOException("运行时版本标记过大");
  try(InputStream in=Files.newInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[65];int n;while((n=in.read(b))!=-1){if(out.size()+n>64)throw new IOException("运行时版本标记过大");out.write(b,0,n);}String v=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();if(!v.matches("r[0-9]{1,4}"))throw new IOException("运行时版本标记无效");return v;}
 }
 private RuntimePaths(){}
}
