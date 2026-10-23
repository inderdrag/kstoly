package ru.kstolu.app;
import org.json.JSONObject;
final class Calories {
 static int value(JSONObject recipe){JSONObject n=recipe.optJSONObject("nutrition");if(n==null||!n.optString("source").equals("estimate"))return -1;Object raw=n.opt("kcal_per_100g");if(!(raw instanceof Number))return -1;double v=((Number)raw).doubleValue();return Double.isFinite(v)&&v>0&&v<=2000?Math.max(1,(int)Math.round(v)):-1;}
 static String shortValue(JSONObject r){int v=value(r);return v<0?"Нет оценки":"≈ "+v;}
 static String label(JSONObject r){int v=value(r);return v<0?"Нет оценки на 100 г":"≈ "+v+" ккал / 100 г";}
 static String note(JSONObject r){return value(r)<0?"У старого рецепта нет оценки на 100 г. Новые подборы учитывают примерную массу готового блюда.":"Ккал указаны на 100 г готового блюда. Масса после приготовления и калорийность — приблизительная оценка, а не точное измерение.";}
}
