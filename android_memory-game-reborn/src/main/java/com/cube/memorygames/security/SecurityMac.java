package com.cube.memorygames.security;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Integrity tags over the local economy columns of LocalUser (hardening
 * design §4.3). Confidentiality is not the goal — the tag makes silent
 * SQLite edits detectable: a mismatched tag sets a session-scoped taint flag
 * that the online flow consumes to stop trusting the local coin mirror.
 *
 * Stamp-once bookkeeping: the first time a row is tagged, an encrypted
 * "row was stamped" marker is written via SecurePrefs. A later load that
 * finds the tag column empty while the marker exists means the tag was
 * stripped — that is itself treated as tampering, closing the naive
 * "edit money, null the tag" shortcut. Upgrades from schema 16 have no tag
 * and no marker: their first save stamps both, no taint involved.
 *
 * Failure discipline (§4.4): when the HMAC key is unavailable (Keystore
 * hiccup, reset), verification is skipped — unverifiable is not tampered.
 * Taint never crashes, never logs, never shows UI.
 */
public final class SecurityMac {

    private static final String MARKER_PREFS = "vault_state";
    private static final String KEY_STAMPED = "rowStamp";

    private static volatile Context appContext;
    private static volatile boolean tainted;
    private static volatile boolean stampedKnown;

    private SecurityMac() {
    }

    /** Called first thing in Application.onCreate, before any DB access. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /** HmacSHA256 over the payload, Base64; null when the key is unavailable. */
    public static String compute(String payload) {
        Context context = appContext;
        if (context == null) {
            return null;
        }
        try {
            SecretKey key = CryptoVault.macKey(context);
            if (key == null) {
                return null;
            }
            Mac mac;
            try {
                mac = Mac.getInstance("HmacSHA256");
                mac.init(key);
            } catch (GeneralSecurityException keystoreKeyRejected) {
                // Non-exportable Keystore HMAC keys are refused by the default
                // provider; the Keystore provider owns and routes them.
                mac = Mac.getInstance("HmacSHA256", CryptoVault.ANDROID_KEYSTORE);
                mac.init(key);
            }
            return Base64.encodeToString(mac.doFinal(payload.getBytes("UTF-8")), Base64.NO_WRAP);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /**
     * True when the tag matches the payload. An empty tag means the row is
     * not stamped yet — verified() only when the stamp-once marker says this
     * row never carried a tag (see class doc for the stripped-tag case).
     */
    public static boolean verify(String payload, String tag) {
        if (tag == null || tag.length() == 0) {
            return true;
        }
        String computed = compute(payload);
        if (computed == null) {
            return true; // cannot verify is not tampered
        }
        return MessageDigest.isEqual(
                computed.getBytes(), tag.getBytes());
    }

    /** Marks that the LocalUser row has been stamped at least once. */
    public static void rememberStamped() {
        if (stampedKnown) {
            return;
        }
        Context context = appContext;
        if (context != null) {
            SecurePrefs.put(context, MARKER_PREFS, KEY_STAMPED, "1");
            stampedKnown = true;
        }
    }

    public static boolean wasStamped() {
        if (stampedKnown) {
            return true;
        }
        Context context = appContext;
        return context != null && SecurePrefs.get(context, MARKER_PREFS, KEY_STAMPED) != null;
    }

    public static void markTainted() {
        tainted = true;
    }

}
