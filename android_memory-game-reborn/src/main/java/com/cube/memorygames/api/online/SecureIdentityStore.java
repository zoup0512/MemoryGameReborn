package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/** API 21 compatible: Keystore RSA wraps a random AES-256 key; GCM protects the identity. */
final class SecureIdentityStore {
    private final String ALIAS;
    private final Context context;
    private final SharedPreferences prefs;
    SecureIdentityStore(Context context) { this(context, "online_secure_identity"); }
    SecureIdentityStore(Context context, String name) {
        this.context = context.getApplicationContext();
        ALIAS = "memorygames." + name;
        prefs = this.context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
    boolean exists() { return prefs.contains("identity"); }
    private KeyStore keys() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            // Never replace a lost key when ciphertext exists: that would silently create another account.
            if (prefs.contains("identity")) throw new IllegalStateException("Identity key unavailable");
            Calendar start = Calendar.getInstance(), end = Calendar.getInstance();
            end.add(Calendar.YEAR, 50);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS)
                    .setSubject(new X500Principal("CN=MemoryGames identity"))
                    .setSerialNumber(BigInteger.ONE).setStartDate(start.getTime()).setEndDate(end.getTime())
                    .setKeySize(2048).build());
            generator.generateKeyPair();
        }
        return store;
    }
    String read() throws Exception {
        String blob = prefs.getString("identity", null);
        if (blob == null) return null;
        String[] parts = blob.split(":", -1);
        if (parts.length != 3) throw new IllegalStateException("Invalid secure identity");
        KeyStore store = keys();
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, store.getKey(ALIAS, null));
        byte[] key = rsa.doFinal(decode(parts[0]));
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, decode(parts[1])));
        return new String(aes.doFinal(decode(parts[2])), "UTF-8");
    }
    void write(String json) throws Exception {
        KeyStore store = keys();
        byte[] key = new byte[32], iv = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(key); random.nextBytes(iv);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = aes.doFinal(json.getBytes("UTF-8"));
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, store.getCertificate(ALIAS).getPublicKey());
        String blob = encode(rsa.doFinal(key)) + ":" + encode(iv) + ":" + encode(encrypted);
        if (!prefs.edit().putString("identity", blob).commit()) throw new IllegalStateException("Secure storage failed");
    }
    static String randomSecret() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    private static String encode(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.NO_WRAP); }
}
