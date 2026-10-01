package com.yalantis.ucrop.util;

/**
 * Local constant-only replacement. The decompiled sources of the vendored
 * libraries (activeandroid, MPAndroidChart, ...) reference a shared
 * "." string constant that the original compiler folded into the uCrop
 * utility class; only that constant is needed by the offline build.
 */
public final class FileUtils {
    public static final String HIDDEN_PREFIX = ".";

    private FileUtils() {
    }
}
