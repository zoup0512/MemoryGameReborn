package com.memorygames.pk.party;

import java.util.ArrayList;
import java.util.List;

/** v1 party payloads. Round indexes are zero-based; scores never come from clients. */
public final class PartyProtocol {
    private PartyProtocol() { }
    public static class Command {
        public String lobbyId, matchId, code;
        public Integer roundCount, roundIndex, level;
        public Long playerId;
        public Boolean ready, success;
    }
    public static class Member {
        public long playerId;
        public String nickname, avatarValue;
        public boolean ready, connected;
    }
    public static class Participant {
        public long playerId;
        public String nickname, avatarValue;
        public boolean connected, withdrawn, roundEligible;
        public int score, points2, firstPlaces, rank;
    }
    public static class Entry {
        public long playerId;
        public int score, rank, points2;
        public boolean eligible;
    }
    public static class RoundResult {
        public int roundIndex;
        public String gameId;
        public List<Entry> entries = new ArrayList<>();
    }
    public static class State {
        public String lobbyId, code, phase, matchId, status, reason;
        public long hostId, stateVersion, serverTime, startAt, deadlineAt, phaseDeadlineAt;
        public int roundCount, capacity = 6, rulesVersion = 1, roundIndex = -1;
        public List<com.memorygames.pk.protocol.ProtocolCodec.RoundDesc> rounds = new ArrayList<>();
        public List<Member> members = new ArrayList<>();
        public List<Participant> participants = new ArrayList<>();
        public List<RoundResult> results = new ArrayList<>();
    }
    public record Left(String lobbyId, String reason) { }
    public record HistoryEntry(String matchId, long ts, String status, int roundCount,
                               int participantCount, int rank, int points2, int firstPlaces) { }
    public record History(List<HistoryEntry> matches) { }
}
