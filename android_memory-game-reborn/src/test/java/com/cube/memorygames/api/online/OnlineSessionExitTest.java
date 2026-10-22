package com.cube.memorygames.api.online;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Drives the production session dispatcher, transport and delayed resign callbacks. */
public class OnlineSessionExitTest {
    private static final class Runtime implements OnlineSession.Runtime, PartySession.Runtime {
        final List<String> frames = new ArrayList<>();
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        final PartySession party = new PartySession(this);
        long elapsed;
        boolean online = true;

        public boolean connected() { return online; }
        public boolean accountBusy() { return false; }
        public boolean send(String json) { if (!online) return false; frames.add(json); return true; }
        public void postDelayed(Runnable task, long delay) { tasks.put(task, elapsed + delay); }
        public void removeCallbacks(Runnable task) { tasks.remove(task); }
        public long elapsedRealtime() { return elapsed; }
        public PartySession party() { return party; }
        public void debug(String message, Throwable trace) { }
        public boolean canAutoConnect() { return true; }
        public boolean requiresAccountLogin() { return false; }
        public boolean canChangeAccount() { return true; }
        public void connectIfDisconnected() { }
        public boolean send(String type, com.google.gson.JsonObject data, String rid) {
            return send(OnlineMessages.envelope(type, data));
        }
        void advance(long millis) {
            long target = elapsed + millis;
            while (true) {
                Runnable next = null;
                long due = Long.MAX_VALUE;
                for (Map.Entry<Runnable, Long> task : tasks.entrySet()) {
                    if (task.getValue() <= target && task.getValue() < due) {
                        next = task.getKey(); due = task.getValue();
                    }
                }
                if (next == null) break;
                elapsed = due; tasks.remove(next); next.run();
            }
            elapsed = target;
        }
        long count(String type) {
            return frames.stream().filter(frame -> type.equals(OnlineMessages.typeOf(frame))).count();
        }
    }

    private static class Listener implements OnlineSession.MatchListener {
        final List<OnlineMessages.MatchEnd> ends = new ArrayList<>();
        final List<MatchAcceptance.Action> cancellations = new ArrayList<>();
        String cancelledMode;
        public void onStateChanged(int state) { }
        public void onAuthOk(OnlineMessages.AuthOk profile) { }
        public void onMatchFound(OnlineMessages.MatchFound match) { }
        public void onMatchCancelled(MatchAcceptance.Action action, String mode) {
            cancellations.add(action); cancelledMode = mode;
        }
        public void onRoundStart(OnlineMessages.RoundStart round) { }
        public void onOpponentScore(OnlineMessages.OpponentScore score) { }
        public void onRoundEnd(OnlineMessages.RoundEnd round) { }
        public void onMatchEnd(OnlineMessages.MatchEnd end) { ends.add(end); }
        public void onOpponentStart() { }
        public void onRoomCreated(String code) { }
        public void onHistory(OnlineMessages.HistoryEntry[] entries) { }
        public void onTop(OnlineMessages.TopEntry[] entries) { }
        public void onOpponentDisconnected() { }
        public void onOpponentReconnected() { }
        public void onError(int code, String message) { }
    }

