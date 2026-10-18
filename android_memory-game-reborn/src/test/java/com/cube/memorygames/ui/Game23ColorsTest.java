package com.cube.memorygames.ui;

import com.cube.memorygames.logic.GameRandom;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class Game23ColorsTest {
    @Test public void paletteHoldsTheTenOpaqueSchulteColors() {
        Set<Integer> expected = new HashSet<>(Arrays.asList(
                0xffe91e63, 0xff9c27b0, 0xff3f51b5, 0xff2196f3, 0xff00bcd4,
                0xff4caf50, 0xffffeb3b, 0xffff9800, 0xffff5722, 0xff795548));
        int[] palette = Game23Colors.shuffledPalette();
        Set<Integer> actual = new HashSet<>();
        for (int color : palette) {
            assertEquals(0xff000000, color & 0xff000000);
            actual.add(color);
        }
        assertEquals(10, palette.length);
        assertEquals(expected, actual);
    }

    @Test public void shuffledPaletteIsReproducibleAndVariesBySeed() {
        GameRandom.init(12345L);
        int[] colors = Game23Colors.shuffledPalette();
        GameRandom.init(12345L);
        assertArrayEquals(colors, Game23Colors.shuffledPalette());
        GameRandom.init(54321L);
        assertFalse(Arrays.equals(colors, Game23Colors.shuffledPalette()));
    }

    @Test public void numbersCycleThroughTheShuffledPaletteByNumber() {
        int[] palette = Game23Colors.shuffledPalette();
        for (int number = 1; number <= 48; number++) {
            assertEquals(palette[(number - 1) % palette.length],
                    Game23Colors.colorForNumber(number, palette));
        }
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
