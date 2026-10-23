package com.cube.memorygames.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM sealed string storage on top of plain SharedPreferences
 * (hardening design §4.2). Values share the caller's preference file and key,
 * so legacy plaintext values migrate in place: {@link #seal} rewrites the
 * value under the same key once crypto is available.
 *
 * Wire format per value: Base64(0x01 || ivLen || IV || ciphertext+tag).
 * A stored value that does not parse as this format is treated as legacy
 * plaintext and returned as-is until sealed.
 *
 * IV handling: Keystore-backed keys are generated with
 * setRandomizedEncryptionRequired(true), which rejects caller-supplied IVs on
 * encryption — the provider generates the IV and it is read back via
 * getIV(). Software keys (the API 21/22 wrapped fallback) require an explicit
 * random IV instead. Both paths store the actual IV length.
 *
 * Failure discipline (§4.4): a blob that fails to decrypt — wrong key after a
 * Keystore reset, or manual tampering — reads back as null ("value absent"),
 * never an exception. When no key can be established at all, writes fall back
 * to plaintext so the app keeps working; those devices are simply unprotected.
 */
public final class SecurePrefs {

    private static final int VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    /** version byte + IV length byte */
    private static final int HEADER_LENGTH = 2;
    /** smallest well-formed blob: header + 1-byte IV + GCM tag */
    private static final int MIN_BLOB_LENGTH = HEADER_LENGTH + 1 + 16;
    /** Base64 length of the 30-byte smallest real blob (40 chars); legacy
     *  plaintext values we seal are all shorter than this. */
    private static final int MIN_BLOB_TEXT_LENGTH = 40;

    private static final Object LOCK = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurePrefs() {
    }

    /**
     * Reads a sealed (or not-yet-sealed legacy plaintext) value. Returns null
     * when absent or unreadable.
     */
    public static String get(Context context, String prefsName, String key) {
        synchronized (LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            String raw = readRaw(prefs, key);
            if (raw == null) {
                return null;
            }
            if (isBlob(raw)) {
                SecretKey aesKey = CryptoVault.aesKey(context);
                if (aesKey == null) {
                    // sealed value but no key: treat as absent
                    return null;
                }
                try {
                    return decrypt(aesKey, Base64.decode(raw, Base64.DEFAULT));
                } catch (Throwable unreadable) {
                    return null;
                }
            }
            return raw;
        }
    }

    /** Account credentials fail closed, retaining the original blob on key/decryption failure. */
    public static String getStrict(Context context, String prefsName, String key) {
        synchronized (LOCK) {
            SharedPreferences prefs=context.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
            String raw=readRaw(prefs,key);
            if (raw == null) return null;
            SecretKey aesKey=CryptoVault.aesKey(context);
            if (aesKey == null) throw new IllegalStateException("安全密钥不可用，原凭证保留");
            try {
                if (isBlob(raw)) return decrypt(aesKey,Base64.decode(raw,Base64.DEFAULT));
                putStrict(context,prefsName,key,raw); // Durable in-place migration of old plaintext.
                return raw;
            } catch (Exception e) { throw new IllegalStateException("安全凭证无法读取，原凭证保留，请勿清除数据",e); }
        }
    }

    /** New trust records have no plaintext predecessor; accepting one would bypass release endpoint pinning. */
    public static String getSealedStrict(Context context, String prefsName, String key) {
        synchronized (LOCK) {
            String raw=readRaw(context.getSharedPreferences(prefsName,Context.MODE_PRIVATE),key);
            if (raw != null && !isBlob(raw)) throw new IllegalStateException("服务器信任记录损坏，禁止连接");
            return getStrict(context,prefsName,key);
        }
    }

    public static void putStrict(Context context, String prefsName, String key, String value) {
        synchronized (LOCK) {
            SharedPreferences.Editor edit=context.getSharedPreferences(prefsName,Context.MODE_PRIVATE).edit();
            if (value == null) edit.remove(key);
            else {
                SecretKey aesKey=CryptoVault.aesKey(context);
                if (aesKey == null) throw new IllegalStateException("安全密钥不可用，禁止明文保存凭证");
                String blob=encrypt(aesKey,value);
                // Existing offline encrypt() retains its fallback; account callers explicitly reject it.
                if (blob.equals(value) || !isBlob(blob)) throw new IllegalStateException("凭证加密失败，原凭证保留");
                edit.putString(key,blob);
            }
            if (!edit.commit()) throw new IllegalStateException("安全凭证保存失败，原身份保留");
        }
    }

    /** Stores a value sealed, or as plaintext when crypto is unavailable. */
    public static void put(Context context, String prefsName, String key, String value) {
        synchronized (LOCK) {
            SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            if (value == null) {
                prefs.edit().remove(key).apply();
                return;
            }
            SecretKey aesKey = CryptoVault.aesKey(context);
            if (aesKey == null) {
                prefs.edit().putString(key, value).apply();
                return;
            }
            prefs.edit().putString(key, encrypt(aesKey, value)).apply();
        }
    }


    /**
     * Reads a value regardless of how a legacy install typed it: pre-hardening
     * code stored real booleans (e.g. the welcome-gift flag), and getString
     * throws ClassCastException on those. Typed values stringify on the way
     * out so callers keep a single string-based view.
     */
    private static String readRaw(SharedPreferences prefs, String key) {
        try {
            return prefs.getString(key, null);
        } catch (ClassCastException legacyTyped) {
            Object typed = prefs.getAll().get(key);
            return typed == null ? null : String.valueOf(typed);
        }
    }

    private static boolean isBlob(String raw) {
        if (raw.length() < MIN_BLOB_TEXT_LENGTH) {
            return false;
        }
        try {
            byte[] decoded = Base64.decode(raw, Base64.DEFAULT);
            if (decoded == null || decoded.length < MIN_BLOB_LENGTH || decoded[0] != VERSION) {
                return false;
            }
            int ivLength = decoded[1] & 0xFF;
            return ivLength >= 8 && ivLength <= 32
                    && decoded.length >= HEADER_LENGTH + ivLength + 16;
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private static String encrypt(SecretKey key, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = null;
            try {
                // Keystore path: the provider owns the IV (randomized
                // encryption required rejects caller-supplied IVs).
                cipher.init(Cipher.ENCRYPT_MODE, key);
                iv = cipher.getIV();
            } catch (Throwable noSystemIv) {
                iv = null;
            }
            if (iv == null || iv.length == 0) {
                // software key path: GCM needs an explicit random IV
                iv = new byte[IV_LENGTH];
                RANDOM.nextBytes(iv);
                cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            }
            byte[] cipherText = cipher.doFinal(value.getBytes("UTF-8"));
            byte[] blob = new byte[HEADER_LENGTH + iv.length + cipherText.length];
            blob[0] = (byte) VERSION;
            blob[1] = (byte) iv.length;
            System.arraycopy(iv, 0, blob, HEADER_LENGTH, iv.length);
            System.arraycopy(cipherText, 0, blob, HEADER_LENGTH + iv.length, cipherText.length);
            return Base64.encodeToString(blob, Base64.NO_WRAP);
        } catch (Throwable failed) {
            // never lose a write to a crypto hiccup: degrade to plaintext
            return value;
        }
    }

    private static String decrypt(SecretKey key, byte[] blob) throws Exception {
        int ivLength = blob[1] & 0xFF;
        if (ivLength <= 0 || blob.length < HEADER_LENGTH + ivLength + 16) {
            throw new IllegalArgumentException("malformed sealed blob");
        }
        byte[] iv = new byte[ivLength];
        System.arraycopy(blob, HEADER_LENGTH, iv, 0, ivLength);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = cipher.doFinal(blob, HEADER_LENGTH + ivLength,
                blob.length - HEADER_LENGTH - ivLength);
        return new String(plain, "UTF-8");
    }
}
