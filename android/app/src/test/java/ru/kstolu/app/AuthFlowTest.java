package ru.kstolu.app;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class AuthFlowTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;
    private static final String TOKEN="eyJ1c2VyIjoib3duZXIifQ."+"b".repeat(43);
    private final List<SessionTest.Connection> calls=new ArrayList<>();
    @Before public void prepare(){
        android.content.Context context=RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs=context.getSharedPreferences("auth-flow-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();
        Map<String,String> sealed=new HashMap<>();SessionStore store=new SessionStore(prefs,new SessionStore.Protector(){public String encrypt(String value){String key=UUID.randomUUID().toString();sealed.put(key,value);return key;}public String decrypt(String key){return sealed.get(key);}});
        ApiClient.useStoreForTests(store);ApiClient.configureForTests(new SessionCookies(store),url->{
            String path=url.getPath();Map<String,List<String>> headers=path.equals("/api/login")?Collections.singletonMap("Set-Cookie",List.of("oliva_session="+TOKEN+"; HttpOnly; SameSite=Strict; Path=/; Max-Age=604800; Secure")):Collections.emptyMap();
            SessionTest.Connection result=new SessionTest.Connection(url,200,path.equals("/api/status")?"{\"authenticated\":true}":"{\"ok\":true}",headers);calls.add(result);return result;
        });
        open();
    }
    private void open(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    private void stop(){controller.pause().stop().destroy();controller=null;}
    @After public void end(){if(controller!=null)stop();ApiClient.clearSession();ApiClient.restoreTransport();}
    private TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private EditText password(View view){if(view instanceof EditText)return(EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=password(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void click(String label){TextView view=find(activity.getWindow().getDecorView(),label);assertNotNull(label,view);view.performClick();}
    private void idle()throws Exception{Field busy=MainActivity.class.getDeclaredField("busy");busy.setAccessible(true);long until=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();if(!busy.getBoolean(activity))return;Thread.sleep(10);}fail("Login task did not complete");}
    @Test public void firstOpenPromptsOnceThenAnalyzeAndAppReopenDoNotAskAgain()throws Exception{
        assertNotNull(find(activity.getWindow().getDecorView(),"Войти"));assertNull(find(activity.getWindow().getDecorView(),"Подобрать блюда"));assertEquals(0,calls.size());
        password(activity.getWindow().getDecorView()).setText("test-only-owner-password");click("Войти");idle();
        assertEquals(2,calls.size());assertEquals("/api/login",calls.get(0).getURL().getPath());assertEquals("oliva_session="+TOKEN,calls.get(1).sent.get("Cookie"));
        assertNotNull(find(activity.getWindow().getDecorView(),"Подобрать блюда"));assertNull(find(activity.getWindow().getDecorView(),"Войти"));
        Field photo=MainActivity.class.getDeclaredField("productPhoto");photo.setAccessible(true);photo.set(activity,new byte[]{1,2,3});click("Подобрать блюда");
        assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());assertEquals(2,calls.size());
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().dismiss();stop();open();idle();
        assertEquals(3,calls.size());assertEquals("/api/status",calls.get(2).getURL().getPath());assertEquals("oliva_session="+TOKEN,calls.get(2).sent.get("Cookie"));assertNotNull(find(activity.getWindow().getDecorView(),"Подобрать блюда"));
        click("Настройки");click("Выйти");assertNotNull(find(activity.getWindow().getDecorView(),"Войти"));assertFalse(ApiClient.hasSession());stop();open();assertEquals(3,calls.size());assertNotNull(find(activity.getWindow().getDecorView(),"Войти"));
    }
}
