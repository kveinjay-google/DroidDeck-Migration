package com.droiddeck.launcher.migration;
import android.content.*;
import org.json.*;
import java.util.*;
import java.io.IOException;
/** Portable preferences only; credentials, grants and hardware paths remain device-local. */
public final class MigrationSettings {
 private static final Set<String> FILES=new HashSet<>(Arrays.asList("session","controller","steam_touch","app-ui","file_manager","file_manager_prefs","game_files","language","lossless","frame_gen"));
 static boolean portable(String file,String key){
  if(!FILES.contains(file)||key.length()>256)return false;
  String k=key.toLowerCase(Locale.ROOT);
  for(String blocked:new String[]{"storage","folder","driver","serial","address","paired","permission","processlimit","phantom","token","password","apikey","api_key","lastpath","favorite"})if(k.contains(blocked))return false;
  return true;
 }
 public static JSONObject capture(Context c)throws JSONException{
  JSONObject all=new JSONObject();
  for(String file:FILES){JSONObject values=new JSONObject();for(Map.Entry<String,?> e:c.getSharedPreferences(file,0).getAll().entrySet())if(portable(file,e.getKey())){
   Object v=e.getValue();values.put(e.getKey(),v instanceof Set?new JSONArray((Set<?>)v):v);
  }all.put(file,values);}return all;
 }
 public static JSONObject validate(JSONObject source)throws JSONException{
  JSONObject clean=new JSONObject();Iterator<String> files=source.keys();
  while(files.hasNext()){String f=files.next();if(!FILES.contains(f))continue;JSONObject values=source.getJSONObject(f),out=new JSONObject();Iterator<String> keys=values.keys();
   while(keys.hasNext()){String key=keys.next();if(!portable(f,key))continue;Object value=values.get(key);
    if(value instanceof String&&((String)value).length()>65536)throw new JSONException("设置文字过长");
    if(!(value instanceof String||value instanceof Boolean||value instanceof Number||value instanceof JSONArray))throw new JSONException("设置值类型无效");
    if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(a.length()>4096)throw new JSONException("设置列表过长");for(int i=0;i<a.length();i++)if(!(a.get(i) instanceof String))throw new JSONException("设置列表格式无效");}
    out.put(key,value);
   }clean.put(f,out);
  }return clean;
 }
 public static void apply(Context c,JSONObject source)throws JSONException,IOException{
  JSONObject clean=validate(source);Iterator<String> files=clean.keys();
  while(files.hasNext()){String file=files.next();SharedPreferences prefs=c.getSharedPreferences(file,0);SharedPreferences.Editor editor=prefs.edit();
   // Remove portable keys only, retaining local driver/path/permission settings.
   for(String key:prefs.getAll().keySet())if(portable(file,key))editor.remove(key);
   JSONObject vals=clean.getJSONObject(file);Iterator<String> keys=vals.keys();while(keys.hasNext()){
    String key=keys.next();Object v=vals.get(key);if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Number)editor.putFloat(key,((Number)v).floatValue());else if(v instanceof JSONArray){Set<String> set=new HashSet<>();JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)set.add(a.getString(i));editor.putStringSet(key,set);}else editor.putString(key,(String)v);
   }if(!editor.commit())throw new IOException("无法保存应用设置："+file);
  }
 }
 private MigrationSettings(){}
}
