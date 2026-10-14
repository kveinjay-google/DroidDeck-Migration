package com.droiddeck.launcher.migration;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class VolumeStreamsTest {
 @Test public void splitRoundtripAndExactBoundary() throws Exception {
  Map<String,byte[]> data=new HashMap<>();
  VolumeStreams.Writer w=new VolumeStreams.Writer(4,(name)->new ByteArrayOutputStream(){ public void close(){data.put(name,toByteArray());}});
  w.write(new byte[]{1,2,3,4,5,6,7,8});w.close();assertEquals(2,w.parts.size());
  try(InputStream r=new VolumeStreams.Reader(w.parts,n->new ByteArrayInputStream(data.get(n)),w.sha256())){assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8},r.readAllBytes());}
 }
 @Test public void rejectsCorruptAndMissingPart() throws Exception {
  Map<String,byte[]> data=new HashMap<>();
  VolumeStreams.Writer w=new VolumeStreams.Writer(4,(name)->new ByteArrayOutputStream(){public void close(){data.put(name,toByteArray());}});
  w.write(new byte[]{1,2,3,4,5});w.close();data.put(w.parts.get(0).name,new byte[]{9,2,3,4});
  assertThrows(IOException.class,()->new VolumeStreams.Reader(w.parts,n->new ByteArrayInputStream(data.get(n)),w.sha256()).readAllBytes());
  assertThrows(IOException.class,()->new VolumeStreams.Reader(w.parts,n->{throw new FileNotFoundException(n);},w.sha256()).readAllBytes());
 }
 @Test public void invalidPartNameCannotEscapeFolder() {
  assertThrows(IllegalArgumentException.class,()->new VolumeStreams.Part("../secret",2,"0".repeat(64)));
 }
 @Test public void verifiesWholeArchiveHash() throws Exception {
  VolumeStreams.Writer w=new VolumeStreams.Writer(4,n->new ByteArrayOutputStream());w.write(new byte[]{1});w.close();
  assertThrows(IOException.class,()->new VolumeStreams.Reader(w.parts,n->new ByteArrayInputStream(new byte[]{1}),"0".repeat(64)).readAllBytes());
 }
}
