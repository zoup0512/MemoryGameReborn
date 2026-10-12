package com.mopub.volley;

/**
 * Local constant-only replacement for the Volley retry policy class. The
 * decompiled game logic borrowed DEFAULT_BACKOFF_MULT (=1.0f) as a plain
 * scale factor; no HTTP functionality remains.
 */
public class DefaultRetryPolicy {
    public static final float DEFAULT_BACKOFF_MULT = 1.0f;

    public DefaultRetryPolicy() {
    }
}
