package com.cube.memorygames.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Single entry point for the app's symmetric keys (hardening design §4.1).
 *
 * API 23+: two non-exportable keys live in AndroidKeyStore — an AES-256 GCM
 * key for {@link SecurePrefs} and an HmacSHA256 key for the LocalUser
 * integrity tag. On API 28+ StrongBox is attempted for the AES key and the
 * plain TEE path is the fallback.
 *
 * API 21/22: AndroidKeyStore cannot hold symmetric keys on these levels, so
 * both keys are random software keys wrapped with a Keystore-resident RSA
 * key pair and stored in preferences. Weaker (root can extract the RSA
 * private key and unwrap), documented best-effort for the 5.x tail.
 *
 * Failure discipline (§4.4): every path degrades silently. If key material
 * cannot be established at all, {@code available()} turns false and callers
 * fall back to unencrypted behavior — no crash, no log, no user-facing hint.
 * A Keystore reset invalidates everything; the consequence is re-registered
 * guest identity, never a blocked app.
 */
final class CryptoVault {

    /** Provider name, also used by SecurityMac to route Mac operations. */
    static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS_AES = "mgsVaultA1";
    private static final String ALIAS_MAC = "mgsVaultM1";
    private static final String ALIAS_RSA = "mgsVaultR1";
    private static final String WRAP_PREFS = "vault_state";
    private static final String KEY_WRAPPED_AES = "s0";
    private static final String KEY_WRAPPED_MAC = "s1";

    private static final Object LOCK = new Object();
    private static volatile SecretKey aesKey;
    private static volatile SecretKey macKey;
    private static volatile boolean broken;

    private CryptoVault() {
    }

    /** The AES-GCM key, or null when no key could be established. */
    static SecretKey aesKey(Context context) {
        if (broken) {
            return null;
        }
        if (aesKey == null) {
            synchronized (LOCK) {
                if (aesKey == null) {
                    aesKey = establish(context.getApplicationContext(), false);
                }
            }
        }
        return aesKey;
    }

    /** The HmacSHA256 key, or null when no key could be established. */
    static SecretKey macKey(Context context) {
        if (broken) {
            return null;
        }
        if (macKey == null) {
            synchronized (LOCK) {
                if (macKey == null) {
                    macKey = establish(context.getApplicationContext(), true);
                }
            }
        }
        return macKey;
    }

    /** True when symmetric crypto is usable for new writes. */
    static boolean available(Context context) {
        return aesKey(context) != null && macKey(context) != null;
    }

    private static SecretKey establish(Context context, boolean mac) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                return Api23.keystoreKey(mac ? ALIAS_MAC : ALIAS_AES, mac);
            } catch (Throwable first) {
                try {
                    Api23.deleteAlias(mac ? ALIAS_MAC : ALIAS_AES);
                    return Api23.keystoreKey(mac ? ALIAS_MAC : ALIAS_AES, mac);
                } catch (Throwable second) {
                    // fall through to the wrapped fallback below
                }
            }
        }
        try {
            return wrappedKey(context, mac);
        } catch (Throwable first) {
            try {
                wipeWrappedState(context);
                return wrappedKey(context, mac);
            } catch (Throwable second) {
                broken = true;
                return null;
            }
        }
    }

    /**
     * API 21/22 fallback: random 32-byte software key, RSA-wrapped by a
     * Keystore key pair, stored Base64 in preferences. Regenerating after a
     * wipe means previously sealed values become unreadable — accepted.
     */
    private static SecretKey wrappedKey(Context context, boolean mac) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(WRAP_PREFS, Context.MODE_PRIVATE);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(ALIAS_RSA, null);
        if (entry == null) {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 50);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(ALIAS_RSA)
                    .setSubject(new javax.security.auth.x500.X500Principal("CN=mgsVault"))
                    .setSerialNumber(BigInteger.valueOf(701991L))
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE);
            generator.initialize(spec);
            generator.generateKeyPair();
            entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(ALIAS_RSA, null);
            if (entry == null) {
                throw new IllegalStateException("RSA wrap key not retrievable");
            }
            // fresh wrap key: any blobs from a previous vault are unreadable
            prefs.edit().clear().apply();
        }

        String stored = prefs.getString(mac ? KEY_WRAPPED_MAC : KEY_WRAPPED_AES, null);
        if (stored != null) {
            try {
                Cipher unwrap = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                unwrap.init(Cipher.UNWRAP_MODE, entry.getPrivateKey());
                return (SecretKey) unwrap.unwrap(Base64.decode(stored, Base64.DEFAULT),
                        mac ? "HmacSHA256" : "AES", Cipher.SECRET_KEY);
            } catch (Throwable unreadable) {
                // blob stale or corrupt — regenerate below
            }
        }
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        SecretKey key = new SecretKeySpec(raw, mac ? "HmacSHA256" : "AES");
        Cipher wrap = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        wrap.init(Cipher.WRAP_MODE, entry.getCertificate().getPublicKey());
        String wrapped = Base64.encodeToString(wrap.wrap(key), Base64.NO_WRAP);
        prefs.edit().putString(mac ? KEY_WRAPPED_MAC : KEY_WRAPPED_AES, wrapped).apply();
        return key;
    }

    private static void wipeWrappedState(Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            keyStore.deleteEntry(ALIAS_RSA);
        } catch (Throwable ignored) {
        }
        context.getSharedPreferences(WRAP_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** API 23+ key material. Isolated so API 21/22 devices never verify it. */
    private static final class Api23 {
        private static SecretKey keystoreKey(String alias, boolean hmac) throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            KeyStore.Entry entry = keyStore.getEntry(alias, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            if (hmac) {
                return generateHmac(alias);
            }
            // AES: prefer StrongBox on 28+ when present, TEE otherwise
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    return generateAes(alias, true);
                } catch (Throwable noStrongBox) {
                    // fall through to the plain TEE path
                }
            }
            return generateAes(alias, false);
        }

        private static SecretKey generateAes(String alias, boolean strongBox) throws Exception {
            KeyGenerator generator = KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            android.security.keystore.KeyGenParameterSpec.Builder builder =
                    new android.security.keystore.KeyGenParameterSpec.Builder(alias,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                                    | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(
                                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setRandomizedEncryptionRequired(true);
            if (strongBox) {
                Api28.strongBox(builder);
            }
            generator.init(builder.build());
            return generator.generateKey();
        }

        private static SecretKey generateHmac(String alias) throws Exception {
            KeyGenerator generator = KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    ANDROID_KEYSTORE);
            generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(alias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN
                            | android.security.keystore.KeyProperties.PURPOSE_VERIFY).build());
            return generator.generateKey();
        }

        private static void deleteAlias(String alias) {
            try {
                KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
                keyStore.load(null);
                keyStore.deleteEntry(alias);
            } catch (Throwable ignored) {
            }
        }
    }

    /** API 28+ StrongBox opt-in. Isolated so older devices never verify it. */
    private static final class Api28 {
        private static void strongBox(android.security.keystore.KeyGenParameterSpec.Builder builder) {
            builder.setIsStrongBoxBacked(true);
        }
    }
}
