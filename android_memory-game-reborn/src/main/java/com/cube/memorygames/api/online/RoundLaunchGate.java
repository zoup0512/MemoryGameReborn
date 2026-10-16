package com.cube.memorygames.api.online;

/** Activity-local preparation gate; Android callbacks use the original frame as a token. */
final class RoundLaunchGate {
    enum Action { IGNORE, PREPARE, READY, START }

    private OnlineMessages.RoundStart token;
    private OnlineMessages.RoundStart formal;
    private boolean prepared;
    private boolean launched;

    Action accept(OnlineMessages.RoundStart frame) {
        return accept(frame, true);
    }

    Action accept(OnlineMessages.RoundStart frame, boolean resumeWithoutPreparation) {
        if (frame == null || frame.matchId == null || frame.round < 1) return Action.IGNORE;
        boolean same = token != null && token.matchId.equals(frame.matchId) && token.round == frame.round;
        if (!same) {
            if (token != null && token.matchId.equals(frame.matchId) && frame.round < token.round) {
                return Action.IGNORE;
            }
            clear();
            token = frame;
            if (!frame.preparing) formal = frame;
            if (resumeWithoutPreparation && frame.resuming && !frame.preparing) {
                prepared = true;
                return Action.START;
            }
            return Action.PREPARE;
        }
        if (launched) return Action.IGNORE;
        if (frame.preparing) {
            return prepared && formal == null ? Action.READY : Action.IGNORE;
        }
        formal = frame;
        return prepared ? Action.START : Action.IGNORE;
    }

    boolean canComplete(OnlineMessages.RoundStart frame) {
        return frame != null && frame == token && !prepared && !launched;
    }

    Action complete(OnlineMessages.RoundStart frame) {
        if (!canComplete(frame)) return Action.IGNORE;
        prepared = true;
        return formal == null ? Action.READY : Action.START;
    }

    OnlineMessages.RoundStart formalFrame() { return formal; }
    OnlineMessages.RoundStart token() { return token; }

    boolean markLaunched(OnlineMessages.RoundStart frame) {
        if (frame == null || frame != formal || !prepared || launched) return false;
        launched = true;
        return true;
    }

    void clear() {
        token = null;
        formal = null;
        prepared = false;
        launched = false;
    }
}
