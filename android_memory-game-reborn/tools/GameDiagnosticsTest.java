import com.cube.memorygames.diagnostics.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** No Android runtime or Gradle: correlation, privacy and snapshot wire budgets. */
public final class GameDiagnosticsTest {
    private static int checks;
    private static final String RUN = "12345678-1234-1234-1234-123456789abc";
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static DiagnosticLogStore.Snapshot freeze(DiagnosticLogStore store) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        DiagnosticLogStore.Snapshot[] result = new DiagnosticLogStore.Snapshot[1];
        Exception[] error = new Exception[1];
        store.freeze((s, e) -> { result[0] = s; error[0] = e; latch.countDown(); });
        check(latch.await(5, TimeUnit.SECONDS), "freeze completion");
        if (error[0] != null) throw error[0];
        return result[0];
    }
    public static void main(String[] args) throws Exception {
        List<String> records = new ArrayList<>();
        long[] now = {100};
        String owningMatch = "12345678-1234-1234-1234-123456789abc";
        GameDiagnostics.Sink sink = (event, fields) -> {
            check(fields.length <= 40, "20 pair budget");
            String record = DiagnosticSanitizer.record(1, records.size(), RUN, event, fields);
            check(record != null && record.getBytes(StandardCharsets.UTF_8).length < 2048, "event byte budget");
            records.add(record);
        };
        GameDiagnostics game = new GameDiagnostics(true, true, owningMatch, "5", 2, "PARTY", () -> now[0], sink);
        game.lifecycle("create", "framework");
        String id = records.get(0).replaceAll(".*\"activityId\":\"([0-9a-f]{16})\".*", "$1").trim();
        check(id.matches("[0-9a-f]{16}"), "activity id format");
        String hash = GameDiagnostics.fingerprint(2, 2, new int[]{0, 123, 456, 0});
        check(hash.matches("[0-9a-f]{16}"), "keyed fingerprint format");
        check(hash.equals(GameDiagnostics.fingerprint(2, 2, new int[]{0, 123, 456, 0})), "same layout same process");
        check(!hash.equals(GameDiagnostics.fingerprint(2, 2, new int[]{0, 456, 123, 0})), "positions included");
        check(!hash.equals(GameDiagnostics.fingerprint(2, 2, new int[]{0, 124, 456, 0})), "resource identity included");
        check(!hash.equals(GameDiagnostics.fingerprint(1, 4, new int[]{0, 123, 456, 0})), "dimensions included");
        game.beginQuestion(1); game.generated(hash);
        int[] executed = {0};
        Runnable old = game.callback("hide_cells", 200, () -> {
            executed[0]++;
            game.questionEvent("question_transition", "intent", "next_level");
        });
        check(records.get(records.size() - 1).contains("\"targetQuestion\":1"), "schedule captures question");
        game.beginQuestion(1); game.generated(hash); // same level and same layout still a NEW generation
        check(records.get(records.size() - 1).contains("\"question\":2"), "duplicate generation increments ordinal");
        now[0] = 350; old.run();
        String callback = records.get(records.size() - 2), transition = records.get(records.size() - 1);
        check(executed[0] == 1, "old callback not fixed or dropped by diagnostics");
        check(callback.contains("\"targetQuestion\":1") && callback.contains("\"question\":2"), "old callback versus current question");
        check(callback.contains("\"elapsedMs\":350"), "synchronous monotonic clock capture");
        check(transition.contains("\"callbackId\":1"), "transition linked to triggering callback");
        GameDiagnostics.Callback flow = game.scheduled("flow_advance", 1000, 2, 3, 0);
        game.executed(flow, false);
        check(records.get(records.size() - 1).contains("\"reason\":\"stale_generation\""), "existing gate skip logged");
        game.lifecycle("destroy", "finish");
        for (String record : records) {
            check(record.contains(id), "stable instance identity");
            check(record.contains(owningMatch), "immutable UUID owning match");
        }
        GameDiagnostics another = new GameDiagnostics(true, false, owningMatch, "5", 2, "PARTY", () -> 1, sink);
        another.lifecycle("recreate", "framework");
        check(!records.get(records.size() - 1).contains(id), "recreated instance new id");
        int size = records.size(); another.beginQuestion(1); another.generated(hash); another.callback("hide_cells", 1, () -> {}).run();
        check(records.size() == size, "non-detailed games lifecycle only");
        GameDiagnostics disabled = new GameDiagnostics(false, true, owningMatch, "5", 2, "PARTY", () -> 1, sink);
        disabled.lifecycle("create", "framework"); disabled.beginQuestion(1); disabled.generated(hash);
        check(records.size() == size, "offline not logged");
        GameDiagnostics broken = new GameDiagnostics(true, true, owningMatch, "5", 2, "PARTY", () -> 1,
                (event, fields) -> { throw new IllegalStateException(); });
        broken.beginQuestion(1); broken.callback("hide_cells", 1, () -> executed[0]++).run();
        check(executed[0] == 2, "sink failure cannot prevent original callback");
        String privateRecord = DiagnosticSanitizer.record(1, 1, RUN, "question_answer",
                "seed", 123456789L, "rawSeed", "SECRET_SEED", "rightAnswer", 99, "selected", "SECRET_INPUT",
                "questionText", "SECRET_QUESTION", "options", "SECRET_OPTIONS", "answer", "SECRET_ANSWER",
                "phase", "wrong", "reason", "answer_wrong", "fingerprint", hash);
        check(!privateRecord.contains("SECRET") && !privateRecord.contains("\"seed\"")
                && !privateRecord.contains("\"rawSeed\"") && !privateRecord.contains("\"rightAnswer\"")
                && !privateRecord.contains("\"selected\"") && !privateRecord.contains("\"questionText\"")
                && !privateRecord.contains("\"options\"") && !privateRecord.contains("\"answer\""),
                "no raw seed/content/answer/selected");
        check(privateRecord.contains("answer_wrong"), "only correctness outcome retained");
        for (String key : Arrays.asList("activityId", "fingerprint", "phase", "reason")) {
            String bad = DiagnosticSanitizer.record(1, 1, RUN, "question_callback", key, "RAW_UNTRUSTED");
            check(!bad.contains(key), "strict code/hash rejection " + key);
        }
        check(!DiagnosticSanitizer.record(1, 1, RUN, "question_callback", "reason", "java.lang.IllegalStateException").contains("reason"), "game reasons strictly closed");
        Object[] maximum = new Object[42];
        Arrays.fill(maximum, "SECRET");
        for (int i = 0; i < 40; i += 2) { maximum[i] = "unknown" + i; maximum[i + 1] = 1; }
        maximum[40] = "callbackId"; maximum[41] = 123;
        check(!DiagnosticSanitizer.record(1, 1, RUN, "question_callback", maximum).contains("callbackId"), "40 parameter bound unchanged");

        Path dir = Files.createTempDirectory("game-diagnostics");
        DiagnosticLogStore store = new DiagnosticLogStore(dir.toFile());
        try {
            check(store.flush(3000), "initialize store");
            // Compact complete NDJSON fixtures exercise the line gate independently of the byte gate.
            String compact = "{\"ts\":" + System.currentTimeMillis() + ",\"event\":\"fatal\"}\n";
            StringBuilder history = new StringBuilder();
            for (int i = 0; i < 2600; i++) history.append(compact);
            Files.write(dir.resolve("events-0.ndjson"), history.toString().getBytes(StandardCharsets.UTF_8));
            store.log("game_activity", "matchId", owningMatch, "phase", "destroy");
            check(store.flush(3000), "flush UUID");
            DiagnosticLogStore.Snapshot snapshot = freeze(store);
            check(snapshot.records == 2047, "snapshot bounded to newest 2047 complete records");
            check(snapshot.content.split("\n", -1).length == 2048, "server split including trailing newline budget");
            check(snapshot.bytes <= DiagnosticLogStore.SNAPSHOT_BYTES, "existing byte budget retained");
            check(owningMatch.equals(snapshot.matchId), "Party UUID snapshot associated");
            String upload = snapshot.uploadId;
            store.close(); store = new DiagnosticLogStore(dir.toFile());
            DiagnosticLogStore.Snapshot retry = freeze(store);
            check(upload.equals(retry.uploadId) && retry.records == 2047 && owningMatch.equals(retry.matchId), "pending UUID budget survives restart");
            store.close();
            // Simulate a previously persisted snapshot exceeding the newly enforced line budget.
            try (RandomAccessFile file = new RandomAccessFile(dir.resolve("pending.bin").toFile(), "rw")) {
                file.readInt(); file.readUTF(); file.readUTF(); file.readLong(); file.readLong(); file.writeInt(2048);
            }
            store = new DiagnosticLogStore(dir.toFile());
            DiagnosticLogStore.Snapshot repaired = freeze(store);
            check(!upload.equals(repaired.uploadId) && repaired.records <= 2047,
                    "oversized legacy pending replaced with valid bounded snapshot");
        } finally {
            store.close();
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { throw new RuntimeException(e); } });
            }
        }
        System.out.println("PASS game diagnostics: " + checks + " assertions");
    }
}
