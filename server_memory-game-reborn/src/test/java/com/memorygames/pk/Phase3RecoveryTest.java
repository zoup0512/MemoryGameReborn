package com.memorygames.pk;

import com.memorygames.pk.match.MatchInstance;
import com.memorygames.pk.match.MatchInstanceRepository;
import com.memorygames.pk.match.SettlementService;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.CoinLedger;
import com.memorygames.pk.user.CoinLedgerRepository;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-3 regression tests (P3 set): escrowed match lifecycle with
 * idempotent crash recovery, the append-only coin ledger, and the protocol
 * version gate / request-id correlation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase3RecoveryTest {

    @LocalServerPort
    int port;

    @Autowired
    UserService users;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SettlementService settlement;

    @Autowired
    MatchInstanceRepository instances;

    @Autowired
    CoinLedgerRepository ledger;

    private TestWsClient newClient() throws Exception {
        TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + this.port + "/ws"));
        client.connect();
        return client;
    }

    @Test
    @Timeout(30)
    void escrowedMatchRecoversIdempotentlyAfterCrash() {
        UserAccount a = this.users.register(UUID.randomUUID().toString(), "EscrowA");
        UserAccount b = this.users.register(UUID.randomUUID().toString(), "EscrowB");
        String matchId = "escrow-crash-1";

        // The room opened: fees left both wallets atomically with the row.
        this.settlement.openMatch(matchId, "RANKED", a.id, b.id, 30);
        assertEquals(70, this.userRepository.findById(a.id).orElseThrow().balance);
        assertEquals(70, this.userRepository.findById(b.id).orElseThrow().balance);
        MatchInstance instance = this.instances.findByMatchId(matchId).orElseThrow();
        assertEquals(MatchInstance.STATUS_ACTIVE, instance.status);
        assertEquals(30, instance.entryFee);
        assertEquals(2, this.ledger.findAll().stream()
                .filter(row -> matchId.equals(row.matchId) && CoinLedger.REASON_ENTRY_FEE.equals(row.reason))
                .count(), "one ENTRY_FEE ledger row per seat");

        // The process "crashed": nobody settled. Recovery aborts and refunds.
        assertTrue(this.settlement.abortMatch(matchId), "first recovery abort wins");
        assertEquals(100, this.userRepository.findById(a.id).orElseThrow().balance);
        assertEquals(100, this.userRepository.findById(b.id).orElseThrow().balance);
        assertEquals(MatchInstance.STATUS_ABORTED, this.instances.findByMatchId(matchId).orElseThrow().status);

        // A replayed recovery (restart loop) must be a no-op.
        assertFalse(this.settlement.abortMatch(matchId), "replayed abort changes nothing");
        assertEquals(100, this.userRepository.findById(a.id).orElseThrow().balance);
        assertEquals(100, this.userRepository.findById(b.id).orElseThrow().balance);
        assertEquals(2, this.ledger.findAll().stream()
                .filter(row -> matchId.equals(row.matchId) && CoinLedger.REASON_REFUND.equals(row.reason))
                .count(), "one REFUND row per seat, never two");
    }

    @Test
    @Timeout(30)
    void settlementWritesLedgerAndInstanceExactlyOnce() {
        UserAccount a = this.users.register(UUID.randomUUID().toString(), "LedgerA");
        UserAccount b = this.users.register(UUID.randomUUID().toString(), "LedgerB");
        String matchId = "ledger-settle-1";
        this.settlement.openMatch(matchId, "RANKED", a.id, b.id, 30);

        SettlementService.Command cmd = new SettlementService.Command();
        cmd.matchId = matchId;
        cmd.mode = "RANKED";
        cmd.entryCharged = true;
        cmd.entryFee = 30;
        cmd.createdAt = System.currentTimeMillis();
        cmd.botElo = 1000;
        for (int i = 0; i < 2; i++) {
            SettlementService.SeatPlan plan = new SettlementService.SeatPlan();
            plan.userId = i == 0 ? a.id : b.id;
            plan.nickname = i == 0 ? a.nickname : b.nickname;
            plan.score = i == 0 ? 90 : 10;
            plan.result = i == 0 ? "W" : "L";
            plan.eloDelta = i == 0 ? 16 : -16;
            plan.coinDelta = i == 0 ? 50 : 0;
            plan.win = i == 0;
            cmd.seats[i] = plan;
        }
        SettlementService.Result first = this.settlement.settle(cmd);
        assertFalse(first.alreadySettled);
        assertEquals(70 + 50, first.seats[0].balance);
        assertEquals(MatchInstance.STATUS_SETTLED, this.instances.findByMatchId(matchId).orElseThrow().status);

        // A replayed settlement command must not move anything again.
        SettlementService.Result replay = this.settlement.settle(cmd);
        assertTrue(replay.alreadySettled);
        UserAccount reloaded = this.userRepository.findById(a.id).orElseThrow();
        assertEquals(120, reloaded.balance, "coins applied exactly once");
        assertEquals(1, reloaded.wins);

        List<CoinLedger> rows = this.ledger.findAll().stream()
                .filter(row -> matchId.equals(row.matchId)).toList();
        assertEquals(3, rows.size(), "ENTRY_FEE x2 + PAYOUT x1");
        assertEquals(1, rows.stream().filter(r -> CoinLedger.REASON_PAYOUT.equals(r.reason)).count());
    }

    @Test
    @Timeout(30)
    void welcomeGrantIsLedgered() {
        this.users.register(UUID.randomUUID().toString(), "Welcomed");
        assertEquals(1, this.ledger.findAll().stream()
                .filter(row -> CoinLedger.REASON_WELCOME.equals(row.reason) && row.amount == 100)
                .count(), "the welcome grant leaves a WELCOME ledger row");
    }

    @Test
    @Timeout(30)
    void protocolVersionGateAndRequestIdEcho() throws Exception {
        TestWsClient a = newClient();
        try {
            // Wrong protocol version: fast 400, no silent misparse.
            a.sendRaw("{\"v\":9,\"t\":\"ping\",\"d\":{}}");
            TestWsClient.Frame versionError = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(versionError);
            assertEquals(ProtocolCodec.ERR_UNSUPPORTED_VERSION,
                    versionError.as(ProtocolCodec.ErrorMsg.class).code);

            // rid on a command is echoed on the immediate reply.
            a.sendRaw("{\"v\":1,\"t\":\"ping\",\"d\":{},\"rid\":\"corr-42\"}");
            TestWsClient.Frame pong = a.await(ProtocolCodec.T_PONG, 5000);
            assertNotNull(pong);
            assertEquals("corr-42", pong.rid, "pong echoes the request id");

            // rid survives on post-auth replies too.
            ProtocolCodec.Register register = new ProtocolCodec.Register();
            register.uuid = UUID.randomUUID().toString();
            register.nickname = "Corr";
            a.send(ProtocolCodec.T_REGISTER, register);
            a.await(ProtocolCodec.T_AUTH_OK, 5000);
            a.sendRaw("{\"v\":1,\"t\":\"top_request\",\"d\":{},\"rid\":\"corr-7\"}");
            TestWsClient.Frame top = a.await(ProtocolCodec.T_TOP_RESPONSE, 5000);
            assertNotNull(top);
            assertEquals("corr-7", top.rid);

            // Unknown types fail loudly instead of vanishing.
            a.sendRaw("{\"v\":1,\"t\":\"definitely_not_a_type\",\"d\":{},\"rid\":\"corr-9\"}");
            TestWsClient.Frame unknown = a.await(ProtocolCodec.T_ERROR, 5000);
            assertNotNull(unknown);
            assertEquals(ProtocolCodec.ERR_UNKNOWN_TYPE, unknown.as(ProtocolCodec.ErrorMsg.class).code);
            assertEquals("corr-9", unknown.rid);
        } finally {
            a.close();
        }
    }
}
