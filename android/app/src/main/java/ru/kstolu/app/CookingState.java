package ru.kstolu.app;
import org.json.*;
/** One manual-start timer. An expiry is consumed before navigation, exactly once. */
final class CookingState {
    int index=0;long remaining=0,deadline=0;int boot=0;boolean running=false;
    void start(long seconds,long now,int boot){if(seconds<1||seconds>21600)throw new IllegalArgumentException();remaining=seconds*1000;deadline=now+remaining;this.boot=boot;running=true;}
    long left(long now){return running?Math.max(0,deadline-now):remaining;}
    void pause(long now){remaining=left(now);running=false;deadline=0;}
    boolean expire(long now){if(!running||now<deadline)return false;running=false;remaining=0;deadline=0;index++;return true;}
    JSONObject json()throws Exception{return new JSONObject().put("index",index).put("remaining",remaining).put("deadline",deadline).put("boot",boot).put("running",running);}
    static CookingState restore(JSONObject j,int boot){CookingState s=new CookingState();s.index=Math.max(0,j.optInt("index"));s.remaining=Math.max(0,j.optLong("remaining"));s.deadline=j.optLong("deadline");s.boot=j.optInt("boot");s.running=j.optBoolean("running");if(s.boot!=boot){s.running=false;s.deadline=0;}return s;}
}
