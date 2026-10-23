package ru.kstolu.app;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
/** One fixed local alarm, no network, ringtone selection or stored sound settings.
 * Only used for a consumed expiry; never for pause, manual navigation or rendering.
 * The alarm stream respects system volume and DND. Background delivery is handled by TimerAlarmReceiver when Android permits it. */
final class TimerSound {
 private TimerSound(){}
 private static ToneGenerator tone;
 private static final Handler handler=new Handler(Looper.getMainLooper());
 private static final Runnable release=()->{if(tone!=null){tone.release();tone=null;}};
 static void play(){
  handler.removeCallbacks(release);release.run();
  try{tone=new ToneGenerator(AudioManager.STREAM_ALARM,80);
   tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,2500);
   handler.postDelayed(release,2700);
  }catch(RuntimeException ignored){release.run();} // Sound failure must not block cooking.
 }
}
