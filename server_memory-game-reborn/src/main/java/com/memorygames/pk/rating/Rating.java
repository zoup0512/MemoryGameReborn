package com.memorygames.pk.rating;

import com.memorygames.pk.config.PkProperties;
import org.springframework.stereotype.Component;

/** ELO rating maths and rank names (PRD 3.8). */
@Component
public class Rating {

    private static final String[] RANKS = {"Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master"};
    private static final int[] RANK_FLOORS = {0, 1100, 1300, 1500, 1700, 1900};

    private final PkProperties properties;

    public Rating(PkProperties properties) {
        this.properties = properties;
    }

    /** Standard ELO delta for the player with rating a against b; s in {0,0.5,1}. */
    public int delta(int ratingA, int ratingB, double s) {
        return delta(ratingA, ratingB, s, this.properties.getRating().getKFactor());
    }

    /** Same maths with an explicit K factor (per-match terms snapshot). */
    public int delta(int ratingA, int ratingB, double s, int k) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
        return (int) Math.round(k * (s - expected));
    }

    public int rankIndex(int elo) {
        int rank = 0;
        for (int i = RANK_FLOORS.length - 1; i >= 1; i--) {
            if (elo >= RANK_FLOORS[i]) {
                rank = i;
                break;
            }
        }
        return rank;
    }

    public static String rankName(int index) {
        return index >= 0 && index < RANKS.length ? RANKS[index] : RANKS[0];
    }
}
