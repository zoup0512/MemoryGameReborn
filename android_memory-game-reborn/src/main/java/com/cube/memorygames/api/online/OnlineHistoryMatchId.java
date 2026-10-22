package com.cube.memorygames.api.online;

/** Shared display/copy policy for identifiers in online match history. */
final class OnlineHistoryMatchId {
    private OnlineHistoryMatchId() {
    }

    static String copyableId(String matchId) {
        return matchId == null || matchId.trim().isEmpty() ? null : matchId;
    }
}
