package ru.kstolu.app;
import android.content.Context;import android.content.SharedPreferences;import org.json.JSONObject;import java.io.IOException;import java.util.UUID;
/** Persist an opaque recovery ID before upload. Polling is GET-only and never restarts Gemini. */
final class SelectionClient {
 static final String PENDING="pendingSelection",READY="readySelection";
 static JSONObject run(Context context,JSONObject request,ApiClient.Progress progress)throws Exception{
  SharedPreferences prefs=context.getSharedPreferences("settings",0);String ready=prefs.getString(READY,null);
  // A consumed recognition result must not swallow an explicit recipe-plan click.
  // The reviewed list has already been committed by the screen; no photos replay.
  if(ready!=null&&request!=null&&request.optString("mode").equals("plan")&&new JSONObject(ready).optString("kind").equals("inventory")){
   if(!prefs.contains("reviewedInventory"))throw new IOException("Сначала сохраните подтверждённый список продуктов.");
   if(!prefs.edit().remove(READY).commit())throw new IOException("Не удалось обновить состояние подбора. Запрос не отправлен.");ready=null;
  }
  if(ready!=null)return new JSONObject(ready);
  String id=prefs.getString(PENDING,null);JSONObject job=null;
  if(id==null){if(request==null)throw new IOException("Нет незавершённого подбора.");id=UUID.randomUUID().toString();request.put("requestId",id);
   if(!prefs.edit().putString(PENDING,id).commit())throw new IOException("Не удалось сохранить код подбора. Фотографии не отправлены.");
   try{job=ApiClient.call("/api/selection-jobs",request,progress);}catch(ApiClient.ApiError e){if(e.status>=400&&e.status<500)prefs.edit().remove(PENDING).commit();throw e;}catch(IOException e){if(progress!=null)progress.stage("recovering");}
  }
  long deadline=android.os.SystemClock.elapsedRealtime()+12*60*1000;int networkFailures=0;
  while(!Thread.currentThread().isInterrupted()&&android.os.SystemClock.elapsedRealtime()<deadline){
   if(job!=null){String status=job.optString("status");if(status.equals("done")){JSONObject result=job.getJSONObject("result");if(!prefs.edit().putString(READY,result.toString()).remove(PENDING).commit())throw new IOException("Результат получен, но не сохранён. Проверьте прежний подбор ещё раз.");return result;}
    if(status.equals("failed")){prefs.edit().remove(PENDING).commit();throw new IOException(job.optString("error","Подбор не завершён.")+" Новая отправка — только по вашему нажатию.");}
    if(status.equals("not_found"))throw new IOException("Задание пока не найдено или срок хранения истёк. Проверьте прежний подбор позже; фотографии автоматически не отправлены повторно.");
    if(progress!=null)progress.stage(job.optString("stage","waiting"));Thread.sleep(2000);
   }
   try{job=ApiClient.call("/api/selection-jobs/"+id,null);networkFailures=0;}catch(IOException e){job=null;networkFailures++;if(progress!=null)progress.stage("recovering");Thread.sleep(Math.min(6000,1000L+networkFailures*1000L));}
  }
  throw new IOException("Ожидание приостановлено. На главной нажмите «Проверить прежний подбор» — без повторной отправки фото.");
 }
}
