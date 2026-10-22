package ru.kstolu.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout shell,body,nav; private TextView notice;
    private android.content.SharedPreferences prefs;
    private boolean authenticated=false,busy=false,receiptTarget=false;
    private byte[] productPhoto,receiptPhoto; private Uri cameraUri;
    private JSONArray choices=new JSONArray(),history=new JSONArray();
    private JSONObject selected; private String page="home",returnPage="choices"; private int portions=2;
    private static final int BG=0xfff8f7f0,INK=0xff303928,OLIVE=0xff4e5d3b,MUTED=0xff6d7165,BORDER=0xffd8ddcc;
    interface Task { Object run() throws Exception; }
    interface Done { void accept(Object value) throws Exception; }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("settings",MODE_PRIVATE);ApiClient.resetSession();readHistory();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=35)getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        if(state!=null){String uri=state.getString("cameraUri");if(uri!=null)cameraUri=Uri.parse(uri);receiptTarget=state.getBoolean("receiptTarget");}
        home();
    }
    @Override public void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(cameraUri!=null)out.putString("cameraUri",cameraUri.toString());out.putBoolean("receiptTarget",receiptTarget);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(busy){toast("Дождитесь завершения запроса. Повтор не запускается.");return;}if(page.equals("detail")){if(returnPage.equals("history"))showHistory();else showChoices();}else if(!page.equals("home"))home();else super.onBackPressed();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int fill){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(14));d.setStroke(dp(1),BORDER);return d;}
    private void screen(String name,String heading){
        page=name;shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(24),dp(24),dp(24));scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        notice=text(busy?"Запрос выполняется…":"",14);notice.setTextColor(MUTED);body.addView(notice);title(heading);
        nav=new LinearLayout(this);nav.setPadding(dp(8),dp(8),dp(8),dp(8));
        for(String[] tab:new String[][]{{"Главная","home"},{"История","history"},{"Настройки","settings"}}){Button b=buttonView(tab[0],()->{if(busy){toast("Сначала дождитесь завершения запроса.");return;}switch(tab[1]){case "history":showHistory();break;case "settings":settings();break;default:home();}});b.setTextSize(14);nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}
        shell.addView(nav);setContentView(shell);
    }
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(INK);v.setLineSpacing(dp(4),1);v.setPadding(0,dp(4),0,dp(8));return v;}
    private void title(String value){TextView v=text(value,30);v.setTypeface(Typeface.create("serif",Typeface.NORMAL));body.addView(v);}
    private void paragraph(String value){body.addView(text(value,16));}
    private void space(){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(16)));}
    private Button buttonView(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(OLIVE);b.setMinHeight(dp(48));b.setBackground(background(0xfff0f2e8));if(label.equals("Подобрать блюда")||label.equals("Сохранить рецепт")||label.equals("Сохранить настройки")){b.setTextColor(BG);b.setBackground(background(OLIVE));}b.setOnClickListener(v->action.run());return b;}
    private void button(String label,Runnable action){Button b=buttonView(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);}
    private EditText field(String label,String value){paragraph(label);EditText edit=new EditText(this);edit.setText(value);edit.setTextSize(16);edit.setSingleLine(true);body.addView(edit,new LinearLayout.LayoutParams(-1,dp(52)));return edit;}
    private void message(String value){if(notice!=null)notice.setText(value);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Отмена",null).setPositiveButton("Продолжить",(d,w)->yes.run()).show();}
    private void task(Task work,Done done){
        if(busy){toast("Запрос уже выполняется.");return;}busy=true;message("Запрос выполняется. Не отправляйте его повторно.");
        worker.execute(()->{Object value=null;Exception error=null;try{value=work.run();}catch(Exception e){error=e;}final Object result=value;final Exception failure=error;
            ui.post(()->{if(isFinishing()||isDestroyed())return;busy=false;if(failure!=null){
                if(failure instanceof ApiClient.ApiError){ApiClient.ApiError e=(ApiClient.ApiError)failure;if(e.status==401)authenticated=false;if(e.failedJob)prefs.edit().remove("pendingImage").apply();}
                message(failure.getMessage()==null?"Ошибка. Повтор автоматически не отправлен.":failure.getMessage());
            }else try{done.accept(result);}catch(Exception e){message("Не удалось сохранить результат: "+e.getMessage());}});
        });
    }
    private void home(){screen("home","К столу");paragraph("Вкусное из того, что есть");space();
        paragraph("Сфотографируйте продукты — выберем, что приготовить. История хранится только в этом приложении.");
        button(productPhoto==null?"Добавить фото продуктов":"Заменить фото продуктов",()->photoMenu(false));
        if(productPhoto!=null)showPhoto(productPhoto,160);
        button(receiptPhoto==null?"Добавить чек · необязательно":"Заменить фото чека",()->photoMenu(true));
        if(receiptPhoto!=null)button("Убрать чек",()->{receiptPhoto=null;home();});
        button("Подобрать блюда",()->{if(busy)return;if(productPhoto==null){toast("Сначала добавьте фото продуктов.");return;}if(!authenticated){login();return;}
            confirm("Передача фотографий","Фото продуктов и, если добавлен, чек будут переданы в AnyModel и поставщику модели. Подбор использует настроенный на сервере тариф. Цены в рецептах — ориентиры, если не прочитан чек.",()->analyze());});
        button(authenticated?"Проверить соединение":"Войти по паролю приложения",()->{if(authenticated)task(()->ApiClient.call("/api/status",null),v->{authenticated=((JSONObject)v).optBoolean("authenticated");message(authenticated?"Сервер доступен. Вы вошли.":"Нужен вход по паролю.");});else login();});
        if(productPhoto!=null)button("Убрать выбранные фотографии",()->{productPhoto=null;receiptPhoto=null;home();});
        paragraph("Для подбора нужен интернет. После простоя сервер Render может запускаться около минуты. Пароли и ключи API в приложение не вшиты.");
    }
    private void login(){
        if(busy)return;EditText password=new EditText(this);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSingleLine(true);password.setSaveEnabled(false);password.setHint("Пароль приложения");password.setPadding(dp(24),dp(8),dp(24),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Войти в «К столу»").setMessage("Это APP_PASSWORD, не ключ AnyModel и не пароль GitHub. Пароль на устройстве не сохраняется.").setView(password).setNegativeButton("Отмена",null).setPositiveButton("Войти",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=password.getText().toString();password.setText("");dialog.dismiss();task(()->ApiClient.call("/api/login",new JSONObject().put("password",value)),result->{authenticated=true;home();message("Вход выполнен. Теперь можно подобрать блюда.");});}));dialog.show();
    }
    private void photoMenu(boolean receipt){
        if(busy)return;receiptTarget=receipt;
        new AlertDialog.Builder(this).setTitle(receipt?"Фото чека":"Фото продуктов").setItems(new String[]{"Снять камерой","Выбрать фотографию"},(d,which)->{
            try{if(which==1){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,11);}else{
                File folder=new File(getCacheDir(),"camera");folder.mkdirs();File photo=new File(folder,"capture.jpg");cameraUri=FileProvider.getUriForFile(this,getPackageName()+".files",photo);
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);intent.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Фото",cameraUri));startActivityForResult(intent,12);
            }}catch(Exception e){toast("Не удалось открыть камеру или галерею. Попробуйте другой способ.");}
        }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        Uri uri=request==12?cameraUri:data==null?null:data.getData();if(uri==null)return;boolean target=receiptTarget;
        task(()->readJpeg(uri),value->{if(target)receiptPhoto=(byte[])value;else productPhoto=(byte[])value;if(request==12){new File(getCacheDir(),"camera/capture.jpg").delete();}home();});
    }
    private byte[] readJpeg(Uri uri)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream input=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,bounds);}if(bounds.outWidth<16||bounds.outHeight<16)throw new IOException("Не удалось прочитать фото.");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2200)options.inSampleSize*=2;
        Bitmap bitmap;try(InputStream input=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(input,null,options);}if(bitmap==null)throw new IOException("Неподдерживаемый формат фото.");
        int orientation=ExifInterface.ORIENTATION_NORMAL;try(InputStream input=getContentResolver().openInputStream(uri)){orientation=new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){}
        Matrix transform=new Matrix();switch(orientation){case 2:transform.setScale(-1,1);break;case 3:transform.setRotate(180);break;case 4:transform.setScale(1,-1);break;case 5:transform.setRotate(90);transform.postScale(-1,1);break;case 6:transform.setRotate(90);break;case 7:transform.setRotate(270);transform.postScale(-1,1);break;case 8:transform.setRotate(270);break;default:break;}
        Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;
        return compress(bitmap,1500);
    }
    private byte[] compress(Bitmap bitmap,int limit)throws IOException{
        double scale=Math.min(1,(double)limit/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);if(scaled!=bitmap)bitmap.recycle();
        ByteArrayOutputStream out=new ByteArrayOutputStream();for(int quality=85;quality>=45;quality-=10){out.reset();scaled.compress(Bitmap.CompressFormat.JPEG,quality,out);if(out.size()<=2*1024*1024)break;}scaled.recycle();if(out.size()>2*1024*1024)throw new IOException("Фото слишком большое даже после сжатия.");return out.toByteArray();
    }
    private void showPhoto(byte[] data,int height){BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opt);opt.inSampleSize=1;while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>1200)opt.inSampleSize*=2;opt.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opt);if(bitmap==null)return;ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Фото блюда или выбранных продуктов");body.addView(image,new LinearLayout.LayoutParams(-1,dp(height)));}
    private void analyze(){
        final byte[] photo=productPhoto,receipt=receiptPhoto;
        final String region=prefs.getString("region","Россия"),currency=prefs.getString("currency","RUB");
        task(()->{JSONObject request=new JSONObject().put("image",dataUrl(photo)).put("region",region).put("currency",currency).put("consent",true);if(receipt!=null)request.put("receipt",dataUrl(receipt));return ApiClient.call("/api/analyze",request);},value->{
            JSONObject out=(JSONObject)value;if(!out.optString("status").equals("ok")){JSONObject problem=out.optJSONObject("recipe");message(problem==null?"Нужен другой снимок.":problem.optString("message","Нужен другой снимок."));return;}
            JSONArray recipes=out.getJSONArray("recipes");choices=new JSONArray();for(int i=0;i<recipes.length();i++)choices.put(new JSONObject().put("recipe",recipes.getJSONObject(i)).put("currency",out.optString("currency",currency)).put("region",region).put("id",UUID.randomUUID().toString()));
            productPhoto=null;receiptPhoto=null;showChoices();
        });
    }
    private String dataUrl(byte[] bytes){return "data:image/jpeg;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP);}
    private void showChoices(){screen("choices","Что приготовим?");if(choices.length()==0){paragraph("Пока нет подбора. Добавьте фото на главной.");return;}
        paragraph("Выберите блюдо. Повторное открытие рецепта не вызывает нейросеть.");for(int i=0;i<choices.length();i++){JSONObject item=choices.optJSONObject(i);JSONObject recipe=item.optJSONObject("recipe");space();button(recipe.optString("title"),()->openRecipe(item,"choices"));paragraph((recipe.optInt("prep_minutes")+recipe.optInt("cook_minutes"))+" мин · "+cost(recipe,recipe.optInt("servings",2),item.optString("currency")));}}
    private void openRecipe(JSONObject item,String back){selected=item;portions=item.optInt("portions",item.optJSONObject("recipe").optInt("servings",2));returnPage=back;detail();}
    private void detail(){if(selected==null){home();return;}JSONObject recipe=selected.optJSONObject("recipe");screen("detail",recipe.optString("title"));button(returnPage.equals("history")?"← К истории":"← Ко всем блюдам",()->{if(busy)return;if(returnPage.equals("history"))showHistory();else showChoices();});
        try{File file=imageFile(recipe);if(file.isFile()){showPhoto(readFile(file,3*1024*1024),240);paragraph("Изображение ИИ · принято вами");}}catch(Exception e){paragraph("Сохранённое фото не удалось открыть.");}
        paragraph(recipe.optString("description"));paragraph((recipe.optInt("prep_minutes")+recipe.optInt("cook_minutes"))+" мин · "+cost(recipe,portions,selected.optString("currency")));
        LinearLayout row=new LinearLayout(this);Button minus=buttonView("−",()->{if(portions>1){portions--;detail();}}),plus=buttonView("+",()->{if(portions<8){portions++;detail();}});row.addView(minus,new LinearLayout.LayoutParams(0,dp(48),1));TextView serving=text("Порций: "+portions,16);serving.setGravity(Gravity.CENTER);row.addView(serving,new LinearLayout.LayoutParams(0,dp(48),2));row.addView(plus,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(row);
        button("Сохранить рецепт",()->{try{saveSelected();toast("Рецепт сохранён на устройстве.");}catch(Exception e){message("Не удалось сохранить рецепт.");}});
        if(prefs.getBoolean("generation",false))button("Фото блюда · создать / продолжить",()->generate(false));
        try{if(imageFile(recipe).exists())button("Удалить фото этого блюда",()->confirm("Удалить фото?","Рецепт останется. Новая генерация автоматически не запускается.",()->{try{if(!imageFile(recipe).delete())throw new IOException();detail();}catch(Exception e){message("Не удалось удалить фото.");}}));}catch(Exception ignored){}
        space();title("Ингредиенты");JSONArray ingredients=recipe.optJSONArray("ingredients");double factor=(double)portions/recipe.optInt("servings",2);for(int i=0;i<ingredients.length();i++){JSONObject x=ingredients.optJSONObject(i);String unit=x.optString("unit");paragraph(x.optString("name")+" — "+amount(x.optDouble("amount")*factor)+" "+(unit.equals("g")?"г":unit.equals("ml")?"мл":"шт."));}
        space();title("Готовим по шагам");JSONArray steps=recipe.optJSONArray("steps");for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i);CheckBox box=new CheckBox(this);box.setText((i+1)+". "+step.optString("title"));box.setTextSize(17);box.setTextColor(INK);body.addView(box);paragraph(step.optString("body"));space();}
        JSONArray warnings=recipe.optJSONArray("warnings");if(warnings!=null&&warnings.length()>0){title("Обратите внимание");for(int i=0;i<warnings.length();i++)paragraph("• "+warnings.optString(i));}button("Поделиться рецептом",()->share(recipe));
    }
    private static String amount(double v){return Math.abs(v-Math.rint(v))<.001?String.valueOf(Math.round(v)):String.format(Locale.getDefault(),"%.1f",v);}
    private String cost(JSONObject recipe,int servings,String currency){
        double lo=0,hi=0;int missing=0;JSONArray list=recipe.optJSONArray("ingredients");for(int i=0;i<list.length();i++){JSONObject x=list.optJSONObject(i),price=x.optJSONObject("price");if(price==null||price.optDouble("quantity",0)<=0){missing++;continue;}double ratio=x.optDouble("amount")/price.optDouble("quantity")*servings/recipe.optInt("servings",2);lo+=price.optDouble("low")*ratio;hi+=price.optDouble("high")*ratio;}
        if(missing==list.length())return "Цена неизвестна";return String.format(Locale.getDefault(),"≈ %.0f–%.0f %s%s",lo,hi,currency,missing>0?" + неизвестные цены":"");
    }
    private void share(JSONObject recipe){StringBuilder text=new StringBuilder(recipe.optString("title")+"\n\n");JSONArray list=recipe.optJSONArray("ingredients");for(int i=0;i<list.length();i++){JSONObject x=list.optJSONObject(i);text.append(x.optString("name")).append(" — ").append(amount(x.optDouble("amount")*portions/recipe.optInt("servings",2))).append(" ").append(x.optString("unit")).append('\n');}JSONArray steps=recipe.optJSONArray("steps");for(int i=0;i<steps.length();i++)text.append('\n').append(i+1).append(". ").append(steps.optJSONObject(i).optString("body"));Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,text.toString());startActivity(Intent.createChooser(intent,"Поделиться рецептом"));}
    private void saveSelected()throws Exception{
        selected.put("portions",portions);selected.put("saved",System.currentTimeMillis());JSONArray next=new JSONArray();next.put(new JSONObject(selected.toString()));String key=key(selected.getJSONObject("recipe"));for(int i=0;i<history.length()&&next.length()<50;i++){JSONObject row=history.getJSONObject(i);if(!key(row.getJSONObject("recipe")).equals(key))next.put(row);}writeHistory(next);history=next;
    }
    private void readHistory(){try{history=new JSONArray(new String(readFile(new File(getFilesDir(),"history.json"),4*1024*1024),StandardCharsets.UTF_8));}catch(Exception e){history=new JSONArray();}}
    private void writeHistory(JSONArray value)throws Exception{android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"history.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    private void showHistory(){screen("history","Сохранённое");paragraph("До 50 рецептов. Доступны без интернета. История сайта и Windows сюда автоматически не переносится.");if(history.length()==0)paragraph("Здесь появятся сохранённые рецепты.");for(int i=0;i<history.length();i++){JSONObject row=history.optJSONObject(i);button(row.optJSONObject("recipe").optString("title"),()->openRecipe(row,"history"));}
        if(history.length()>0)button("Удалить всю историю и фото",()->confirm("Очистить историю?","Все рецепты и фото Android-приложения будут удалены. Это действие нельзя отменить.",()->{try{writeHistory(new JSONArray());history=new JSONArray();File[] images=new File(getFilesDir(),"images").listFiles();if(images!=null)for(File f:images)f.delete();showHistory();}catch(Exception e){message("Не удалось очистить историю.");}}));
    }
    private String key(JSONObject recipe)throws Exception{String value=recipe.optString("title")+"\n"+recipe.getJSONArray("ingredients").toString()+"\n"+recipe.getJSONArray("steps").toString();byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private File imageFile(JSONObject recipe)throws Exception{File folder=new File(getFilesDir(),"images");folder.mkdirs();return new File(folder,key(recipe)+".jpg");}
    private static byte[] readFile(File file,int max)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("Файл слишком большой.");out.write(buffer,0,n);}return out.toByteArray();}}
    private void settings(){screen("settings","Настройки");EditText region=field("Регион для оценки цен",prefs.getString("region","Россия"));paragraph("Валюта");Spinner currency=new Spinner(this);String[] currencies={"RUB","KZT","BYN","EUR","USD"};currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,currencies));currency.setSelection(Math.max(0,Arrays.asList(currencies).indexOf(prefs.getString("currency","RUB"))));body.addView(currency,new LinearLayout.LayoutParams(-1,dp(52)));
        Switch generation=new Switch(this);generation.setText("Разрешить генерацию фото");generation.setTextSize(16);generation.setChecked(prefs.getBoolean("generation",false));body.addView(generation);paragraph("Создание фото только по отдельному нажатию. Перед сохранением результат нужно принять. Бесплатность зависит от настроек и тарифа сервера.");
        button("Сохранить настройки",()->{String r=region.getText().toString().trim();if(r.length()<2||r.length()>100){message("Укажите регион длиной от 2 до 100 символов.");return;}prefs.edit().putString("region",r).putString("currency",currency.getSelectedItem().toString()).putBoolean("generation",generation.isChecked()).apply();message("Настройки сохранены.");});
        button("Проверить генератор",()->task(()->ApiClient.call("/api/image-options",null),v->message(((JSONObject)v).optString("message"))));
        if(prefs.contains("pendingImage")){button("Продолжить получение прежнего фото",()->generate(true));button("Убрать незавершённое задание",()->confirm("Убрать задание?","Это не отменит его у поставщика. Следующее создание фото будет новым запросом.",()->{prefs.edit().remove("pendingImage").apply();settings();}));}
        button("Выйти",()->task(()->ApiClient.call("/api/logout",new JSONObject()),v->{ApiClient.resetSession();authenticated=false;home();}));
        paragraph("К столу · 0.7.0 · первая нативная версия\nДля распознавания и генерации нужен интернет. Секретные API-ключи находятся только на сервере. При удалении приложения локальная история исчезнет. Веб-версия и Windows используют отдельное хранилище.");
    }
    private void generate(boolean restore){
        if(busy)return;if(!authenticated){login();return;}if(!prefs.getBoolean("generation",false)){toast("Сначала разрешите генерацию в настройках.");return;}
        try{
            JSONObject pending=prefs.contains("pendingImage")?new JSONObject(prefs.getString("pendingImage","{}")):null;
            if(restore&&pending!=null){selected=pending.getJSONObject("snapshot");portions=selected.optInt("portions",selected.getJSONObject("recipe").optInt("servings",2));runGeneration(pending);return;}
            if(selected==null){toast("Сначала откройте рецепт.");return;}
            if(pending!=null){if(key(pending.getJSONObject("snapshot").getJSONObject("recipe")).equals(key(selected.getJSONObject("recipe")))){runGeneration(pending);return;}toast("Сначала получите или уберите прежнее задание в настройках.");return;}
            File[] photos=new File(getFilesDir(),"images").listFiles();if(photos!=null&&photos.length>=50&&!imageFile(selected.getJSONObject("recipe")).exists()){message("Сохранено 50 фото. Удалите ненужное фото в меню рецепта.");return;}
            if(selected.getJSONObject("recipe").optString("image_prompt").length()<40){message("Для старого рецепта нужен новый подбор с описанием фотографии.");return;}
            confirm("Создать одно фото блюда?","Название, состав и описание блюда отправятся настроенному поставщику изображений. Исходные фото и чек не отправляются. До 5 попыток в сутки; результат может не совпасть с рецептом.",()->{
                try{selected.put("portions",portions);JSONObject job=new JSONObject().put("requestId",UUID.randomUUID().toString()).put("snapshot",new JSONObject(selected.toString()));if(!prefs.edit().putString("pendingImage",job.toString()).commit())throw new IOException("Не удалось сохранить задание");runGeneration(job);}catch(Exception e){message("Не удалось сохранить задание. Запрос не отправлен.");}
            });
        }catch(Exception e){message("Не удалось прочитать задание. Уберите его в настройках.");}
    }
    private void runGeneration(JSONObject pending){
        task(()->{
            JSONObject snapshot=pending.getJSONObject("snapshot"),result;
            if(pending.has("id"))result=ApiClient.call("/api/images/"+pending.getString("id"),null);else{
                JSONObject options=ApiClient.call("/api/image-options",null);if(!options.optBoolean("available"))throw new IOException(options.optString("message"));
                result=ApiClient.call("/api/images",new JSONObject().put("recipe",snapshot.getJSONObject("recipe")).put("currency",snapshot.optString("currency","RUB")).put("consent",true).put("requestId",pending.getString("requestId")));
                if(result.has("id")){pending.put("id",result.getString("id"));if(!prefs.edit().putString("pendingImage",pending.toString()).commit())throw new IOException("Не удалось записать номер задания. Не создавайте новое фото.");}
            }
            long deadline=System.currentTimeMillis()+300000;
            while(result.optString("status").equals("pending")){if(System.currentTimeMillis()>deadline)throw new IOException("Фото ещё создаётся. Продолжите получение в настройках, новый запрос не нужен.");Thread.sleep(5000);result=ApiClient.call("/api/images/"+pending.getString("id"),null);}
            if(result.optString("status").equals("failed")){prefs.edit().remove("pendingImage").commit();throw new IOException("Генератор отказал. Новая попытка автоматически не отправлена.");}
            if(!result.optString("status").equals("done"))throw new IOException("Не удалось получить готовое фото.");
            String encoded=result.getString("image");if(!encoded.matches("^data:image/(jpeg|png);base64,[A-Za-z0-9+/=]+$")||encoded.length()>9*1024*1024)throw new IOException("Неподходящее фото.");byte[] bytes=Base64.decode(encoded.substring(encoded.indexOf(',')+1),Base64.DEFAULT);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>16777216)throw new IOException("Неподходящее разрешение фото.");
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)throw new IOException("Не удалось открыть фото.");return new Object[]{snapshot,compress(bitmap,960)};
        },value->{Object[] result=(Object[])value;preview((JSONObject)result[0],(byte[])result[1]);});
    }
    private void preview(JSONObject snapshot,byte[] bytes){
        selected=snapshot;portions=snapshot.optInt("portions",snapshot.optJSONObject("recipe").optInt("servings",2));returnPage="history";detail();
        LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(20),dp(12),dp(20),dp(12));ImageView image=new ImageView(this);image.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Предпросмотр изображения ИИ");view.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));view.addView(text(snapshot.optJSONObject("recipe").optString("title")+"\n\nПроверьте ингредиенты и вид блюда. Пока фото не сохранено. Отказ не вернёт потраченную попытку.",16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Фото подходит?").setView(view).setNegativeButton("Не сохранять",(d,w)->{prefs.edit().remove("pendingImage").apply();message("Фото отклонено. Новая генерация не запускалась.");}).setPositiveButton("Сохранить",null).create();
        dialog.setOnCancelListener(d->{prefs.edit().remove("pendingImage").apply();message("Фото не сохранено.");});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{File file=imageFile(snapshot.getJSONObject("recipe"));android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}selected=snapshot;saveSelected();prefs.edit().remove("pendingImage").apply();dialog.dismiss();detail();message("Фото и рецепт сохранены на устройстве.");}catch(Exception e){toast("Не удалось сохранить фото. Освободите место и попробуйте снова.");}}));dialog.show();
    }
}
