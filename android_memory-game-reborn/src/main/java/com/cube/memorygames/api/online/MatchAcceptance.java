package com.cube.memorygames.api.online;

/** Session-owned START intent; pairing alone must never authorize another queue entry. */
public final class MatchAcceptance {
    public enum Action { IGNORE, RETURN_TO_LOBBY, REQUEUE }

    private boolean startRequested;

    void requestStart() {
        startRequested = true;
    }

    boolean isStartRequested() {
        return startRequested;
    }

    Action cancel(boolean hadMatch, String mode, boolean leavingMatch) {
        Action action = Action.IGNORE;
        if (hadMatch) {
            action = startRequested && OnlineSession.MODE_RANKED.equals(mode) && !leavingMatch
                    ? Action.REQUEUE : Action.RETURN_TO_LOBBY;
        }
        clear();
        return action;
    }

    void clear() {
        startRequested = false;
    }
}
