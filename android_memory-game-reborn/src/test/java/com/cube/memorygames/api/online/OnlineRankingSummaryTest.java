package com.cube.memorygames.api.online;

import org.junit.Test;

import static org.junit.Assert.*;

public class OnlineRankingSummaryTest {
    @Test public void neverSyncedProfileDoesNotInventRatingOrResults() {
        assertNull(OnlineRankingSummary.from(null, null));
        assertNull(OnlineRankingSummary.from(new OnlineMessages.AuthOk(), null));
        assertNull(OnlineRankingSummary.from(null, new AccountManager.UserProfile()));
    }

    @Test public void liveProfileWinsOverOlderCacheForTheSameUser() {
        OnlineMessages.AuthOk live = profile(7L);
        live.nickname = "清风小鹿";
        live.elo = 1450;
        live.rank = 2;
        live.wins = 8;
        live.losses = 1;
        live.draws = 1;
        live.seasonIndex = 3;
        live.seasonEndsAt = 10L * 86400000L;
        AccountManager.UserProfile cached = cached(7L);
        cached.elo = 1000;
        OnlineRankingSummary summary = OnlineRankingSummary.from(live, cached);
        assertEquals("清风小鹿", summary.nickname);
        assertEquals(1450, summary.elo);
        assertEquals(2, summary.tier);
        assertEquals(8, summary.wins);
        assertEquals(80, summary.winRate());
        assertTrue(summary.hasSeason());
        assertEquals(3, summary.seasonIndex);
        assertEquals(2, summary.daysLeft(8L * 86400000L));
        assertEquals(0, summary.daysLeft(11L * 86400000L));
    }

    @Test public void cacheNeverInventsSeasonOrDerivesTierFromElo() {
        AccountManager.UserProfile cached = cached(7L);
        cached.elo = 1600;
        cached.wins = 2;
        cached.losses = 1;
        OnlineRankingSummary summary = OnlineRankingSummary.from(null, cached);
        assertEquals(1600, summary.elo);
        assertEquals(-1, summary.tier);
        assertEquals(67, summary.winRate());
        assertFalse(summary.hasSeason());
    }

    @Test public void differentIdentityCannotReusePreviousSocketResults() {
        OnlineMessages.AuthOk live = profile(7L);
        live.elo = 1900;
        AccountManager.UserProfile cached = cached(8L);
        cached.elo = 1100;
        OnlineRankingSummary summary = OnlineRankingSummary.from(live, cached);
        assertEquals(1100, summary.elo);
        assertEquals(-1, summary.tier);
        assertFalse(summary.hasSeason());
    }

    @Test public void missingCacheAllowsAuthenticatedLegacyProfile() {
        OnlineMessages.AuthOk live = profile(7L);
        live.elo = 1250;
        assertEquals(1250, OnlineRankingSummary.from(live, null).elo);
    }

    @Test public void rateHandlesEmptyDrawnAndLargeCounters() {
        OnlineMessages.AuthOk live = profile(7L);
        assertEquals(0, OnlineRankingSummary.from(live, null).winRate());
        live.wins = 1;
        live.draws = 1;
        assertEquals(50, OnlineRankingSummary.from(live, null).winRate());
        live.wins = Integer.MAX_VALUE;
        live.losses = Integer.MAX_VALUE;
        live.draws = 0;
        assertEquals(50, OnlineRankingSummary.from(live, null).winRate());
        live.wins = -1;
        live.losses = -1;
        assertEquals(0, OnlineRankingSummary.from(live, null).winRate());
    }

    @Test public void largeTextReflowsStatsWithoutShrinkingOrHidingNumbers() {
        assertEquals(4, OnlineRankingSummary.statColumns(296, 64, 1.0f));
        assertEquals(2, OnlineRankingSummary.statColumns(256, 64, 1.3f));
        assertEquals(2, OnlineRankingSummary.statColumns(256, 64, 2.0f));
        assertEquals(1, OnlineRankingSummary.statColumns(240, 64, 2.0f));
        assertEquals(4, OnlineRankingSummary.statColumns(528, 64, 2.0f));
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
}
