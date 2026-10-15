package com.memorygames.pk;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-match integration test: two real WebSocket clients (mirroring the
 * Android OnlineClient) register, get matched, play five accelerated rounds
 * and receive consistent settlements — exercising the gateway, matchmaker,
 * room state machine, server-authoritative scoring, ELO and the coin
 * ledger end to end.
 */
@org.springframework.context.annotation.Import(PassingNicknameTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FullMatchIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    GameCatalog catalog;

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

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void twoPlayersPlayAFullMatch() throws Exception {
        TestWsClient a = newClient();
        TestWsClient b = newClient();
        try {
            ProtocolCodec.AuthOk profileA = register(a, "Alice");
            ProtocolCodec.AuthOk profileB = register(b, "Bob");
            assertEquals(100, profileA.balance);
            assertEquals(1000, profileA.elo);

            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());
            b.send(ProtocolCodec.T_MATCH_JOIN, new Object());

            TestWsClient.Frame foundA = a.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            TestWsClient.Frame foundB = b.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            b.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(a.await(ProtocolCodec.T_OPPONENT_START, 5000),
                    "both STARTs must flip the players to the ready state");
            assertNotNull(b.await(ProtocolCodec.T_OPPONENT_START, 5000));
            assertNotNull(foundA, "Alice must be matched");
            assertNotNull(foundB, "Bob must be matched");
            ProtocolCodec.MatchFound matchA = foundA.as(ProtocolCodec.MatchFound.class);
            ProtocolCodec.MatchFound matchB = foundB.as(ProtocolCodec.MatchFound.class);
            assertEquals(matchA.matchId, matchB.matchId, "both sides share one match");
            assertEquals(5, matchA.rounds.length);
            assertEquals("Bob", matchA.opponent.nickname);
            assertEquals("Alice", matchB.opponent.nickname);

            int totalA = 0;
            int totalB = 0;
            for (int round = 1; round <= 5; round++) {
                TestWsClient.Frame startA = a.await(ProtocolCodec.T_ROUND_START, 10000);
                TestWsClient.Frame startB = b.await(ProtocolCodec.T_ROUND_START, 10000);
                assertNotNull(startA, "round " + round + " must start for Alice");
                assertNotNull(startB, "round " + round + " must start for Bob");
                ProtocolCodec.RoundStart rsA = startA.as(ProtocolCodec.RoundStart.class);
                ProtocolCodec.RoundStart rsB = startB.as(ProtocolCodec.RoundStart.class);
                assertEquals(round, rsA.round);
                assertEquals(rsA.gameId, rsB.gameId, "same game per round");
                assertEquals(rsA.durationMs, rsB.durationMs, "same duration per round");

                GameCatalog.GameSpec spec = this.catalog.byId(rsA.gameId);
                assertNotNull(spec, "round game must be in the launch pool");

                // Alice wins three levels; Bob idles.
                int roundScoreA = 0;
                for (int level = 1; level <= 3; level++) {
                    ProtocolCodec.LevelWin win = new ProtocolCodec.LevelWin();
                    win.round = round;
                    win.level = level;
                    win.score = 0;
                    a.send(ProtocolCodec.T_LEVEL_WIN, win);
                    roundScoreA += spec.levelScore(level);
                    Thread.sleep(250); // > min-level-interval override (200ms)
                }
                totalA += roundScoreA;

                // The server relays one opponent_score per accepted level;
                // assert on the final cumulative frame.
                TestWsClient.Frame oppScore = null;
                for (int i = 0; i < 3; i++) {
                    TestWsClient.Frame frame = b.await(ProtocolCodec.T_OPPONENT_SCORE, 3000);
                    assertNotNull(frame, "Bob must see Alice's score");
                    oppScore = frame;
                }
                assertEquals(totalA, oppScore.as(ProtocolCodec.OpponentScore.class).score,
                        "cumulative server-recomputed score matches the client score table");

                ProtocolCodec.RoundComplete doneA = new ProtocolCodec.RoundComplete();
                doneA.round = round;
                doneA.score = roundScoreA;
                a.send(ProtocolCodec.T_ROUND_COMPLETE, doneA);
                ProtocolCodec.RoundComplete doneB = new ProtocolCodec.RoundComplete();
                doneB.round = round;
                doneB.score = 0;
                b.send(ProtocolCodec.T_ROUND_COMPLETE, doneB);

                TestWsClient.Frame endA = a.await(ProtocolCodec.T_ROUND_END, 15000);
                TestWsClient.Frame endB = b.await(ProtocolCodec.T_ROUND_END, 5000);
                assertNotNull(endA, "round " + round + " must end for Alice");
                assertNotNull(endB, "round " + round + " must end for Bob");
                ProtocolCodec.RoundEnd reA = endA.as(ProtocolCodec.RoundEnd.class);
                assertEquals(totalA, reA.myScore, "round_end carries cumulative totals");
                assertEquals(0, reA.oppScore);
            }

            TestWsClient.Frame matchEndA = a.await(ProtocolCodec.T_MATCH_END, 15000);
            TestWsClient.Frame matchEndB = b.await(ProtocolCodec.T_MATCH_END, 5000);
            assertNotNull(matchEndA, "Alice must receive the settlement");
            assertNotNull(matchEndB, "Bob must receive the settlement");
            ProtocolCodec.MatchEnd meA = matchEndA.as(ProtocolCodec.MatchEnd.class);
            ProtocolCodec.MatchEnd meB = matchEndB.as(ProtocolCodec.MatchEnd.class);
            assertEquals(ProtocolCodec.RESULT_WIN, meA.result);
            assertEquals(ProtocolCodec.RESULT_LOSE, meB.result);
            assertEquals(totalA, meA.myScore);
            assertEquals(meA.myScore, meB.oppScore);
            assertEquals(meB.myScore, meA.oppScore);
            assertTrue(meA.eloDelta > 0, "winner gains ELO");
            assertEquals(-meA.eloDelta, meB.eloDelta, "ELO is zero-sum");
            assertEquals(50, meA.coinDelta, "win payout");
            assertEquals(0, meB.coinDelta, "no payout for the loser");
            assertEquals(120, meA.balance, "100 - 30 entry + 50 win");
            assertEquals(70, meB.balance, "100 - 30 entry");
            assertEquals(1016, meA.elo, "K=32 symmetric start delta");
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void lonelyPlayerGetsBotFallback() throws Exception {
        TestWsClient a = newClient();
        try {
            register(a, "Lonely");
            a.send(ProtocolCodec.T_MATCH_JOIN, new Object());

            TestWsClient.Frame found = a.await(ProtocolCodec.T_MATCH_FOUND, 10000);
            assertNotNull(found, "bot fallback must produce a match");
            ProtocolCodec.MatchFound match = found.as(ProtocolCodec.MatchFound.class);
            assertTrue(match.opponent.isBot, "opponent must be a bot");
            a.send(ProtocolCodec.T_MATCH_START, new Object());
            assertNotNull(a.await(ProtocolCodec.T_OPPONENT_START, 5000),
                    "bot's instant accept flips the ready state too");

            // The bot scores during the rounds.
            TestWsClient.Frame start = a.await(ProtocolCodec.T_ROUND_START, 10000);
            assertNotNull(start);
            assertNotNull(a.await(ProtocolCodec.T_OPPONENT_SCORE, 10000),
                    "the bot must report progress");

            // Let the server drive the match to its end via round deadlines.
            TestWsClient.Frame end = a.await(ProtocolCodec.T_MATCH_END, 90000);
            assertNotNull(end, "match must settle via deadline forfeits");
            ProtocolCodec.MatchEnd result = end.as(ProtocolCodec.MatchEnd.class);
            assertNotNull(result.result);
            assertTrue(result.result.equals(ProtocolCodec.RESULT_WIN)
                    || result.result.equals(ProtocolCodec.RESULT_LOSE)
                    || result.result.equals(ProtocolCodec.RESULT_DRAW));
        } finally {
            a.close();
        }
    }
}
