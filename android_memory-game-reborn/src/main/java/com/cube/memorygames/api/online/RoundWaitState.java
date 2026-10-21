package com.cube.memorygames.api.online;

final class RoundWaitState {
    enum Phase { CONFIRMING, COUNTDOWN, WAITING, SETTLING }

    final Phase phase;
    final long seconds;

    private RoundWaitState(Phase phase, long seconds) {
        this.phase = phase;
        this.seconds = seconds;
    }

    static RoundWaitState duel(OnlineMessages.RoundEnd end, int totalRounds,
            boolean hasCountdown, long remainingMs) {
        if (end == null) return new RoundWaitState(Phase.CONFIRMING, 0);
        if (totalRounds > 0 && end.round >= totalRounds) return new RoundWaitState(Phase.SETTLING, 0);
        return waiting(hasCountdown ? remainingMs : 0);
    }

    static RoundWaitState party(long deadlineAt, long serverNow) {
        return waiting(deadlineAt > 0 ? deadlineAt - serverNow : 0);
    }

    static String nextPartyGameId(PartyState state) {
        if (state == null || state.rounds == null || state.roundIndex < 0
                || state.roundIndex + 1 >= state.rounds.length
                || state.roundIndex + 1 >= state.roundCount) return null;
        PartyState.Round next = state.rounds[state.roundIndex + 1];
        return next == null ? null : next.gameId;
    }

    private static RoundWaitState waiting(long remainingMs) {
        long seconds = RoundIntermission.secondsRemaining(remainingMs);
        return new RoundWaitState(seconds > 0 ? Phase.COUNTDOWN : Phase.WAITING, seconds);
    }
}
