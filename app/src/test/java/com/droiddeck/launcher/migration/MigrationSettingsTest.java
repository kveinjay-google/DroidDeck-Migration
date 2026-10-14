package com.droiddeck.launcher.migration;
import android.content.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class)
public class MigrationSettingsTest {
 @Test public void importsPortableTypedValuesAndRetainsDeviceSettings()throws Exception{
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("session",0).edit().putString("gpuDriverMode","auto").commit();
  JSONObject values=new JSONObject().put("fullscreen",true).put("resolutionWidth",1280).put("gpuDriverMode","custom").put("gameStorage","/old/phone");
  MigrationSettings.apply(c,new JSONObject().put("session",values).put("agent",new JSONObject().put("commands",true)));
  SharedPreferences p=c.getSharedPreferences("session",0);assertTrue(p.getBoolean("fullscreen",false));assertEquals(1280,p.getInt("resolutionWidth",0));assertEquals("auto",p.getString("gpuDriverMode",""));assertFalse(p.contains("gameStorage"));assertFalse(c.getSharedPreferences("agent",0).contains("commands"));
 }
 @Test public void rejectsOversizedMetadataAndMixedSets()throws Exception{
  assertThrows(JSONException.class,()->MigrationSettings.validate(new JSONObject().put("session",new JSONObject().put("name","x".repeat(65537)))));
  assertThrows(JSONException.class,()->MigrationSettings.validate(new JSONObject().put("controller",new JSONObject().put("mapping",new JSONArray().put(42)))));
 }
 @Test public void capturedSettingsExcludeAccountAndDeviceFields()throws Exception{
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("session",0).edit().putString("apiKey","private").putString("gameFolder","/secret").putBoolean("fullscreen",true).commit();
  JSONObject vals=MigrationSettings.capture(c).getJSONObject("session");assertTrue(vals.getBoolean("fullscreen"));assertFalse(vals.has("apiKey"));assertFalse(vals.has("gameFolder"));
 }
}
