package ru.kstolu.app;
import android.Manifest;import android.widget.Toast;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.*;import android.provider.Settings;import java.util.UUID;
/** One explicit cooking alarm. Receiver and foreground share an atomic consumption token. */
final class TimerAlarm {
 static final String PREF="cooking-alarm",CHANNEL="cooking-timers-v1";static final int ID=731;
 private static android.content.SharedPreferences store(Context c){return c.getSharedPreferences(PREF,0);}
 static void channel(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel ch=new NotificationChannel(CHANNEL,"Таймеры приготовления",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("Сигнал завершения таймера. Мелодия в приложении не настраивается.");ch.setSound(null,null);n.createNotificationChannel(ch);}
 static boolean notifications(Context c){channel(c);NotificationManager n=c.getSystemService(NotificationManager.class);return n.areNotificationsEnabled()&&n.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
 static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
 static void prepare(Activity a,String key,Runnable start){
  if(store(a).contains("token")&&!key.equals(store(a).getString("key",""))){new AlertDialog.Builder(a).setTitle("Заменить активный таймер?").setMessage("Поддерживается один фоновый будильник. Предыдущий таймер будет поставлен на паузу.").setNegativeButton("Отмена",null).setPositiveButton("Заменить",(d,w)->{pausePrevious(a);cancel(a);prepare(a,key,start);}).show();return;}

  if(notifications(a)&&exact(a)){start.run();return;}
  new AlertDialog.Builder(a).setTitle("Сигнал в фоне и при блокировке")
   .setMessage("Нужны уведомления и разрешение Android «Будильники и напоминания». После выдачи разрешений снова нажмите запуск таймера. Без них таймер работает только в открытом приложении. Системная громкость будильника и «Не беспокоить» могут заглушить звук.")
   .setPositiveButton("Разрешить",(d,w)->{try{if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},731);else if(!exact(a)&&Build.VERSION.SDK_INT>=31)a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+a.getPackageName())));else a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));}catch(Exception e){Toast.makeText(a,"Откройте разрешения приложения в настройках Android.",Toast.LENGTH_LONG).show();}})
   .setNeutralButton("Только в приложении",(d,w)->start.run()).setNegativeButton("Отмена",null).show();
 }
 private static void pausePrevious(Context c){String k=store(c).getString("key","");if(k.isEmpty())return;android.content.SharedPreferences p=c.getSharedPreferences("cooking",0);try{CookingState old=CookingState.restore(new org.json.JSONObject(p.getString(k,"{}")),android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,0));old.pause(SystemClock.elapsedRealtime());p.edit().putString(k,old.json().toString()).commit();}catch(Exception ignored){}}
 private static PendingIntent pending(Context c,String token){return PendingIntent.getBroadcast(c,ID,new Intent(c,TimerAlarmReceiver.class).setAction("ru.kstolu.TIMER").putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static synchronized boolean schedule(Context c,String key,long elapsedDeadline,String title,String snapshot){
  String oldKey=store(c).getString("key","");if(!oldKey.isEmpty()&&!oldKey.equals(key)){android.content.SharedPreferences states=c.getSharedPreferences("cooking",0);try{CookingState old=CookingState.restore(new org.json.JSONObject(states.getString(oldKey,"{}")),android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,0));old.pause(SystemClock.elapsedRealtime());states.edit().putString(oldKey,old.json().toString()).commit();}catch(Exception ignored){}}
  cancel(c);if(!notifications(c)||!exact(c))return false;
  String token=UUID.randomUUID().toString();store(c).edit().putString("token",token).putString("key",key).putString("title",title).putString("item",snapshot).commit();
  PendingIntent show=PendingIntent.getActivity(c,ID,new Intent(c,MainActivity.class).putExtra("openTimer",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  try{long when=System.currentTimeMillis()+Math.max(1,elapsedDeadline-SystemClock.elapsedRealtime());c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(when,show),pending(c,token));return true;}catch(SecurityException e){store(c).edit().clear().commit();return false;}
 }
 static synchronized void cancel(Context c){c.getSystemService(AlarmManager.class).cancel(pending(c,""));c.getSystemService(NotificationManager.class).cancel(ID);store(c).edit().clear().commit();}
 static synchronized void cancelFor(Context c,String key){if(key.equals(store(c).getString("key","")))cancel(c);}
 static synchronized boolean consume(Context c,String token){if(token==null||!token.equals(store(c).getString("token","")))return false;store(c).edit().remove("token").commit();return true;}
 static synchronized boolean foreground(Context c,String key){if(!key.equals(store(c).getString("key","")))return true;String token=store(c).getString("token",null);if(token==null)return false;return consume(c,token);}
}
