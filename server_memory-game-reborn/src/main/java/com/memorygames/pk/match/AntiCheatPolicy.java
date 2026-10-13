package com.memorygames.pk.match;

import com.memorygames.pk.config.PkProperties;

/**
 * Pure anti-cheat v2 decision (PRD 4.5): a human answering faster than
 * floor*speedFactor across a round is a bot/automation signature; strikes
 * accumulate and eventually restrict the account to bot opponents. Extracted
 * from MatchRoom so the thresholds are unit-testable without a room (P2-8).
 */
public final class AntiCheatPolicy {

    private AntiCheatPolicy() {
    }

    /**
     * @param answers           levels this seat reported in the final round
     * @param elapsedMs         final-round duration at settle time
     * @param floorMs           per-game minimum human answer interval
     * @param suspicion         strikes already on the account
     * @param restrictedMatches remaining bot-only matches (0 = unrestricted)
     * @return the flag outcome to apply inside the settlement transaction,
     *         or null when there is nothing to change
     */
    public static SettlementService.FlagUpdate evaluate(int answers, long elapsedMs, long floorMs,
                                                        int suspicion, int restrictedMatches,
                                                        PkProperties.AntiCheat cfg) {
        if (floorMs <= 0 || elapsedMs <= 0 || answers < cfg.getMinAnswersForCheck()) {
            return null;
        }
        SettlementService.FlagUpdate flag = new SettlementService.FlagUpdate();
        if (restrictedMatches > 0) {
            flag.decrementRestriction = true;
        }
        double avgInterval = (double) elapsedMs / answers;
        if (avgInterval < floorMs * cfg.getSpeedFactor()) {
            flag.strike = true;
            flag.suspicionAfter = suspicion + 1;
            if (flag.suspicionAfter >= cfg.getSuspicionThreshold()) {
                flag.restrictionAfter = cfg.getRestrictionMatches();
            }
        }
        return flag.strike || flag.decrementRestriction ? flag : null;
    }
}
