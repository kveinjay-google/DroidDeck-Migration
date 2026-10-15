package com.droiddeck.launcher.migration;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.*;

public class MigrationDiagnosticsTest {
 @Test public void failureSurvivesReopenWithStageAndCause() throws Exception {
  Path dir=Files.createTempDirectory("diagnostics");
  MigrationDiagnostics log=new MigrationDiagnostics(dir,"restore-volumes");
  log.stage("archive-inspect");log.event("source", "content://provider/tree/Download?token=secret-token");
  log.failure(new IOException("Unsafe archive path: ../escape",new IllegalArgumentException("bad entry")));
  String report=MigrationDiagnostics.latest(dir);
  assertTrue(report.contains("archive-inspect"));assertTrue(report.contains("../escape"));
  assertTrue(report.contains("IllegalArgumentException"));assertTrue(report.contains("FAILED"));
  assertFalse(report.contains("secret-token"));
 }
 @Test public void boundedLogStillIncludesFinalFailure() throws Exception {
  Path dir=Files.createTempDirectory("diagnostics");MigrationDiagnostics log=new MigrationDiagnostics(dir,"test");
  for(int i=0;i<500;i++)log.event("event", "x".repeat(4096));
  log.failure(new IOException("terminal failure"));
  assertTrue(MigrationDiagnostics.latest(dir).contains("terminal failure"));
  assertTrue(Files.size(log.path())<1100000);
 }
 @Test public void retainsOnlyFiveReports() throws Exception {
  Path dir=Files.createTempDirectory("diagnostics");
  for(int i=0;i<8;i++){MigrationDiagnostics log=new MigrationDiagnostics(dir,"test"+i);log.success();}
  try(java.util.stream.Stream<Path> files=Files.list(dir)){assertEquals(5,files.count());}
 }
}
