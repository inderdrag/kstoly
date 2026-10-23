package ru.kstolu.app;
import android.app.*;import android.content.*;import android.os.*;
public final class TimerAlarmReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){
  if(!"ru.kstolu.TIMER".equals(intent.getAction()))return;
  if(!TimerAlarm.consume(c,intent.getStringExtra("token")))return;
  TimerAlarm.channel(c);String title=c.getSharedPreferences(TimerAlarm.PREF,0).getString("title","Таймер завершён");
  PendingIntent open=PendingIntent.getActivity(c,TimerAlarm.ID,new Intent(c,MainActivity.class).putExtra("openTimer",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification notification=new Notification.Builder(c,TimerAlarm.CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("К столу · Таймер завершён").setContentText(title+". Проверьте готовность еды.").setStyle(new Notification.BigTextStyle().bigText(title+". Проверьте готовность еды. Откройте рецепт для следующего шага.")).setCategory(Notification.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
  if(TimerAlarm.notifications(c))c.getSystemService(NotificationManager.class).notify(TimerAlarm.ID,notification);
  PendingResult result=goAsync();PowerManager.WakeLock wake=c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"kstolu:timer-signal");wake.acquire(7000);
  TimerSound.play();new Handler(Looper.getMainLooper()).postDelayed(()->{if(wake.isHeld())wake.release();result.finish();},3000);
 }
}
