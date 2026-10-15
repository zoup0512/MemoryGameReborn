package com.memorygames.pk;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Practice (play-vs-bot) integration test (docs/bot-battle-prd.md): an
 * explicit bot_match_request pairs instantly against a difficulty-tier bot,
 * the full match settles with zero ELO, tier coins and no entry fee, and the
 * daily reward cap zeroes later wins without touching the settlement itself.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BotPracticeIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    GameCatalog catalog;

    @Autowired
    PkProperties properties;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    private ProtocolCodec.AuthOk register(TestWsClient client, String nickname) {
        ProtocolCodec.Register register = new ProtocolCodec.Register();
        register.uuid = UUID.randomUUID().toString();
        register.nickname = nickname;
        register.balance = 100;
        client.send(ProtocolCodec.T_REGISTER, register);
        TestWsClient.Frame frame = client.await(ProtocolCodec.T_AUTH_OK, 5000);
        assertNotNull(frame, nickname + " must receive auth_ok");
        return frame.as(ProtocolCodec.AuthOk.class);
    }

    /**
     * Plays one full practice match against the given tier, winning every
     * round with seven levels (the bronze bot answers at most six). Returns
     * the settlement payload.
     */
    private ProtocolCodec.MatchEnd playPracticeMatch(TestWsClient a, int expectedBotElo,
                                                     String expectedBotName) throws Exception {
        ProtocolCodec.BotMatchRequest request = new ProtocolCodec.BotMatchRequest();
        request.difficulty = "BRONZE";
        a.send(ProtocolCodec.T_BOT_MATCH_REQUEST, request);

        TestWsClient.Frame found = a.await(ProtocolCodec.T_MATCH_FOUND, 10000);
        assertNotNull(found, "a practice request must pair instantly");
        ProtocolCodec.MatchFound match = found.as(ProtocolCodec.MatchFound.class);
        assertEquals(ProtocolCodec.MODE_PRACTICE, match.mode, "mode must be PRACTICE");
        assertTrue(match.opponent.isBot, "opponent must be a bot");
        assertEquals(expectedBotElo, match.opponent.elo, "bot rating comes from the tier table");
        assertEquals(expectedBotName, match.opponent.nickname, "bot nickname comes from the tier");

        a.send(ProtocolCodec.T_MATCH_START, new Object());
        assertNotNull(a.await(ProtocolCodec.T_OPPONENT_START, 5000),
                "the bot's instant accept flips the ready state");

        int total = 0;
        for (int round = 1; round <= 5; round++) {
            TestWsClient.Frame start = a.await(ProtocolCodec.T_ROUND_START, 10000);
            assertNotNull(start, "round " + round + " must start");
            ProtocolCodec.RoundStart rs = start.as(ProtocolCodec.RoundStart.class);
            assertEquals(round, rs.round);
            GameCatalog.GameSpec spec = this.catalog.byId(rs.gameId);

            // Seven wins at 250ms: above the 200ms floor override, below the
            // bot's bronze pace (450ms+), so the human always outscores it.
            for (int level = 1; level <= 7; level++) {
                ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
                win.round = round;
                win.level = level;
                win.score = 0;
                a.send(ProtocolCodec.T_LEVEL_WIN, win);
                total += spec.levelScore(level);
                Thread.sleep(250);
            }
            assertNotNull(a.await(ProtocolCodec.T_OPPONENT_SCORE, 5000),
                    "the bot must report progress during the round");

            ProtocolCodec.RoundComplete done = new ProtocolCodec.RoundComplete();
            done.round = round;
            done.score = total;
            a.send(ProtocolCodec.T_ROUND_COMPLETE, done);
        }

        TestWsClient.Frame end = a.await(ProtocolCodec.T_MATCH_END, 60000);
        assertNotNull(end, "the practice match must settle");
        return end.as(ProtocolCodec.MatchEnd.class);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void practiceMatchPaysTierCoinsWithoutEloOrFee() throws Exception {
        TestWsClient a = newClient();
        try {
            register(a, "Practicer");
            int cap = this.properties.getPractice().getDailyRewardCap();

            // Cap 0: the win still settles but pays nothing.
            this.properties.getPractice().setDailyRewardCap(0);
            ProtocolCodec.MatchEnd capped = playPracticeMatch(a, 800, "Bronze Bot");
            assertEquals(ProtocolCodec.RESULT_WIN, capped.result,
                    "seven levels per round must outscore the bronze bot");
            assertEquals(0, capped.eloDelta, "practice duels never move ELO");
            assertEquals(0, capped.coinDelta, "wins beyond the daily cap pay nothing");
            assertEquals(100, capped.balance, "no entry fee, no payout");

            // Cap restored: the next win pays the bronze tier coins.
            this.properties.getPractice().setDailyRewardCap(cap);
            ProtocolCodec.MatchEnd paid = playPracticeMatch(a, 800, "Bronze Bot");
            assertEquals(ProtocolCodec.RESULT_WIN, paid.result);
            assertEquals(0, paid.eloDelta, "practice duels never move ELO");
            assertEquals(8, paid.coinDelta, "the bronze tier payout applies");
            assertEquals(108, paid.balance, "100 + 8 tier coins, no entry fee");
            // The cap-zeroed win above still counted as a win, so the paid
            // win is this account's second practice streak link.
            assertEquals(2, paid.practiceStreak, "the cap-excluded win counted too");

            // A third win extends the streak.
            ProtocolCodec.MatchEnd second = playPracticeMatch(a, 800, "Bronze Bot");
            assertEquals(3, second.practiceStreak, "the streak extends across wins");
        } finally {
            a.close();
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void practiceStreakResetsOnLossAndTiersFlowOnAuth() throws Exception {
        TestWsClient a = newClient();
        try {
            ProtocolCodec.AuthOk profile = register(a, "Streaker");
            assertNotNull(profile.practiceTiers, "auth_ok must carry the tier table");
            assertEquals(5, profile.practiceTiers.length, "five tiers are shipped");
            assertEquals("BRONZE", profile.practiceTiers[0].id);
            assertEquals(800, profile.practiceTiers[0].elo);
            assertEquals(0, profile.practiceStreak);

            // Two practice wins build a streak...
            ProtocolCodec.MatchEnd first = playPracticeMatch(a, 800, "Bronze Bot");
            assertEquals(1, first.practiceStreak);
            ProtocolCodec.MatchEnd second = playPracticeMatch(a, 800, "Bronze Bot");
            assertEquals(2, second.practiceStreak);

            // ...an idle loss (never answering) resets it.
            ProtocolCodec.BotMatchRequest request = new ProtocolCodec.BotMatchRequest();
            request.difficulty = "MASTER";
            a.send(ProtocolCodec.T_BOT_MATCH_REQUEST, request);
            assertNotNull(a.await(ProtocolCodec.T_MATCH_FOUND, 10000));
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(a.await(ProtocolCodec.T_OPPONENT_START, 5000));
            TestWsClient.Frame loss = a.await(ProtocolCodec.T_MATCH_END, 90000);
            assertNotNull(loss, "the idle match must settle via round deadlines");
            assertEquals(ProtocolCodec.RESULT_LOSE, loss.as(ProtocolCodec.MatchEnd.class).result,
                    "an idle human loses to the master bot");
            assertEquals(0, loss.as(ProtocolCodec.MatchEnd.class).practiceStreak,
                    "a loss resets the practice streak");
        } finally {
            a.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void unknownDifficultyFallsBackWithoutError() throws Exception {
        TestWsClient a = newClient();
        try {
            register(a, "Explorer");
            ProtocolCodec.BotMatchRequest request = new ProtocolCodec.BotMatchRequest();
            request.difficulty = "NOPE";
            a.send(ProtocolCodec.T_BOT_MATCH_REQUEST, request);
            TestWsClient.Frame found = a.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            assertNotNull(found, "an unknown tier falls back instead of erroring");
            ProtocolCodec.MatchFound match = found.as(ProtocolCodec.MatchFound.class);
            assertEquals(ProtocolCodec.MODE_PRACTICE, match.mode);
            assertTrue(match.opponent.isBot);
            assertEquals(800, match.opponent.elo, "fallback is the first tier (bronze)");
            // Resigning during the START gate cancels (and refunds) instead
            // of settling — and practice refunds nothing it never charged.
            a.send(ProtocolCodec.T_RESIGN, new Object());
            TestWsClient.Frame cancelled = a.await(ProtocolCodec.T_MATCH_CANCELLED, 15000);
            assertNotNull(cancelled, "a gate-period resign cancels the match");
        } finally {
            a.close();
        }
    }
}
