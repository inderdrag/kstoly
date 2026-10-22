package ru.kstolu.app;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    static final String ORIGIN="https://oliva-photo-helper.onrender.com";
    static final class ApiError extends IOException {
        final int status; final boolean failedJob;
        ApiError(String message,int status,boolean failedJob){super(message);this.status=status;this.failedJob=failedJob;}
    }
    private static SessionCookies session;
    interface StoreFactory { SessionStore create(android.content.Context context); }
    private static StoreFactory stores=SessionStore::new;
    interface Transport { HttpURLConnection open(URL url)throws Exception; }
    private static Transport transport=url->(HttpURLConnection)url.openConnection();
    static synchronized void initialize(android.content.Context context){
        // HttpURLConnection's default CookieManager may emit RFC2965 quotes and
        // $Version metadata. The server expects a simple browser-style cookie.
        CookieHandler.setDefault(null);
        session=new SessionCookies(stores.create(context.getApplicationContext()));
    }
    static synchronized boolean hasSession(){return session!=null&&session.hasSession();}
    static synchronized void clearSession(){if(session!=null)session.clear();}
    static void configureForTests(SessionCookies cookies,Transport testTransport){session=cookies;transport=testTransport;CookieHandler.setDefault(null);}
    static void useStoreForTests(SessionStore store){stores=context->store;}
    static void restoreTransport(){transport=url->(HttpURLConnection)url.openConnection();stores=SessionStore::new;}

    static JSONObject call(String path,JSONObject data) throws Exception {
        if(!path.startsWith("/api/")||path.contains("..")||path.contains("\\"))throw new IOException("Неверный адрес запроса.");
        HttpURLConnection connection=transport.open(new URL(ORIGIN+path));
        connection.setConnectTimeout(30000);connection.setReadTimeout(130000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        if(session==null)throw new IOException("Сначала инициализируйте вход приложения.");
        String cookie=session.header();if(!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
        try{
            if(data!=null){
                connection.setRequestMethod("POST");connection.setRequestProperty("Origin",ORIGIN);connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
                byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=connection.getOutputStream()){out.write(body);}
            }
            int status=connection.getResponseCode();
            if(status==401)session.clear();
            if(status>=200&&status<300&&(path.equals("/api/login")||path.equals("/api/logout")))session.accept(connection.getHeaderFields());
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            if(stream==null)throw new IOException("Пустой ответ сервера.");
            byte[] bytes;
            try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1){total+=count;if(total>10*1024*1024)throw new IOException("Ответ сервера слишком большой.");out.write(buffer,0,count);}bytes=out.toByteArray();
            }
            JSONObject result;
            try{result=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(Exception e){throw new IOException("Сервер пока не готов или вернул непонятный ответ. Подождите и попробуйте позже.");}
            if(status<200||status>=300)throw new ApiError(result.optString("error","Ошибка сервера: "+status),status,result.optBoolean("imageJobFailed"));
            return result;
        }finally{connection.disconnect();}
    }
}
