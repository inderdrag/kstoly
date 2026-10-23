package ru.kstolu.app;
import org.json.JSONObject;
/** A recipe-level approximate model estimate. Never replace missing data with zero. */
final class Calories {
    static int value(JSONObject recipe){JSONObject n=recipe.optJSONObject("nutrition");if(n==null||!n.optString("source").equals("estimate"))return -1;Object raw=n.opt("kcal_per_serving");if(!(raw instanceof Number))return -1;double value=((Number)raw).doubleValue();if(!Double.isFinite(value)||value<1||value>5000)return -1;return Math.max(5,(int)(Math.round(value/5.0)*5));}
    static String shortValue(JSONObject recipe){int value=value(recipe);return value<0?"Нет оценки":"≈ "+value;}
    static String label(JSONObject recipe){int value=value(recipe);return value<0?"Ккал пока неизвестны":"≈ "+value+" ккал / порция";}
    static String note(JSONObject recipe){return value(recipe)<0?"В этом рецепте нет оценки ккал. Новые подборы после обновления сервера содержат приблизительную калорийность.":"Ккал — приблизительная оценка ИИ по ингредиентам на одну порцию, не точный расчёт. При изменении числа порций размер одной порции не меняется.";}
}
