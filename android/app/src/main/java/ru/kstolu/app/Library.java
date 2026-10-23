package ru.kstolu.app;

import android.content.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class Library {
    static String id(JSONObject recipe) throws Exception {
        String value=recipe.optString("title")+"\n"+recipe.getJSONArray("ingredients")+"\n"+recipe.getJSONArray("steps");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static JSONArray read(Context c){try{File file=new File(c.getFilesDir(),"history.json");if(file.length()>16*1024*1024)throw new IOException("Слишком большой файл истории");return new JSONArray(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONArray();}}
    static synchronized void save(Context c,JSONObject item)throws Exception{JSONArray old=read(c),next=new JSONArray();next.put(new JSONObject(item.toString()));String id=id(item.getJSONObject("recipe"));for(int i=0;i<old.length()&&next.length()<50;i++){JSONObject row=old.getJSONObject(i);if(!id(row.getJSONObject("recipe")).equals(id))next.put(row);}write(c,next);}
    static void write(Context c,JSONArray rows)throws Exception{AtomicFile f=new AtomicFile(new File(c.getFilesDir(),"history.json"));FileOutputStream out=null;try{out=f.startWrite();out.write(rows.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
    static String norm(String s){return s.toLowerCase(Locale.forLanguageTag("ru")).replace('ё','е').trim();}
    static boolean matches(JSONObject recipe,String query){StringBuilder s=new StringBuilder(recipe.optString("title"));JSONArray a=recipe.optJSONArray("ingredients");if(a!=null)for(int i=0;i<a.length();i++)s.append(' ').append(a.optJSONObject(i).optString("name"));String hay=norm(s.toString());for(String term:norm(query).split("\\s+"))if(!hay.contains(term))return false;return true;}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("library",Context.MODE_PRIVATE);}
    static List<String> folders(Context c){List<String> a=new ArrayList<>(Arrays.asList("Завтраки","На ужин","Любимое"));try{JSONArray extra=new JSONArray(prefs(c).getString("folders","[]"));for(int i=0;i<extra.length();i++)if(!a.contains(extra.getString(i)))a.add(extra.getString(i));}catch(Exception ignored){}return a;}
    static boolean addFolder(Context c,String name){name=name.trim().replaceAll("\\s+"," ");List<String>a=folders(c);if(name.isEmpty()||name.length()>40||a.size()>=30)return false;for(String f:a)if(norm(f).equals(norm(name)))return false;a.add(name);prefs(c).edit().putString("folders",new JSONArray(a.subList(3,a.size())).toString()).apply();return true;}
    static Set<String> assigned(Context c,JSONObject recipe){try{return new HashSet<>(prefs(c).getStringSet("folders:"+id(recipe),Collections.emptySet()));}catch(Exception e){return new HashSet<>();}}
    static void assign(Context c,JSONObject recipe,Set<String> folders)throws Exception{prefs(c).edit().putStringSet("folders:"+id(recipe),new HashSet<>(folders)).apply();}
    static int rating(Context c,JSONObject recipe){try{return prefs(c).getInt("rating:"+id(recipe),0);}catch(Exception e){return 0;}}
    static void rate(Context c,JSONObject recipe,int stars)throws Exception{if(stars<1||stars>5)throw new IllegalArgumentException();prefs(c).edit().putInt("rating:"+id(recipe),stars).putLong("cooked:"+id(recipe),System.currentTimeMillis()).commit();}
}
