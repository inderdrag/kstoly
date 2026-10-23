package ru.kstolu.app;
import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import org.json.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class FeaturesTest {
 @Test public void cookingExpiryOnlyOnceAndRebootPauses()throws Exception{CookingState s=new CookingState();s.start(120,1000,1);assertFalse(s.expire(120000));assertTrue(s.expire(121000));assertEquals(1,s.index);assertFalse(s.expire(200000));s.start(120,300000,1);s.remaining=s.left(301000);CookingState restored=CookingState.restore(s.json(),2);assertFalse(restored.running);assertEquals(119000,restored.remaining);}
 @Test public void librarySearchFoldersRatingRemain()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();JSONObject r=new JSONObject("{\"title\":\"Тосты с авокадо\",\"ingredients\":[{\"name\":\"Хлеб\"}],\"steps\":[{\"title\":\"Соберите\",\"body\":\"Готово\"}]}");assertTrue(Library.matches(r,"хлеб"));assertTrue(Library.addFolder(c,"Ужины"));Library.rate(c,r,5);assertEquals(5,Library.rating(c,r));Library.save(c,new JSONObject().put("recipe",r));assertEquals(1,Library.read(c).length());}
}
