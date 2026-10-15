package com.cube.memorygames.api.online;

/** Contextual account actions in the lobby; full management lives in Settings. */
enum AccountEntry {
    NONE, BIND_EMAIL, LOGIN;

    static AccountEntry forState(boolean needsLogin, String accountType, boolean hasProfile) {
        if (needsLogin) return LOGIN;
        if (hasProfile && "GUEST".equalsIgnoreCase(accountType)) return BIND_EMAIL;
        return NONE;
    }
}
