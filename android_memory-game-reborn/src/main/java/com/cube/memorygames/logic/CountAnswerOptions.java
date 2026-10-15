package com.cube.memorygames.logic;

/** Four positive, distinct count choices, with the correct choice in any slot. */
public final class CountAnswerOptions {
    private CountAnswerOptions() { }

    public static int[] generate(int rightAnswer) {
        if (rightAnswer < 1) {
            throw new IllegalArgumentException("rightAnswer must be positive");
        }
        int position = GameRandom.nextInt(4);
        // Clamp the whole four-number window, including at Integer.MAX_VALUE.
        int base = Math.max(1, Math.min(rightAnswer - position, Integer.MAX_VALUE - 3));
        int[] answers = new int[4];
        for (int i = 0; i < answers.length; i++) {
            answers[i] = base + i;
        }
        // Clamping must not pin small (or very large) answers to an edge slot.
        int originalPosition = rightAnswer - base;
        answers[originalPosition] = answers[position];
        answers[position] = rightAnswer;
        return answers;
    }
}
