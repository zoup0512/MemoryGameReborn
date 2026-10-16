package com.cube.memorygames.ui;

import com.cube.memorygames.logic.GameRandom;

public final class Game23Colors {
    private Game23Colors() {
    }

    public static int nextColor() {
        return 0xff000000 | (GameRandom.nextInt(256) << 16)
                | (GameRandom.nextInt(256) << 8) | GameRandom.nextInt(256);
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
