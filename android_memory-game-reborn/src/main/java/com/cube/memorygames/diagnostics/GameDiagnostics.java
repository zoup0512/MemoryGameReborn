package com.cube.memorygames.diagnostics;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/** Pure Java, fail-open correlation only. Never accepts a seed, answer or option value. */
public final class GameDiagnostics {
    public static final String EXTRA_MATCH = "diagnosticOwningMatch";
    public static final String EXTRA_MODE = "diagnosticOwningMode";
    public interface Clock { long elapsedMs(); }
    public interface Sink { void event(String event, Object... fields); }
    private final boolean enabled, detailed;
    private final String activityId, matchId, gameId, mode;
    private final int round;
    private final Clock clock;
    private final Sink sink;
    private long question, callbackId, executingCallbackId;
    private int level;
    private String fingerprint;

    public GameDiagnostics(boolean enabled, boolean detailed, String matchId, String gameId,
                           int round, String mode, Clock clock, Sink sink) {
        this.enabled = enabled;
        this.detailed = enabled && detailed;
        this.matchId = matchId;
        this.gameId = gameId;
        this.round = round;
        this.mode = mode;
        this.clock = clock;
        this.sink = sink;
        this.activityId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public boolean detailed() { return detailed; }
    public void beginQuestion(int level) {
        if (!detailed) return;
        ++question;
        this.level = level;
        fingerprint = null;
    }
    public void generated(String hash) {
        if (!detailed) return;
        fingerprint = hash;
        questionEvent("question_generated", "generated", "count_all");
    }
    public void questionEvent(String event, String phase, String reason) {
        if (detailed) event(event, "phase", phase, "reason", reason, "callbackId", executingCallbackId);
    }
    public void lifecycle(String phase, String reason) {
        event("game_activity", "phase", phase, "reason", reason);
    }
    public void event(String event, Object... extra) {
        if (!enabled) return;
        try {
            // Capture at the call site, not when the asynchronous log worker dequeues it.
            long elapsed = clock.elapsedMs();
            Object[] common = {"matchId", matchId, "gameId", gameId, "round", round,
                    "mode", mode, "activityId", activityId, "question", question,
                    "level", level, "fingerprint", fingerprint, "elapsedMs", elapsed};
            Object[] fields = Arrays.copyOf(common, Math.min(40, common.length + extra.length));
            System.arraycopy(extra, 0, fields, common.length, fields.length - common.length);
            sink.event(event, fields);
        } catch (RuntimeException ignored) { }
    }

    public static final class Callback {
        private final long id, question, delay, run;
        private final int step, generation;
        private final String reason;
        private Callback(long id, long question, String reason, long delay, long run, int step, int generation) {
            this.id = id; this.question = question; this.reason = reason; this.delay = delay;
            this.run = run; this.step = step; this.generation = generation;
        }
    }
    public Callback scheduled(String reason, long delay, long run, int step, int generation) {
        if (!detailed) return null;
        Callback callback = new Callback(++callbackId, question, reason, delay, run, step, generation);
        callbackEvent(callback, "schedule", reason);
        return callback;
    }
    public void executed(Callback callback, boolean accepted) {
        if (callback != null) callbackEvent(callback, accepted ? "execute" : "skipped",
                accepted ? callback.reason : "stale_generation");
    }
    private void callbackEvent(Callback callback, String phase, String reason) {
        event("question_callback", "phase", phase, "reason", reason,
                "targetQuestion", callback.question, "callbackId", callback.id, "delayMs", callback.delay,
                "flowRun", callback.run, "flowStep", callback.step, "generation", callback.generation);
    }
    /** Wraps observation only: the original callback still runs even for an old question. */
    public Runnable callback(String reason, long delay, Runnable task) {
        if (!detailed) return task;
        final Callback captured = scheduled(reason, delay, 0, 0, 0);
        return () -> {
            executed(captured, true);
            long previous = executingCallbackId;
            executingCallbackId = captured.id;
            try { task.run(); } finally { executingCallbackId = previous; }
        };
    }
    public void flow(String event, String phase, long run, int step, int generation) {
        if (detailed) event(event, "phase", phase, "flowRun", run, "flowStep", step, "generation", generation);
    }

    private static final class FingerprintKey {
        // Independent of GameRandom. Process-local and never persisted or logged.
        static final byte[] KEY = create();
        private static byte[] create() {
            try { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return key; }
            catch (RuntimeException ignored) { return null; }
        }
    }
    /** HMAC-SHA256 using MessageDigest, truncated to 64 bits. Cells are ordered resource identities. */
    public static String fingerprint(int columns, int rows, int[] cells) {
        try {
            byte[] key = FingerprintKey.KEY;
            if (key == null || cells == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inner = new byte[64], outer = new byte[64];
            Arrays.fill(inner, (byte) 0x36); Arrays.fill(outer, (byte) 0x5c);
            for (int i = 0; i < key.length; i++) { inner[i] ^= key[i]; outer[i] ^= key[i]; }
            digest.update(inner);
            update(digest, 1); // canonical layout schema
            update(digest, columns); update(digest, rows); update(digest, cells.length);
            for (int cell : cells) update(digest, cell);
            byte[] content = digest.digest();
            digest.update(outer);
            byte[] hash = digest.digest(content);
            char[] hex = "0123456789abcdef".toCharArray(), result = new char[16];
            for (int i = 0; i < 8; i++) { result[i * 2] = hex[(hash[i] & 255) >>> 4]; result[i * 2 + 1] = hex[hash[i] & 15]; }
            return new String(result);
        } catch (java.security.NoSuchAlgorithmException | RuntimeException ignored) { return null; }
    }
    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24)); digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8)); digest.update((byte) value);
    }
}
