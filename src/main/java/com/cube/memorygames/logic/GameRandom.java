package com.cube.memorygames.logic;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameRandom {
    private static long currentSeed = System.currentTimeMillis();
    private static Random random = new Random(currentSeed);

    private GameRandom() {
    }

    public static void reset() {
        random = new Random(currentSeed);
    }

    public static void init() {
        init(System.currentTimeMillis());
    }

    public static void init(long newSeed) {
        currentSeed = newSeed;
        reset();
    }

    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public static double nextDouble() {
        return random.nextDouble();
    }

    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    public static void shuffle(List<?> list) {
        Collections.shuffle(list, random);
    }
}
