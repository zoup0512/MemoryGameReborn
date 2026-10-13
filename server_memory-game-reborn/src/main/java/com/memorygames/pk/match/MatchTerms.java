package com.memorygames.pk.match;

import com.memorygames.pk.config.PkProperties;

/**
 * Immutable per-match economy and rating terms (P2-1), captured from the
 * live configuration the moment a room is created. Entry-fee charges,
 * refunds, voids and payouts all read this snapshot, so hot-editing the
 * admin config mid-match can no longer change what a running match charges
 * or pays out. Bump {@code TERMS_VERSION} when the shape evolves.
 */
public record MatchTerms(
        int entryFee,
        int winCoins,
        int drawCoins,
        int kFactor,
        /** Practice tier payout for the seat's difficulty (0 outside practice). */
        int practiceTierWinCoins,
        int practiceDailyCap,
        int termsVersion) {

    public static final int TERMS_VERSION = 1;

    /** Terms for RANKED / FRIEND matches. */
    public static MatchTerms forVersus(PkProperties properties) {
        return new MatchTerms(
                properties.getEconomy().getEntryFee(),
                properties.getEconomy().getWinCoins(),
                properties.getEconomy().getDrawCoins(),
                properties.getRating().getKFactor(),
                0,
                0,
                TERMS_VERSION);
    }

    /** Terms for a PRACTICE duel against the given difficulty tier. */
    public static MatchTerms forPractice(PkProperties properties, PkProperties.BotDifficulty tier) {
        return new MatchTerms(
                0, // practice charges no entry fee
                0, // payout comes from the tier
                0,
                0, // practice moves no rating
                tier != null ? tier.getWinCoins() : 0,
                properties.getPractice().getDailyRewardCap(),
                TERMS_VERSION);
    }
}
