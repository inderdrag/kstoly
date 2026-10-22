package ru.kstolu.app;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.content.*;
import android.util.Base64;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class SessionTest {
    private SharedPreferences prefs;private SessionStore store;private SessionStore.Protector protector;
    private static final String TOKEN="eyJ1c2VyIjoib3duZXIifQ."+"a".repeat(43);
    @Before public void setup()throws Exception{
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("session-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();
        SecretKey key=KeyGenerator.getInstance("AES").generateKey();
        protector=new SessionStore.Protector(){
            public String encrypt(String value)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
            public String decrypt(String value)throws Exception{String[] p=value.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
        };
        store=new SessionStore(prefs,protector);
    }
    @After public void close(){ApiClient.clearSession();ApiClient.restoreTransport();}
    private Map<String,List<String>> headers(String token,int age){return Collections.singletonMap("Set-Cookie",List.of("oliva_session="+token+"; HttpOnly; SameSite=Strict; Path=/; Max-Age="+age+"; Secure"));}
    @Test public void sessionCookieIsUnquotedAndSurvivesRecreation()throws Exception{
        SessionCookies first=new SessionCookies(store);first.accept(headers(TOKEN,604800));
        assertEquals("oliva_session="+TOKEN,first.header());assertFalse(first.header().contains("$Version"));assertFalse(first.header().contains("\""));
        assertFalse(prefs.getAll().toString().contains(TOKEN));assertFalse(prefs.contains("password"));
        SessionCookies restored=new SessionCookies(new SessionStore(prefs,protector));assertTrue(restored.hasSession());assertEquals(first.header(),restored.header());
        restored.accept(headers("",0));assertFalse(restored.hasSession());assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void damagedSavedSessionCannotBecomeAuthenticated()throws Exception{
        new SessionCookies(store).accept(headers(TOKEN,100));prefs.edit().putString("encrypted","broken-ciphertext").commit();
        assertFalse(new SessionCookies(store).hasSession());assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void expiredSavedSessionIsRemoved()throws Exception{
        store.save(new JSONObject().put("token",TOKEN).put("expires",System.currentTimeMillis()-1000).toString());assertFalse(new SessionCookies(store).hasSession());assertTrue(prefs.getAll().isEmpty());
    }
    static final class Connection extends HttpURLConnection {
        final Map<String,String> sent=new HashMap<>();final ByteArrayOutputStream body=new ByteArrayOutputStream();final Map<String,List<String>> replyHeaders;final int status;final String reply;
        Connection(URL u,int status,String reply,Map<String,List<String>> headers){super(u);this.status=status;this.reply=reply;replyHeaders=headers;}
        @Override public void setRequestProperty(String k,String v){sent.put(k,v);}
        @Override public int getResponseCode(){return status;}
        @Override public Map<String,List<String>> getHeaderFields(){return replyHeaders;}
        @Override public OutputStream getOutputStream(){return body;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(reply.getBytes(StandardCharsets.UTF_8));}
        @Override public InputStream getErrorStream(){return getInputStream();}
        @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
    }
    @Test public void loginStatusAnalyzeAndReopenAllUseTheSameSessionWithoutPasswordReplay()throws Exception{
        List<Connection> requests=new ArrayList<>();ApiClient.Transport transport=url->{String path=url.getPath();Connection c=new Connection(url,200,path.equals("/api/status")?"{\"authenticated\":true}":"{\"ok\":true}",path.equals("/api/login")?headers(TOKEN,604800):Collections.emptyMap());requests.add(c);return c;};
        ApiClient.configureForTests(new SessionCookies(store),transport);
        ApiClient.call("/api/login",new JSONObject().put("password","test-only-not-a-real-password"));
        assertTrue(ApiClient.call("/api/status",null).getBoolean("authenticated"));
        ApiClient.call("/api/analyze",new JSONObject().put("test",true));
        ApiClient.configureForTests(new SessionCookies(new SessionStore(prefs,protector)),transport);
        assertTrue(ApiClient.hasSession());ApiClient.call("/api/status",null);ApiClient.call("/api/analyze",new JSONObject().put("test",true));
        assertEquals(5,requests.size());for(int i=1;i<requests.size();i++){assertEquals("oliva_session="+TOKEN,requests.get(i).sent.get("Cookie"));assertFalse(requests.get(i).body.toString().contains("password"));}
        assertEquals(ApiClient.ORIGIN,requests.get(2).sent.get("Origin"));ApiClient.clearSession();assertFalse(ApiClient.hasSession());assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void unauthorizedResponseClearsSavedSessionAndDoesNotRetry()throws Exception{
        SessionCookies cookies=new SessionCookies(store);cookies.accept(headers(TOKEN,604800));int[] calls={0};ApiClient.configureForTests(cookies,url->{calls[0]++;return new Connection(url,401,"{\"error\":\"Войдите снова\"}",Collections.emptyMap());});
        try{ApiClient.call("/api/image-options",null);fail();}catch(ApiClient.ApiError e){assertEquals(401,e.status);}
        assertEquals(1,calls[0]);assertFalse(ApiClient.hasSession());assertTrue(prefs.getAll().isEmpty());
    }
}
