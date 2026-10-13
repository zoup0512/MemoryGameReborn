package com.memorygames.pk;

import com.memorygames.pk.match.SettlementService;
import com.memorygames.pk.season.Season;
import com.memorygames.pk.season.SeasonRepository;
import com.memorygames.pk.season.SeasonService;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import com.memorygames.pk.user.UserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL-backed concurrency tests (P2 set), opt-in via the {@code docker}
 * tag because they need a container runtime: {@code ./gradlew pgTest}.
 * These also exercise the Flyway migrations and pessimistic locks on the
 * real database engine instead of H2.
 */
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = "pk.economy.welcome-coins=100")
class PgConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    UserService users;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SettlementService settlement;

    @Autowired
    SeasonService seasons;

    @Autowired
    SeasonRepository seasonRepository;

    private UserAccount newUser(String nickname) {
        return this.users.register(UUID.randomUUID().toString(), nickname);
    }

    private SettlementService.Command command(String matchId, UserAccount a, UserAccount b) {
        SettlementService.Command cmd = new SettlementService.Command();
        cmd.matchId = matchId;
        cmd.mode = "RANKED";
        cmd.practice = false;
        cmd.voided = false;
        cmd.entryCharged = true;
        cmd.entryFee = 30;
        cmd.createdAt = System.currentTimeMillis();
        cmd.botElo = 1000;
        for (int i = 0; i < 2; i++) {
            SettlementService.SeatPlan plan = new SettlementService.SeatPlan();
            plan.userId = i == 0 ? a.id : b.id;
            plan.nickname = i == 0 ? a.nickname : b.nickname;
            plan.score = i == 0 ? 100 : 40;
            plan.result = i == 0 ? "W" : "L";
            plan.eloDelta = i == 0 ? 16 : -16;
            plan.coinDelta = i == 0 ? 50 : 0;
            plan.win = i == 0;
            cmd.seats[i] = plan;
        }
        return cmd;
    }

    @Test
    @Timeout(60)
    void settlementReplayAppliesExactlyOnce() {
        UserAccount a = newUser("PgA");
        UserAccount b = newUser("PgB");
        SettlementService.Command cmd = command("pgreplay1", a, b);

        SettlementService.Result first = this.settlement.settle(cmd);
        assertEquals(100 + 50, first.seats[0].balance, "entry refund not involved: 100 + win");

        // A replay of the identical command must be a no-op.
        SettlementService.Result replay = this.settlement.settle(cmd);
        assertTrue(replay.alreadySettled, "the unique(match_id) key makes replays idempotent");
        assertEquals(first.seats[0].balance, replay.seats[0].balance);
        assertEquals(a.elo + 16, this.userRepository.findById(a.id).orElseThrow().elo);
        assertEquals(1, this.userRepository.findById(a.id).orElseThrow().wins);
    }

    @Test
    @Timeout(60)
    void concurrentDuplicateSettlementSingleWinner() throws Exception {
        UserAccount a = newUser("PgC");
        UserAccount b = newUser("PgD");
        SettlementService.Command cmd = command("pgrace1", a, b);

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        SettlementService.Result result = this.settlement.settle(cmd);
                        if (result.alreadySettled) {
                            rejected.incrementAndGet();
                        } else {
                            applied.incrementAndGet();
                        }
                    } catch (DataIntegrityViolationException lost) {
                        rejected.incrementAndGet(); // lost the unique-index race
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
        assertEquals(1, applied.get(), "exactly one settlement applies");
        assertEquals(threads - 1, rejected.get());
        UserAccount reloaded = this.userRepository.findById(a.id).orElseThrow();
        assertEquals(1, reloaded.wins, "W counted exactly once");
        assertEquals(100 + 50, reloaded.balance, "coins applied exactly once");
    }

    @Test
    @Timeout(60)
    void seasonRolloverUnderConcurrencyKeepsSingleActiveSeason() throws Exception {
        // Force an expired active season.
        assertNotNull(this.seasons.snapshot(), "season service responsive");
        Season current = this.seasonRepository.findFirstByActiveFlagOrderByIdDesc(1).orElse(null);
        assertNotNull(current, "an active season exists");
        current.endAt = System.currentTimeMillis() - 1000;
        this.seasonRepository.save(current);

        UserAccount rich = newUser("PgE");
        rich.elo = 2000;
        this.userRepository.save(rich);

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(this.seasons::activeSeason);
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        List<Season> active = this.seasonRepository.findByActiveFlag(1);
        assertEquals(1, active.size(), "exactly one active season survives the rollover race");
        UserAccount after = this.userRepository.findById(rich.id).orElseThrow();
        assertEquals(1500, after.elo, "soft reset applied exactly once (2000 -> 1500)");
    }
}
