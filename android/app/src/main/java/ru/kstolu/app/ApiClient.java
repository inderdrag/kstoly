package ru.kstolu.app;
import org.json.JSONObject;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;
final class ApiClient {
 static final String ORIGIN="https://oliva-photo-helper.onrender.com";
 static final class ApiError extends IOException {final int status;final boolean failedJob;ApiError(String m,int s,boolean f){super(m);status=s;failedJob=f;}}
 interface Transport {HttpURLConnection open(URL url)throws Exception;}
 private static Transport transport=url->(HttpURLConnection)url.openConnection();
 static void configureForTests(Transport t){transport=t;}static void restoreTransport(){transport=url->(HttpURLConnection)url.openConnection();}
 interface Progress {void stage(String stage);}
 static JSONObject call(String path,JSONObject data)throws Exception{return call(path,data,null);}
 static JSONObject call(String path,JSONObject data,Progress progress)throws Exception {
  if(!path.startsWith("/api/")||path.contains("..")||path.contains("\\"))throw new IOException("Неверный адрес запроса.");
  HttpURLConnection c=transport.open(new URL(ORIGIN+path));c.setConnectTimeout(30000);c.setReadTimeout(130000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");
  try{if(data!=null){c.setRequestMethod("POST");c.setRequestProperty("Origin",ORIGIN);c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);byte[] b=data.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);if(progress!=null)progress.stage("upload");try(OutputStream out=c.getOutputStream()){for(int offset=0;offset<b.length;offset+=32768)out.write(b,offset,Math.min(32768,b.length-offset));}if(progress!=null)progress.stage("waiting");}
   int status=c.getResponseCode();InputStream input=status>=400?c.getErrorStream():c.getInputStream();if(input==null)throw new IOException("Пустой ответ сервера.");byte[] bytes;try(input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n,total=0;while((n=input.read(b))!=-1){total+=n;if(total>10*1024*1024)throw new IOException("Ответ слишком большой.");out.write(b,0,n);}bytes=out.toByteArray();}
   if(progress!=null)progress.stage("validation");JSONObject result;try{result=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(Exception e){throw new IOException("Сервер запускается или вернул непонятный ответ. Повтор автоматически не отправлен.");}if(status<200||status>=300)throw new ApiError(result.optString("error","Ошибка сервера: "+status),status,result.optBoolean("imageJobFailed"));return result;
  }finally{c.disconnect();}
 }
}
