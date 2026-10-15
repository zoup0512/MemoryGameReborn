import com.cube.memorygames.logic.CountAnswerOptions;
import com.cube.memorygames.logic.GameRandom;
import java.util.Arrays;
import java.util.Random;

public final class CountAnswerOptionsTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static int verify(int rightAnswer, int[] answers) {
        check(answers.length == 4, "four options");
        int correctPosition = -1;
        for (int i = 0; i < answers.length; i++) {
            check(answers[i] > 0, "positive option for " + rightAnswer);
            for (int j = 0; j < i; j++) {
                check(answers[i] != answers[j], "distinct options for " + rightAnswer);
            }
            if (answers[i] == rightAnswer) correctPosition = i;
        }
        check(correctPosition >= 0, "contains correct value " + rightAnswer);
        return correctPosition;
    }

    public static void main(String[] args) {
        int[] boundaries = {1, 2, 3, 4, 5, 10, 100, 1000000,
                Integer.MAX_VALUE - 3, Integer.MAX_VALUE - 2,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (int rightAnswer : boundaries) {
            GameRandom.init(20261015L);
            int positions = 0;
            for (int sample = 0; sample < 512; sample++) {
                positions |= 1 << verify(rightAnswer, CountAnswerOptions.generate(rightAnswer));
            }
            check(positions == 15, "all four positions reachable for " + rightAnswer);
        }

        GameRandom.init(99L);
        for (int value = 1; value <= 10000; value++) {
            verify(value, CountAnswerOptions.generate(value));
        }
        Random values = new Random(42L);
        for (int sample = 0; sample < 10000; sample++) {
            int value = 1 + values.nextInt(Integer.MAX_VALUE);
            verify(value, CountAnswerOptions.generate(value));
        }

        // Exactly one seeded GameRandom.nextInt(4), including the clamped edges.
        for (int value : boundaries) {
            GameRandom.init(123456L);
            Random expected = new Random(123456L);
            for (int sample = 0; sample < 64; sample++) {
                int expectedPosition = expected.nextInt(4);
                check(verify(value, CountAnswerOptions.generate(value)) == expectedPosition,
                        "seeded position for " + value);
            }
            check(GameRandom.nextInt(1000) == expected.nextInt(1000), "one random draw per generation");
        }

        GameRandom.init(7L);
        int[][] first = new int[boundaries.length][];
        for (int i = 0; i < boundaries.length; i++) first[i] = CountAnswerOptions.generate(boundaries[i]);
        GameRandom.reset();
        for (int i = 0; i < boundaries.length; i++) {
            check(Arrays.equals(first[i], CountAnswerOptions.generate(boundaries[i])), "seed replay");
        }
        for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
            boolean rejected = false;
            try {
                CountAnswerOptions.generate(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "reject non-positive correct answer " + invalid);
        }
        System.out.println("PASS count answer options: " + checks + " assertions");
    }
}
