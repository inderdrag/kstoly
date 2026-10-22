package ru.kstolu.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only the encrypted server session, never the owner's password. */
final class SessionStore {
    interface Protector { String encrypt(String plaintext)throws Exception; String decrypt(String ciphertext)throws Exception; }
    private final SharedPreferences prefs;
    private final Protector protector;
    SessionStore(Context context){this(context.getSharedPreferences("session-v1",Context.MODE_PRIVATE),new AndroidProtector());}
    SessionStore(SharedPreferences prefs,Protector protector){this.prefs=prefs;this.protector=protector;}
    String load(){
        String encrypted=prefs.getString("encrypted",null);if(encrypted==null)return "";
        try{return protector.decrypt(encrypted);}catch(Exception e){clear();return "";}
    }
    void save(String value)throws Exception{
        String encrypted=protector.encrypt(value);
        if(!prefs.edit().clear().putString("encrypted",encrypted).commit())throw new java.io.IOException("Не удалось сохранить вход на устройстве.");
    }
    void clear(){prefs.edit().clear().commit();}
    private static final class AndroidProtector implements Protector {
        private static final String ALIAS="k-stolu-session-aes-v1";
        private SecretKey key()throws Exception{
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            return generator.generateKey();
        }
        public String encrypt(String plaintext)throws Exception{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] data=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP);
        }
        public String decrypt(String ciphertext)throws Exception{
            String[] parts=ciphertext.split(":",-1);if(parts.length!=2)throw new java.io.IOException("Повреждённая сессия.");
            byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);if(iv.length!=12)throw new java.io.IOException("Неверный формат сессии.");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }
    }
}
