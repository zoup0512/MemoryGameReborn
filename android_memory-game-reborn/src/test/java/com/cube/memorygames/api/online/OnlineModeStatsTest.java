package com.cube.memorygames.api.online;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class OnlineModeStatsTest {
    @Test public void missingModeDataNeverReusesMixedLegacyTotals() {
        OnlineMessages.AuthOk live = profile(7L);
        live.wins = 90;
        live.losses = 10;
        AccountManager.UserProfile cached = cached(7L);
        cached.wins = 90;
        assertNull(OnlineModeStats.forMode(null, null, OnlineSession.MODE_RANKED));
        assertNull(OnlineModeStats.forMode(live, null, OnlineSession.MODE_RANKED));
        assertNull(OnlineModeStats.forMode(null, cached, OnlineSession.MODE_PRACTICE));
    }

    @Test public void eachModeSelectsOnlyItsOwnRecordIncludingDraws() {
        OnlineMessages.AuthOk live = profile(7L);
        live.modeStats = snapshot(8, 1, 1, 2, 6, 2, 1, 2, 1);
        OnlineModeStats ranked = OnlineModeStats.forMode(live, null, OnlineSession.MODE_RANKED);
        OnlineModeStats practice = OnlineModeStats.forMode(live, null, OnlineSession.MODE_PRACTICE);
        OnlineModeStats friend = OnlineModeStats.forMode(live, null, OnlineSession.MODE_FRIEND);
        assertEquals(8L, ranked.wins);
        assertEquals(1L, ranked.losses);
        assertEquals(1L, ranked.draws);
        assertEquals(80, ranked.winRate());
        assertEquals(2L, practice.wins);
        assertEquals(6L, practice.losses);
        assertEquals(20, practice.winRate());
        assertEquals(1L, friend.wins);
        assertEquals(2L, friend.losses);
        assertEquals(25, friend.winRate());
        assertNull(OnlineModeStats.forMode(live, null, "UNKNOWN"));
        assertNull(OnlineModeStats.forMode(live, null, null));
    }

    @Test public void liveSnapshotWinsOverCachedModeStatsForSameIdentity() {
        OnlineMessages.AuthOk live = profile(7L);
        live.modeStats = snapshot(3, 1, 0, 0, 0, 0, 0, 0, 0);
        AccountManager.UserProfile cached = cached(7L);
        cached.modeStats = snapshot(1, 8, 0, 5, 1, 0, 3, 3, 0);
        assertEquals(75, OnlineModeStats.forMode(live, cached, OnlineSession.MODE_RANKED).winRate());
        assertEquals(0L, OnlineModeStats.forMode(live, cached, OnlineSession.MODE_PRACTICE).wins);
        live.modeStats = null;
        assertNull(OnlineModeStats.forMode(live, cached, OnlineSession.MODE_RANKED));
    }

    @Test public void oldSocketCannotLeakRecordsIntoDifferentAccount() {
        OnlineMessages.AuthOk oldLive = profile(7L);
        oldLive.modeStats = snapshot(50, 0, 0, 0, 0, 0, 0, 0, 0);
        AccountManager.UserProfile current = cached(8L);
        current.modeStats = snapshot(1, 3, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(25, OnlineModeStats.forMode(oldLive, current, OnlineSession.MODE_RANKED).winRate());
        current.modeStats = null;
        assertNull(OnlineModeStats.forMode(oldLive, current, OnlineSession.MODE_RANKED));
        assertNull(OnlineModeStats.forMode(profile(0L), cached(0L), OnlineSession.MODE_RANKED));
    }

    @Test public void cachedRestSnapshotWorksWithoutInventingUnknownBuckets() {
        AccountManager.UserProfile cached = cached(7L);
        cached.modeStats = new LinkedHashMap<>();
        cached.modeStats.put(OnlineSession.MODE_FRIEND, stats(2, 1, 0));
        assertEquals(67, OnlineModeStats.forMode(null, cached, OnlineSession.MODE_FRIEND).winRate());
        assertNull(OnlineModeStats.forMode(null, cached, OnlineSession.MODE_RANKED));
    }

    @Test public void emptyNegativeAndLargeCountersHaveSafeRates() {
        OnlineMessages.AuthOk live = profile(7L);
        live.modeStats = snapshot(0, 0, 0, -2, -1, -1, Long.MAX_VALUE, Long.MAX_VALUE, 0);
        assertEquals(0, OnlineModeStats.forMode(live, null, OnlineSession.MODE_RANKED).winRate());
        OnlineModeStats practice = OnlineModeStats.forMode(live, null, OnlineSession.MODE_PRACTICE);
        assertEquals(0L, practice.wins);
        assertEquals(0L, practice.losses);
        assertEquals(0L, practice.draws);
        assertEquals(0, practice.winRate());
        assertEquals(50, OnlineModeStats.forMode(live, null, OnlineSession.MODE_FRIEND).winRate());
    }

    @Test public void settlementReplacesFullSnapshotAndReplayNeverIncrements() {
        OnlineMessages.AuthOk live = profile(7L);
        live.modeStats = snapshot(2, 0, 0, 0, 0, 0, 0, 0, 0);
        OnlineMessages.MatchEnd end = new OnlineMessages.MatchEnd();
        end.mode = OnlineSession.MODE_PRACTICE;
        end.modeStats = snapshot(2, 0, 0, 1, 0, 0, 0, 0, 0);
        OnlineModeStats.applySettlement(live, end);
        OnlineModeStats.applySettlement(live, end);
        assertEquals(2L, OnlineModeStats.forMode(live, null, OnlineSession.MODE_RANKED).wins);
        assertEquals(1L, OnlineModeStats.forMode(live, null, OnlineSession.MODE_PRACTICE).wins);
        assertNotSame(end.modeStats, live.modeStats);
        assertNotSame(end.modeStats.get(OnlineSession.MODE_PRACTICE),
                live.modeStats.get(OnlineSession.MODE_PRACTICE));
        end.modeStats.get(OnlineSession.MODE_PRACTICE).wins = 100;
        assertEquals(1L, OnlineModeStats.forMode(live, null, OnlineSession.MODE_PRACTICE).wins);
        end.modeStats = null;
        OnlineModeStats.applySettlement(live, end);
        assertNull(OnlineModeStats.forMode(live, null, OnlineSession.MODE_PRACTICE));
    }

    @Test public void additiveWireFieldsParseInAuthRestAndSettlement() {
        String data = "{\"userId\":7,\"wins\":99,\"modeStats\":{"
                + "\"RANKED\":{\"wins\":3,\"losses\":1,\"draws\":0},"
                + "\"PRACTICE\":{\"wins\":0,\"losses\":0,\"draws\":0},"
                + "\"FRIEND\":{\"wins\":0,\"losses\":1,\"draws\":1}}}";
        OnlineMessages.AuthOk live = OnlineMessages.payloadOf(
                "{\"v\":1,\"t\":\"auth_ok\",\"d\":" + data + "}", OnlineMessages.AuthOk.class);
        AccountManager.UserProfile cached = new Gson().fromJson(data, AccountManager.UserProfile.class);
        assertEquals(99, live.wins);
        assertEquals(75, OnlineModeStats.forMode(live, null, OnlineSession.MODE_RANKED).winRate());
        assertEquals(75, OnlineModeStats.forMode(null, cached, OnlineSession.MODE_RANKED).winRate());
        OnlineMessages.MatchEnd end = OnlineMessages.payloadOf(
                "{\"v\":1,\"t\":\"match_end\",\"d\":{\"mode\":\"FRIEND\","
                        + "\"modeStats\":" + new Gson().toJson(live.modeStats) + "}}",
                OnlineMessages.MatchEnd.class);
        assertEquals(OnlineSession.MODE_FRIEND, end.mode);
        OnlineModeStats.applySettlement(live, end);
        assertEquals(1L, OnlineModeStats.forMode(live, null, OnlineSession.MODE_FRIEND).draws);
    }

    @Test public void largeTextReflowsStatsWithoutShrinkingNumbers() {
        assertEquals(4, OnlineModeStats.statColumns(296, 64, 1.0f));
        assertEquals(2, OnlineModeStats.statColumns(256, 64, 1.3f));
        assertEquals(1, OnlineModeStats.statColumns(240, 64, 2.0f));
    }

    private static OnlineMessages.AuthOk profile(long userId) {
        OnlineMessages.AuthOk profile = new OnlineMessages.AuthOk();
        profile.userId = userId;
        return profile;
    }

    private static AccountManager.UserProfile cached(long userId) {
        AccountManager.UserProfile profile = new AccountManager.UserProfile();
        profile.userId = userId;
        return profile;
    }

    private static OnlineMessages.MatchStats stats(long wins, long losses, long draws) {
        OnlineMessages.MatchStats stats = new OnlineMessages.MatchStats();
        stats.wins = wins;
        stats.losses = losses;
        stats.draws = draws;
        return stats;
    }

    private static Map<String, OnlineMessages.MatchStats> snapshot(long... values) {
        Map<String, OnlineMessages.MatchStats> result = new LinkedHashMap<>();
        result.put(OnlineSession.MODE_RANKED, stats(values[0], values[1], values[2]));
        result.put(OnlineSession.MODE_PRACTICE, stats(values[3], values[4], values[5]));
        result.put(OnlineSession.MODE_FRIEND, stats(values[6], values[7], values[8]));
        return result;
    }
}
