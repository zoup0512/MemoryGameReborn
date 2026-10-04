package com.memorygames.pk.admin;

import com.memorygames.pk.config.PkProperties;
import com.memorygames.pk.match.MatchManager;
import com.memorygames.pk.match.Matchmaker;
import com.memorygames.pk.season.SeasonService;
import com.memorygames.pk.user.UserAccount;
import com.memorygames.pk.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only operational snapshot (admin design 4.1). */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminGuard guard;
    private final MatchManager manager;
    private final Matchmaker matchmaker;
    private final UserRepository users;
    private final SessionStats sessions;
    private final EventRing events;
    private final AdminMetrics metrics;
    private final SeasonService seasons;
    private final PkProperties properties;
    private final Optional<BuildProperties> build;
    private final long startedAt = System.currentTimeMillis();
    private volatile String cachedJarMd5;

    public AdminDashboardController(AdminGuard guard, MatchManager manager, Matchmaker matchmaker,
                                    UserRepository users, SessionStats sessions, EventRing events,
                                    AdminMetrics metrics, SeasonService seasons, PkProperties properties,
                                    Optional<BuildProperties> build) {
        this.guard = guard;
        this.manager = manager;
        this.matchmaker = matchmaker;
        this.users = users;
        this.sessions = sessions;
        this.events = events;
        this.metrics = metrics;
        this.seasons = seasons;
        this.properties = properties;
        this.build = build;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(HttpServletRequest request) {
        ResponseEntity<String> denied = this.guard.deny(request);
        if (denied != null) {
            return denied;
        }
        Map<String, Object> view = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("version", this.build.map(BuildProperties::getVersion).orElse("dev"));
        server.put("startedAt", this.startedAt);
        server.put("upSeconds", (System.currentTimeMillis() - this.startedAt) / 1000);
        server.put("jarMd5", jarMd5());
        view.put("server", server);

        Map<String, Object> online = new LinkedHashMap<>();
        online.put("wsConnections", this.sessions.connections.get());
        online.put("authenticatedUsers", this.sessions.authenticated.get());
        view.put("online", online);

        Map<String, Object> matchmaking = new LinkedHashMap<>();
        matchmaking.put("queueDepth", this.matchmaker.queueSnapshot().size());
        matchmaking.put("queued", this.matchmaker.queueSnapshot());
        view.put("matchmaking", matchmaking);

        Map<String, Object> matches = new LinkedHashMap<>();
        List<com.memorygames.pk.match.MatchRoom.Snapshot> active = this.manager.activeSnapshots();
        matches.put("active", active.size());
        matches.put("list", active);
        view.put("matches", matches);

        Map<String, Object> economy = new LinkedHashMap<>();
        economy.put("entryFee", this.properties.getEconomy().getEntryFee());
        economy.put("winCoins", this.properties.getEconomy().getWinCoins());
        economy.put("drawCoins", this.properties.getEconomy().getDrawCoins());
        economy.put("balanceTop", this.users.findTop5ByOrderByBalanceDesc().stream()
                .map(u -> u.nickname + "=" + u.balance).toList());
        view.put("economy", economy);

        SeasonService.Snapshot season = this.seasons.snapshot();
        Map<String, Object> seasonView = new LinkedHashMap<>();
        seasonView.put("index", season.index);
        seasonView.put("endsAt", season.endAt);
        seasonView.put("daysLeft", season.daysLeft());
        view.put("season", seasonView);

        Map<String, Object> anticheat = new LinkedHashMap<>();
        anticheat.put("restrictedNow", this.users.countByRestrictedMatchesGreaterThan(0));
        anticheat.put("bannedNow", this.users.countByBannedUntilGreaterThan(System.currentTimeMillis()));
        view.put("anticheat", anticheat);

        view.put("counters", this.metrics.snapshot());

        List<EventRing.Event> recent = this.events.recent(20);
        view.put("eventsCount", this.events.size());
        view.put("events", recent.stream().map(EventRing.Event::format).toList());
        return ResponseEntity.ok(view);
    }

    private String jarMd5() {
        if (this.cachedJarMd5 != null) {
            return this.cachedJarMd5;
        }
        try {
            File jar = new File(AdminDashboardController.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!jar.isFile()) {
                return "unknown";
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream in = java.nio.file.Files.newInputStream(jar.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format("%02x", b));
            }
            this.cachedJarMd5 = out.toString();
            return this.cachedJarMd5;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
