package com.cube.memorygames;

/**
 * The original dialog offered coins through Google Play in-app purchases,
 * a rewarded ad video and a Facebook share. All three backends are removed
 * from this offline build, so the dialog itself is gone; the class survives
 * only as the holder of the interfaces and constants that the untouched
 * game logic still references.
 */
public final class SharingDialog {
    public static final int DOLLAR1_COINS = 700;
    public static final int DOLLAR2_COINS = 1600;
    public static final int DOLLAR3_COINS = 2500;
    public static final String SKU1 = "1dollars";
    public static final String SKU2 = "2dollars";
    public static final String SKU3 = "3dollars";

    private SharingDialog() {
    }

    public interface StatisticListener {
        void onStatisticUpdated();
    }

    public interface IabStatus {
        boolean isIabSetupFinished();
    }
}
