package ru.kstolu.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    private org.robolectric.android.controller.ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void setup(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    @After public void close(){controller.pause().stop().destroy();}
    private TextView find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void click(String label){TextView view=find(activity.getWindow().getDecorView(),label);assertNotNull(label,view);view.performClick();}
    private void render(String name)throws Exception{renderView(activity.getWindow().getDecorView(),name);}
    private void renderView(View root,String name)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(844,View.MeasureSpec.EXACTLY));root.layout(0,0,390,844);
        Bitmap bitmap=Bitmap.createBitmap(390,844,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File dir=new File("build/visual-qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    @Test public void nativeHomeSettingsHistoryScreens()throws Exception{
        assertNotNull(find(activity.getWindow().getDecorView(),"К столу"));render("01-home");click("Настройки");assertNotNull(find(activity.getWindow().getDecorView(),"Сохранить настройки"));render("02-settings");click("История");render("03-empty-history");assertNotNull(find(activity.getWindow().getDecorView(),"Здесь появятся сохранённые рецепты."));
    }
    @Test public void nativeLoginDialogDoesNotStorePassword()throws Exception{
        click("Войти по паролю приложения");android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);renderView(dialog.getWindow().getDecorView(),"05-login");dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
    }
    @Test public void recipeKeepsUnknownPricesAndDoesNotGenerateAutomatically()throws Exception{
        String json="{\"recipe\":{\"status\":\"ok\",\"title\":\"Бутерброды с авокадо\",\"description\":\"Хлеб с творожным сыром и ломтиками авокадо.\",\"servings\":2,\"prep_minutes\":10,\"cook_minutes\":0,\"ingredients\":[{\"name\":\"Хлеб\",\"amount\":2,\"unit\":\"piece\",\"price\":null},{\"name\":\"Авокадо\",\"amount\":1,\"unit\":\"piece\",\"price\":null}],\"steps\":[{\"title\":\"Соберите\",\"body\":\"Намажьте хлеб сыром, сверху выложите ломтики авокадо.\"}],\"warnings\":[]},\"currency\":\"RUB\",\"id\":\"test\"}";
        JSONObject snapshot=new JSONObject(json);Method open=MainActivity.class.getDeclaredMethod("openRecipe",JSONObject.class,String.class);open.setAccessible(true);open.invoke(activity,snapshot,"history");render("04-recipe");
        assertNull(find(activity.getWindow().getDecorView(),"Фото блюда · создать / продолжить"));Method cost=MainActivity.class.getDeclaredMethod("cost",JSONObject.class,int.class,String.class);cost.setAccessible(true);assertEquals("Цена неизвестна",cost.invoke(activity,snapshot.getJSONObject("recipe"),2,"RUB"));
        JSONArray items=new JSONArray().put(snapshot);Field choices=MainActivity.class.getDeclaredField("choices");choices.setAccessible(true);choices.set(activity,items);Method choose=MainActivity.class.getDeclaredMethod("showChoices");choose.setAccessible(true);choose.invoke(activity);render("06-choices");open.invoke(activity,snapshot,"history");
        click("Сохранить рецепт");click("История");assertNotNull(find(activity.getWindow().getDecorView(),"Бутерброды с авокадо"));render("03-history");
        Bitmap sample=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);sample.eraseColor(0xffeeeeee);ByteArrayOutputStream bytes=new ByteArrayOutputStream();sample.compress(Bitmap.CompressFormat.JPEG,85,bytes);sample.recycle();
        Method preview=MainActivity.class.getDeclaredMethod("preview",JSONObject.class,byte[].class);preview.setAccessible(true);preview.invoke(activity,snapshot,bytes.toByteArray());android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);renderView(dialog.getWindow().getDecorView(),"07-photo-preview");dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(find(activity.getWindow().getDecorView(),"Фото отклонено. Новая генерация не запускалась."));
    }
}
