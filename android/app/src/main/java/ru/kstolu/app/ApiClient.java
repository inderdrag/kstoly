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
    static void resetSession(){CookieHandler.setDefault(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER));}
    static JSONObject call(String path,JSONObject data) throws Exception {
        if(!path.startsWith("/api/")||path.contains("..")||path.contains("\\"))throw new IOException("Неверный адрес запроса.");
        HttpURLConnection connection=(HttpURLConnection)new URL(ORIGIN+path).openConnection();
        connection.setConnectTimeout(30000);connection.setReadTimeout(130000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        try{
            if(data!=null){
                connection.setRequestMethod("POST");connection.setRequestProperty("Origin",ORIGIN);connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
                byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=connection.getOutputStream()){out.write(body);}
            }
            int status=connection.getResponseCode();
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
