package ru.kstolu.app;
import org.json.*;import java.util.*;
final class StepIngredients {
 static String label(JSONObject recipe,int index,int portions){
  JSONArray steps=recipe.optJSONArray("steps"),all=recipe.optJSONArray("ingredients");if(steps==null||all==null||index<0||index>=steps.length())return "";
  JSONArray ids=steps.optJSONObject(index).optJSONArray("ingredient_indexes");if(ids==null)return "";
  ArrayList<String> names=new ArrayList<>();HashSet<Integer> seen=new HashSet<>();double factor=(double)portions/Math.max(1,recipe.optInt("servings",2));
  for(int n=0;n<ids.length();n++){int id=ids.optInt(n,-1);if(id<0||id>=all.length()||!seen.add(id))continue;JSONObject i=all.optJSONObject(id);if(i==null)continue;double amount=i.optDouble("amount",0)*factor;String unit=i.optString("unit");unit=unit.equals("g")?"г":unit.equals("ml")?"мл":"шт.";names.add(i.optString("name")+" — "+String.format(Locale.ROOT,"%.2f",amount).replaceAll("0+$","").replaceAll("\\.$","")+" "+unit);}
  return String.join("\n",names);
 }
}
