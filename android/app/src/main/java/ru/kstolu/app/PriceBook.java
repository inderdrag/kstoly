package ru.kstolu.app;
import android.content.*;
import org.json.*;
final class PriceBook {
    private static String key(JSONObject i,String currency){return Library.norm(i.optString("name"))+":"+i.optString("unit")+":"+currency;}
    static JSONObject price(Context c,JSONObject ingredient,String currency){try{String value=c.getSharedPreferences("prices",0).getString(key(ingredient,currency),null);if(value!=null)return new JSONObject(value);}catch(Exception ignored){}return ingredient.optJSONObject("price");}
    static double decimal(String s){try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return Double.NaN;}}
    static void save(Context c,JSONObject i,String currency,double quantity,double value)throws Exception{if(!Double.isFinite(quantity)||!Double.isFinite(value)||quantity<=0||quantity>100000||value<0||value>1000000)throw new IllegalArgumentException("Проверьте цену и количество.");JSONObject p=new JSONObject().put("quantity",quantity).put("low",value).put("high",value).put("currency",currency).put("source","manual");c.getSharedPreferences("prices",0).edit().putString(key(i,currency),p.toString()).commit();}
    static void clear(Context c,JSONObject i,String currency){c.getSharedPreferences("prices",0).edit().remove(key(i,currency)).apply();}
}
