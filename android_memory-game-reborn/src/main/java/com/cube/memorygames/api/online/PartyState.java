package com.cube.memorygames.api.online;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** FRIEND_PARTY wire snapshot. Indices are zero based; points2 is exact half-points. */
public final class PartyState {
    public String lobbyId, code, phase, matchId, status, reason;
    public long hostId, stateVersion, serverTime, startAt, deadlineAt, phaseDeadlineAt;
    public int roundCount, capacity, rulesVersion, roundIndex = -1;
    public Round[] rounds = new Round[0];
    public Member[] members = new Member[0];
    public Participant[] participants = new Participant[0];
    public Result[] results = new Result[0];

    public static class Round { public String gameId; public int durationMs; public long seed; }
    public static class Member {
        public long playerId; public String nickname, avatarValue;
        public boolean ready, connected;
    }
    public static class Participant {
        public long playerId; public String nickname, avatarValue;
        public boolean connected, withdrawn, roundEligible;
        public int score, points2, firstPlaces, rank;
    }
    public static class Entry { public long playerId; public int score, rank, points2; public boolean eligible; }
    public static class Result { public int roundIndex; public String gameId; public Entry[] entries = new Entry[0]; }
    public static class History {
        public String matchId, status; public long ts;
        public int roundCount, participantCount, rank, points2, firstPlaces;
    }
    public static class HistoryResponse { public History[] matches = new History[0]; }
    public static class Left { public String lobbyId, reason; }

    public Member member(long id) {
        if (members != null) for (Member p : members) if (p != null && p.playerId == id) return p;
        return null;
    }
    public Participant participant(long id) {
        if (participants != null) for (Participant p : participants) if (p != null && p.playerId == id) return p;
        return null;
    }
    public boolean ownsRound(String lobby, String match, int round) {
        return lobbyId != null && lobbyId.equals(lobby) && matchId != null
                && matchId.equals(match) && roundIndex == round;
    }
    public Round round() {
        return rounds != null && roundIndex >= 0 && roundIndex < rounds.length ? rounds[roundIndex] : null;
    }
    public boolean is(String value) { return value.equals(phase); }
    public boolean activeRound() { return is("LOADING") || is("COUNTDOWN") || is("PLAYING"); }
    public boolean canStart(long playerId) {
        if (!is("LOBBY") || hostId != playerId || members == null || members.length < 3 || members.length > 6) return false;
        for (Member p : members) if (!p.connected || (p.playerId != hostId && !p.ready)) return false;
        return true;
    }
    /** All real participants, never a synthetic single opponent. */
    public List<Participant> leaderboard() {
        List<Participant> list = new ArrayList<>();
        if (participants != null) for (Participant p : participants) if (p != null) list.add(p);
        Collections.sort(list, (a, b) -> {
            if (a.withdrawn != b.withdrawn) return a.withdrawn ? 1 : -1;
            int points = Integer.compare(b.points2, a.points2);
            if (points != 0) return points;
            int first = Integer.compare(b.firstPlaces, a.firstPlaces);
            return first != 0 ? first : Integer.compare(b.score, a.score);
        });
        return list;
    }
    public static String points(int points2) {
        return points2 % 2 == 0 ? Integer.toString(points2 / 2) : (points2 / 2) + ".5";
    }
}