    private static final class Fixture {
        final Runtime runtime = new Runtime();
        final OnlineSession session = new OnlineSession(runtime);
        final Listener listener = new Listener();
        final OnlineMessages.AuthOk profile = new OnlineMessages.AuthOk();
        Fixture() throws Exception {
            profile.userId = 7; profile.balance = 500; profile.elo = 1200;
            set(session, "state", OnlineSession.STATE_IDLE);
            set(session, "lastProfile", profile);
            session.setMatchListener(listener);
            session.sendBotMatchRequest("BRONZE");
            receive("match_found", "{\"matchId\":\"old\",\"mode\":\"PRACTICE\","
                    + "\"opponent\":{\"isBot\":true},\"rounds\":[{\"gameId\":\"22\",\"durationMs\":45000}]}");
            assertEquals("old", session.getMatchId());
        }
        void start() throws Exception {
            session.sendMatchStart();
            receive("round_start", "{\"matchId\":\"old\",\"round\":1,\"gameId\":\"22\",\"durationMs\":45000}");
            assertEquals(1, session.getCurrentRound());
        }
        void ack() throws Exception { receive("resign_ack", "{\"matchId\":\"old\"}"); }
        void end() throws Exception {
            receive("match_end", "{\"matchId\":\"old\",\"mode\":\"PRACTICE\",\"result\":\"L\","
                    + "\"myScore\":12,\"oppScore\":34,\"elo\":1180,\"balance\":480,"
                    + "\"myWins\":2,\"myLosses\":3,\"practiceStreak\":0,"
                    + "\"modeStats\":{\"PRACTICE\":{\"wins\":2,\"losses\":3,\"draws\":0}}}");
        }
        void receive(String type, String data) throws Exception {
            Method dispatch = OnlineSession.class.getDeclaredMethod("handleMessage", String.class);
            dispatch.setAccessible(true);
            dispatch.invoke(session, "{\"v\":1,\"t\":\"" + type + "\",\"d\":" + data + "}");
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    @Test public void resignAckReleasesSeatButLaterTerminalStillSettlesExactlyOnce() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign();
        assertEquals(1, f.runtime.count("resign"));
        f.ack();
        assertNull(f.session.getMatchId());
        assertEquals(OnlineSession.STATE_IDLE, f.session.getState());
        assertFalse(f.session.hasNonPartyFlow());
        assertTrue(f.session.canChangeAccount());
        assertEquals(500, f.profile.balance); // ACK is not a settlement.
        f.runtime.advance(6000);
        assertEquals(1, f.runtime.count("resign"));
        f.end();
        assertEquals(1, f.listener.ends.size());
        assertEquals(480, f.profile.balance);
        assertEquals(1180, f.profile.elo);
        assertEquals(3, f.profile.losses);
        f.ack(); f.end();
        assertEquals(1, f.listener.ends.size());
    }

    @Test public void releasedTerminalRestoresClonedRoundLabelsWithoutRestoringMatchOccupancy() throws Exception {
        Fixture f = new Fixture(); f.start();
        OnlineMessages.RoundDesc[] original = f.session.getRounds();
        final String[] callbackLabel = {null};
        Listener owner = new Listener() {
            @Override public void onMatchEnd(OnlineMessages.MatchEnd end) {
                assertFalse(f.session.isInMatch());
                callbackLabel[0] = f.session.getRounds()[0].gameId;
            }
        };
        f.session.setMatchListener(owner); f.session.resign(); f.ack();
        assertNull(f.session.getRounds());
        original[0] = new OnlineMessages.RoundDesc(); original[0].gameId = "changed-array";
        f.end();
        assertEquals("22", callbackLabel[0]);
        assertNotSame(original, f.session.getRounds());
        assertFalse(f.session.isInMatch());
        assertFalse(f.session.hasNonPartyFlow());
        assertTrue(f.session.canChangeAccount());
        assertEquals(-1, f.session.getCurrentRound());
        assertNull(f.session.getRoundStartForRestore());
    }

    @Test public void replacementRequestPreventsOldTerminalFromRestoringRoundLabels() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
        f.session.sendBotMatchRequest("BRONZE"); f.end();
        assertNull(f.session.getRounds());
        assertEquals(0, f.listener.ends.size());
        assertTrue(f.session.hasNonPartyFlow());
    }

