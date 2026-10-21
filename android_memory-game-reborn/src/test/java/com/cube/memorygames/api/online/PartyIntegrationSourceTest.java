package com.cube.memorygames.api.online;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Guard integration seams that plain JVM tests cannot instantiate without an Android runtime. */
public class PartyIntegrationSourceTest {
    private String source(String relative) throws Exception {
        Path p = Paths.get("src/main/java/com/cube/memorygames", relative);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
    @Test public void countAllWrongOptionReportsFalseThroughSharedDialogEntry() throws Exception {
        String countAll = source("games/Game5CountAllActivity.java");
        assertTrue(countAll.contains("showFailureDialog()"));
        String base = source("games/Game1MemoryGridActivity.java");
        int dialog = base.indexOf("protected void showFailureDialog()");
        int next = base.indexOf("protected void showAppropriateDialog()", dialog);
        assertTrue(base.substring(dialog, next).contains("reportLevel(this, progression.getLevelNumber(), onlineScore, false)"));
        assertTrue(base.contains("reportLevel(this, level, this.onlineScore, true)"));
    }
    @Test public void terminalAuthenticationDetachesPartyBeforeDisconnect() throws Exception {
        String source = source("api/online/OnlineSession.java");
        int start = source.indexOf("private void stopAuthorization(");
        int end = source.indexOf("private void handleDisconnected", start);
        String stop = source.substring(start, end);
        assertTrue(stop.indexOf("PartySession.get().reset()") < stop.indexOf("disconnect()"));
        assertTrue(stop.contains("reconnectBlocked=true"));
    }
    @Test public void disconnectedHistoryIsReplayedAfterAuthentication() throws Exception {
        String source = source("api/online/PartySession.java");
        assertTrue(source.contains("if (!connected()) { pendingHistory = true; sync(); return; }"));
        assertTrue(source.contains("if (pendingHistory) { pendingHistory = false; requestHistory(); }"));
    }
    @Test public void sixRealGamesUseOneHeldFlowAndAllThreePrivateTimersAreGated() throws Exception {
        String base = source("games/Game1MemoryGridActivity.java");
        assertTrue(base.contains("if (partyWaiting) this.stateTimer.setHeld(true)"));
        assertTrue(base.contains("partyBridge.attach()"));
        for (String game : new String[]{"Game16LikePreviousActivity", "Game20ColorsActivity", "Game23TableActivity"}) {
            String s = source("games/" + game + ".java");
            assertTrue(s.contains("!isPartyWaiting() && this.startedTime <= 0"));
            assertTrue(s.contains("protected void onPartyClockStart()"));
            assertTrue(s.contains("PartySession.get().remaining()"));
        }
    }
}
