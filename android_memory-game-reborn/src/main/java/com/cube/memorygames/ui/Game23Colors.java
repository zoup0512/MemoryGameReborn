package com.cube.memorygames.ui;

import com.cube.memorygames.logic.GameRandom;

public final class Game23Colors {
    // Matches the HarmonyOS SchultzTablesPage palette: ten fixed Material colors,
    // shuffled once per round and assigned by number.
    private static final int[] CELL_COLORS = {
            0xffe91e63, 0xff9c27b0, 0xff3f51b5, 0xff2196f3, 0xff00bcd4,
            0xff4caf50, 0xffffeb3b, 0xffff9800, 0xffff5722, 0xff795548
    };

    private Game23Colors() {
    }

    public static int[] shuffledPalette() {
        int[] palette = CELL_COLORS.clone();
        for (int i = palette.length - 1; i > 0; i--) {
            int j = GameRandom.nextInt(i + 1);
            int color = palette[i];
            palette[i] = palette[j];
            palette[j] = color;
        }
        return palette;
    }

    public static int colorForNumber(int number, int[] palette) {
        return palette[(number - 1) % palette.length];
    }

    public static int textColor(int background) {
        double luminance = 0.2126d * linearChannel((background >>> 16) & 0xff)
                + 0.7152d * linearChannel((background >>> 8) & 0xff)
                + 0.0722d * linearChannel(background & 0xff);
        return luminance > 0.179d ? 0xff000000 : 0xffffffff;
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0d;
        return value <= 0.04045d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }
}
