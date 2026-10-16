package com.cube.memorygames.ui;

import com.cube.memorygames.logic.GameRandom;
import org.junit.Test;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class Game23ColorsTest {
    @Test public void eachColorUsesThreeIndependentFullRangeChannelsAndOpaqueAlpha() {
        GameRandom.init(20261016L);
        Random expected = new Random(20261016L);
        Set<Integer> colors = new HashSet<>();
        Set<Integer> reds = new HashSet<>();
        Set<Integer> greens = new HashSet<>();
        Set<Integer> blues = new HashSet<>();
        for (int i = 0; i < 4096; i++) {
            int color = Game23Colors.nextColor();
            int red = expected.nextInt(256);
            int green = expected.nextInt(256);
            int blue = expected.nextInt(256);
            assertEquals(0xff000000 | (red << 16) | (green << 8) | blue, color);
            colors.add(color);
            reds.add(red);
            greens.add(green);
            blues.add(blue);
        }
        assertTrue(colors.size() > 4000);
        assertEquals(256, reds.size());
        assertEquals(256, greens.size());
        assertEquals(256, blues.size());
    }

    @Test public void sameRoundSeedReproducesAll48Colors() {
        int[] colors = new int[48];
        GameRandom.init(12345L);
        for (int i = 0; i < colors.length; i++) colors[i] = Game23Colors.nextColor();
        GameRandom.init(12345L);
        for (int color : colors) assertEquals(color, Game23Colors.nextColor());
        GameRandom.init(54321L);
        assertNotEquals(colors[0], Game23Colors.nextColor());
    }

    @Test public void digitsUseContrastingBlackOrWhiteOnLightAndDarkCells() {
        assertEquals(0xff000000, Game23Colors.textColor(0xffffffff));
        assertEquals(0xffffffff, Game23Colors.textColor(0xff000000));
        assertEquals(0xff000000, Game23Colors.textColor(0xffffff00));
        assertEquals(0xff000000, Game23Colors.textColor(0xff00ff00));
        assertEquals(0xffffffff, Game23Colors.textColor(0xff0000ff));
    }

    @Test public void textContrastMeetsFourPointFiveAcrossRgbSample() {
        for (int red = 0; red < 256; red += 17) {
            for (int green = 0; green < 256; green += 17) {
                for (int blue = 0; blue < 256; blue += 17) {
                    int color = 0xff000000 | (red << 16) | (green << 8) | blue;
                    double luminance = 0.2126d * linear(red) + 0.7152d * linear(green) + 0.0722d * linear(blue);
                    double contrast = Game23Colors.textColor(color) == 0xff000000
                            ? (luminance + 0.05d) / 0.05d : 1.05d / (luminance + 0.05d);
                    assertTrue("contrast=" + contrast, contrast >= 4.5d);
                }
            }
        }
    }

    private static double linear(int channel) {
        double value = channel / 255.0d;
        return value <= 0.04045d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }
}
