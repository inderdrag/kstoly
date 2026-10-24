package ru.kstolu.app;
import org.json.*;import java.util.*;
/** Merge new names only; keep reviewed spelling, removals, receipt and region on the device. */
final class PantryMerge {
 static JSONObject add(JSONObject reviewed,JSONObject recognized)throws Exception{
  JSONObject merged=new JSONObject(reviewed.toString());JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();
  for(JSONArray source:new JSONArray[]{reviewed.getJSONArray("products"),recognized.getJSONArray("products")})for(int i=0;i<source.length();i++){
   String name=source.getString(i).trim();if(name.isEmpty()||name.length()>80||name.matches(".*[<>\\p{Cntrl}].*"))throw new IllegalArgumentException("Некорректное название продукта.");
   if(seen.add(name.toLowerCase(Locale.ROOT).replace('ё','е'))){if(out.length()>=80)throw new IllegalArgumentException("В списке максимум 80 продуктов. Уберите лишнее перед объединением.");out.put(name);}
  }
  merged.put("products",out);return merged;
 }
}