    @Test public void endBeforeAckAndDuplicateOrUntaggedEndCannotSettleAgain() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.end();
        assertEquals(1, f.listener.ends.size());
        f.profile.balance = 777;
        f.ack(); f.end();
        f.receive("match_end", "{}");
        f.runtime.advance(6000);
        assertEquals(1, f.listener.ends.size());
        assertEquals(777, f.profile.balance);
        assertEquals(1, f.runtime.count("resign"));
    }

    @Test public void ackDoesNotExtendDeadlineAndMissingTerminalNeverLocksAdmission() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
        f.runtime.advance(89000); f.ack(); f.runtime.advance(1001); f.end();
        assertTrue(f.session.canChangeAccount());
        assertFalse(f.session.hasNonPartyFlow());
        assertEquals(0, f.listener.ends.size());
        assertEquals(500, f.profile.balance);
        assertEquals(0, f.runtime.tasks.size());
    }

    @Test public void unrelatedUntaggedAndMalformedTerminalCannotClaimReleasedIdentity() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign();
        f.receive("resign_ack", "{\"matchId\":\"other\"}");
        assertEquals("old", f.session.getMatchId());
        f.ack();
        f.receive("match_end", "{\"matchId\":\"other\",\"balance\":1}");
        f.receive("match_end", "{\"balance\":1}");
        f.receive("match_end", "{\"matchId\":null,\"balance\":1}");
        f.receive("match_end", "{\"matchId\":{\"bad\":true},\"balance\":1}");
        f.receive("match_end", "{\"matchId\":\"old\",\"balance\":{\"bad\":true}}");
        assertEquals(0, f.listener.ends.size());
        assertEquals(500, f.profile.balance);
        f.end();
        assertEquals(1, f.listener.ends.size());
        assertEquals(480, f.profile.balance);
    }

    @Test public void preStartCancellationAfterAckReturnsOwnerWithoutFakeSettlementOrRequeue() throws Exception {
        Fixture f = new Fixture(); f.session.sendMatchStart(); f.session.resign(); f.ack();
        f.receive("match_cancelled", "{\"matchId\":\"old\"}");
        f.receive("match_cancelled", "{\"matchId\":\"old\"}"); f.end();
        assertEquals(1, f.listener.cancellations.size());
        assertEquals(MatchAcceptance.Action.RETURN_TO_LOBBY, f.listener.cancellations.get(0));
        assertEquals(OnlineSession.MODE_PRACTICE, f.listener.cancelledMode);
        assertEquals(0, f.listener.ends.size());
        assertEquals(0, f.runtime.count("match_join"));
        assertEquals(500, f.profile.balance);
    }

    @Test public void preStartCancellationBeforeAckDoesNotCreatePendingSettlement() throws Exception {
        Fixture f = new Fixture(); f.session.resign();
        f.receive("match_cancelled", "{\"matchId\":\"old\"}"); f.ack(); f.end();
        assertEquals(1, f.listener.cancellations.size());
        assertEquals(0, f.listener.ends.size());
        assertTrue(f.session.canChangeAccount());
    }

    @Test public void newQueueFriendRoomAndPracticeRequestRetireOldResultImmediately() throws Exception {
        for (String flow : new String[]{"queue", "friend", "join", "practice"}) {
            Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
            if (flow.equals("queue")) f.session.startMatchmaking();
            else if (flow.equals("friend")) f.session.createFriendRoom();
            else if (flow.equals("join")) f.session.joinFriendRoom("1234");
            else f.session.sendBotMatchRequest("BRONZE");
            int state = f.session.getState();
            assertTrue(flow, f.session.hasNonPartyFlow());
            f.ack(); f.end(); f.receive("match_cancelled", "{\"matchId\":\"old\"}");
            assertEquals(flow, state, f.session.getState());
            assertTrue(flow, f.session.hasNonPartyFlow());
            assertEquals(flow, 0, f.listener.ends.size());
            assertEquals(flow, 0, f.listener.cancellations.size());
            assertEquals(flow, 500, f.profile.balance);
            // Retired stays retired even if the replacement flow is later cancelled.
            if (flow.equals("queue")) f.session.cancelMatchmaking();
            else f.receive("error", "{\"code\":409,\"message\":\"cancelled\"}");
            f.end(); assertEquals(flow, 0, f.listener.ends.size());
        }
    }

    @Test public void oldTerminalCannotClearNewMatchScoresClockModeOrSettlement() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
        f.session.joinFriendRoom("1234");
        f.receive("match_found", "{\"matchId\":\"new\",\"mode\":\"FRIEND\",\"rounds\":[{\"gameId\":\"23\",\"durationMs\":45000}]}");
        f.receive("round_start", "{\"matchId\":\"new\",\"round\":1,\"gameId\":\"23\",\"durationMs\":45000}");
        f.receive("opponent_score", "{\"matchId\":\"new\",\"round\":1,\"score\":77}");
        f.ack(); f.end(); f.receive("match_cancelled", "{\"matchId\":\"old\"}");
        assertEquals("new", f.session.getMatchId());
        assertEquals(OnlineSession.STATE_MATCH, f.session.getState());
        assertEquals(1, f.session.getCurrentRound());
        assertEquals(77, f.session.getOpponentScore());
        assertEquals("23", f.session.getRounds()[0].gameId);
        assertTrue(f.session.isFriendMode());
        assertEquals(45000, f.session.roundRemainingMs());
        assertEquals(500, f.profile.balance);
        assertEquals(0, f.listener.ends.size());
        f.receive("match_end", "{\"matchId\":\"new\",\"result\":\"W\",\"balance\":550}");
        assertEquals(1, f.listener.ends.size());
        assertEquals("new", f.listener.ends.get(0).matchId);
        assertEquals(550, f.profile.balance);
    }

    @Test public void partyEntryInvalidatesOldResultWithoutClaimingLegacySession() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
        assertTrue(f.session.sendParty("party_join", new com.google.gson.JsonObject(), "party-new"));
        f.end();
        assertEquals(0, f.listener.ends.size());
        assertEquals(500, f.profile.balance);
    }

    @Test public void occupiedPartyAndThenResetCannotResurrectOldResult() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
        f.runtime.party.join("123456"); assertTrue(f.runtime.party.occupied());
        f.end(); f.runtime.party.reset(); f.end();
        assertEquals(0, f.listener.ends.size());
        assertEquals(500, f.profile.balance);
    }

    @Test public void nullOrReplacementListenerStillGetsProfileButNeverSomeoneElsesResult() throws Exception {
        for (boolean absent : new boolean[]{true, false}) {
            Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
            Listener replacement = new Listener();
            f.session.setMatchListener(absent ? null : replacement); f.end();
            assertEquals(480, f.profile.balance);
            assertEquals(0, f.listener.ends.size());
            assertEquals(0, replacement.ends.size());
        }
    }

    @Test public void beforeAckListenerChangeAndRepeatedResignCannotTransferResultOwnership() throws Exception {
        for (boolean absent : new boolean[]{true, false}) {
            Fixture f = new Fixture(); f.start(); f.session.resign();
            Listener replacement = new Listener();
            f.session.setMatchListener(absent ? null : replacement);
            f.session.resign(); // Same-match repeated intent must retain the original owner.
            f.ack(); f.end();
            assertEquals(480, f.profile.balance);
            assertEquals(0, f.listener.ends.size());
            assertEquals(0, replacement.ends.size());
        }
    }

    @Test public void restoredOriginalListenerBeforeTerminalStillReceivesItsResult() throws Exception {
        Fixture f = new Fixture(); f.start(); f.session.resign();
        f.session.setMatchListener(null); f.ack();
        f.session.setMatchListener(f.listener); f.end();
        assertEquals(1, f.listener.ends.size());
        assertEquals(480, f.profile.balance);
    }

    @Test public void disconnectAndAccountResetRetireResultAndDoNotTouchNewProfile() throws Exception {
        for (boolean account : new boolean[]{true, false}) {
            Fixture f = new Fixture(); f.start(); f.session.resign(); f.ack();
            if (account) f.session.resetForAccountChange(); else f.session.disconnect();
            OnlineMessages.AuthOk next = new OnlineMessages.AuthOk(); next.userId = 8; next.balance = 900;
            set(f.session, "lastProfile", next); f.end();
            assertEquals(900, next.balance);
            assertEquals(0, f.listener.ends.size());
            assertEquals(OnlineSession.STATE_DISCONNECTED, f.session.getState());
        }
    }

    @Test public void reentrantEndCallbackCannotReplayOrClearReplacementFlow() throws Exception {
        Fixture f = new Fixture(); f.start();
        final int[] count = {0};
        Listener owner = new Listener() {
            @Override public void onMatchEnd(OnlineMessages.MatchEnd end) {
                count[0]++;
                try { f.end(); } catch (Exception e) { throw new AssertionError(e); }
                f.session.sendBotMatchRequest("BRONZE");
            }
        };
        f.session.setMatchListener(owner); f.session.resign(); f.ack(); f.end();
        assertEquals(1, count[0]);
        assertEquals(480, f.profile.balance);
        assertTrue(f.session.hasNonPartyFlow());
    }
}
