package com.cube.memorygames.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Debug;
import com.memory.brain.training.games.BuildConfig;
import java.security.MessageDigest;

/**
 * Runtime self-protection (hardening design §5): repackaging and dynamic
 * analysis signals, consumed at scattered points with silent degradation.
 *
 * Signature check: the SHA-256 of the signing certificate is compared
 * against the XOR-split anchor. The debug exemption is keyed on the
 * compile-time {@link BuildConfig#DEBUG} flag, not on the runtime
 * ApplicationInfo.FLAG_DEBUGGABLE: a repackaged release APK can set
 * android:debuggable="true" in the manifest and re-sign without touching any
 * code, which would otherwise turn the whole check off. BuildConfig.DEBUG is
 * baked per variant, so release builds stay enforced.
 *
 * Consumption discipline (§5.1): never exit, never toast, never log — a
 * failed check only makes the consuming feature inert, so there is no
 * single patchable "kill switch" and no observable detection point.
 */
public final class IntegritySentinel {

    private static volatile Boolean intact;

    private IntegritySentinel() {
    }

    /**
     * True when the package still carries the anchored release signature.
     * Unanchored builds (no fingerprint stamped yet) and debuggable builds
     * report intact. Result is cached after the first evaluation.
     */
    public static boolean isIntact(Context context) {
        Boolean cached = intact;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean result = evaluate(context);
        intact = Boolean.valueOf(result);
        return result;
    }

    /**
     * Live-process signal for interactive entry points: an attached or
     * waiting debugger on a release build. Consumed together with
     * {@link #isIntact} by the ranked lobby; debug builds debugging
     * themselves are likewise gated — attaching a debugger means ranked
     * stays inert that session.
     */
    public static boolean runtimeClear(Context context) {
        return isIntact(context)
                && !Debug.isDebuggerConnected()
                && !Debug.waitingForDebugger();
    }

    private static boolean evaluate(Context context) {
        Context app = context.getApplicationContext();
        if (BuildConfig.DEBUG) {
            // Sideloaded debug builds sign with the debug key on purpose.
            // Compile-time constant: release builds never take this branch.
            return true;
        }
        byte[] anchor = IntegrityAnchor.fingerprint();
        if (anchor == null) {
            return true;
        }
        byte[] actual = signingCertificateSha256(app);
        return actual != null && MessageDigest.isEqual(anchor, actual);
    }

    /** SHA-256 over the DER signing certificate, or null when unavailable. */
    private static byte[] signingCertificateSha256(Context app) {
        try {
            PackageManager pm = app.getPackageManager();
            String pkg = app.getPackageName();
            Signature[] signatures = signaturesOf(pm, pkg);
            if (signatures == null || signatures.length == 0) {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(signatures[0].toByteArray());
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static Signature[] signaturesOf(PackageManager pm, String pkg) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signing = info.signingInfo;
            if (signing == null) {
                return null;
            }
            return signing.hasMultipleSigners()
                    ? signing.getApkContentsSigners()
                    : signing.getSigningCertificateHistory();
        }
        PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
        return info.signatures;
    }
}
