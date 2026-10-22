package ru.kstolu.app;

import org.json.JSONObject;
import java.net.HttpCookie;
import java.util.*;

/** An explicit version-0 Cookie header, without $Version or quoted values. */
final class SessionCookies {
    private final SessionStore store;
    private String token="";private long expires=0;
    SessionCookies(SessionStore store){this.store=store;
        try{JSONObject saved=new JSONObject(store.load());String value=saved.optString("token");long until=saved.optLong("expires");
            if(valid(value)&&until>System.currentTimeMillis()){token=value;expires=until;}else store.clear();
        }catch(Exception e){store.clear();}
    }
    private static boolean valid(String value){return value!=null&&value.length()<1800&&value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}");}
    synchronized String header(){if(expires<=System.currentTimeMillis()){clear();return "";}return token.isEmpty()?"":"oliva_session="+token;}
    synchronized boolean hasSession(){return !header().isEmpty();}
    synchronized void clear(){token="";expires=0;store.clear();}
    synchronized void accept(Map<String,List<String>> headers)throws Exception{
        for(Map.Entry<String,List<String>> entry:headers.entrySet()){
            if(entry.getKey()==null||!entry.getKey().equalsIgnoreCase("Set-Cookie")||entry.getValue()==null)continue;
            for(String line:entry.getValue()){
                for(HttpCookie cookie:HttpCookie.parse(line)){
                    if(!cookie.getName().equals("oliva_session"))continue;
                    if(cookie.getMaxAge()==0||cookie.getValue().isEmpty()){clear();return;}
                    if(!valid(cookie.getValue())||cookie.getMaxAge()<1)throw new java.io.IOException("Сервер вернул неподходящую сессию входа.");
                    long until=System.currentTimeMillis()+Math.min(cookie.getMaxAge(),7L*24*60*60)*1000;
                    store.save(new JSONObject().put("token",cookie.getValue()).put("expires",until).toString());
                    token=cookie.getValue();expires=until;
                }
            }
        }
    }
}
