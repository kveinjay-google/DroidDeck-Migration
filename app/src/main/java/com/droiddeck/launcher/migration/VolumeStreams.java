package com.droiddeck.launcher.migration;
import java.io.*;
import java.security.*;
import java.util.*;

/** Stream a TAR across independently verifiable files without merging them in memory. */
public final class VolumeStreams {
 public static final long DEFAULT_PART_BYTES=2_000_000_000L;
 public interface Sink { OutputStream open(String name) throws IOException; }
 public interface Source { InputStream open(String name) throws IOException; }
 public static final class Part {
  public final String name,sha256; public final long bytes;
  public Part(String name,long bytes,String sha256){
   if(!name.matches("part-[0-9]{4}\\.bin")||bytes<=0||bytes>DEFAULT_PART_BYTES||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("分卷清单格式无效");
   this.name=name;this.bytes=bytes;this.sha256=sha256;
  }
 }
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 public static String hex(byte[] b){StringBuilder s=new StringBuilder(b.length*2);for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 public static final class Writer extends OutputStream {
  public final List<Part> parts=new ArrayList<>();
  private final long limit;private final Sink sink;private final MessageDigest whole=digest();
  private OutputStream current;private MessageDigest part;private long count;private String name,finalHash;private boolean closed;
  public Writer(long limit,Sink sink){if(limit<=0||limit>DEFAULT_PART_BYTES)throw new IllegalArgumentException("分卷上限无效");this.limit=limit;this.sink=sink;}
  public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
  public void write(byte[] b,int off,int len)throws IOException{
   if(closed)throw new IOException("备份流已关闭");if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
   while(len>0){if(current==null){if(parts.size()>=128)throw new IOException("备份超过 128 GiB 上限");name=String.format(Locale.ROOT,"part-%04d.bin",parts.size()+1);current=sink.open(name);part=digest();count=0;}
    int n=(int)Math.min(len,limit-count);current.write(b,off,n);part.update(b,off,n);whole.update(b,off,n);count+=n;off+=n;len-=n;if(count==limit)finishPart();}
  }
  private void finishPart()throws IOException{current.close();parts.add(new Part(name,count,hex(part.digest())));current=null;}
  public void flush()throws IOException{if(current!=null)current.flush();}
  public void close()throws IOException{if(closed)return;closed=true;if(current!=null)finishPart();finalHash=hex(whole.digest());}
  public String sha256(){if(!closed)throw new IllegalStateException("尚未完成备份");return finalHash;}
 }
 public static final class Reader extends InputStream {
  private final List<Part> parts;private final Source source;private final String expected;private final MessageDigest whole=digest();
  private MessageDigest part;private InputStream current;private int index;private long count;private boolean eof,closed;
  public Reader(List<Part> parts,Source source,String expected){
   if(parts.isEmpty()||parts.size()>128||!expected.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("备份清单格式无效");
   long total=0;Set<String> names=new HashSet<>();for(Part p:parts){if(!names.add(p.name))throw new IllegalArgumentException("重复分卷");total+=p.bytes;}
   if(total>128L*1024*1024*1024)throw new IllegalArgumentException("备份过大");
   this.parts=new ArrayList<>(parts);this.source=source;this.expected=expected;
  }
  public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}
  public int read(byte[] b,int off,int len)throws IOException{
   if(closed)throw new IOException("备份流已关闭");if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();if(len==0)return 0;if(eof)return -1;
   while(true){if(current==null){if(index==parts.size()){eof=true;String actual=hex(whole.digest());if(!actual.equals(expected))throw new IOException("整个备份的 SHA256 校验失败; expectedSha256="+expected+"; actualSha256="+actual);return -1;}try{current=source.open(parts.get(index).name);}catch(IOException e){throw new IOException("无法打开分卷："+parts.get(index).name,e);}part=digest();count=0;}
    Part p=parts.get(index);int n=current.read(b,off,(int)Math.min(len,Math.max(1,p.bytes-count)));
    if(n<0){current.close();current=null;String actual=hex(part.digest());if(count!=p.bytes||!actual.equals(p.sha256))throw new IOException("分卷损坏或不完整："+p.name+"; expectedBytes="+p.bytes+"; actualBytes="+count+"; expectedSha256="+p.sha256+"; actualSha256="+actual);index++;continue;}
    if(n==0)continue;count+=n;if(count>p.bytes)throw new IOException("分卷大小不符："+p.name+"; expectedBytes="+p.bytes+"; actualBytesAtLeast="+count);part.update(b,off,n);whole.update(b,off,n);return n;
   }
  }
  public void close()throws IOException{closed=true;if(current!=null)current.close();}
 }
 private VolumeStreams(){}
}
