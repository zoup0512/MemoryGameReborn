package com.cube.memorygames.api.online;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class MatchResultsPresentationTest {
    private String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/cube/memorygames/api/online/OnlineMatchActivity.java")), StandardCharsets.UTF_8);
    }

    @Test public void settlementImmediatelyIncludesRoundResultsWithoutConfirmation() throws Exception {
        String source = source();
        int start = source.indexOf("private void showSettlementUi(");
        int end = source.indexOf("// ---------------------------------------------------------- VS header", start);
        String settlement = source.substring(start, end);
        int results = settlement.indexOf("this.roundResults.addView(buildResultsBoard(end)");
        int show = settlement.indexOf("showScrollableBoard(this.card, ViewGroup.LayoutParams.MATCH_PARENT)");
        assertTrue(results >= 0);
        assertTrue(show > results);
        assertTrue(settlement.indexOf("this.roundResults.removeAllViews()") < results);
        assertTrue(settlement.indexOf("setOnClickListener") > show);
        assertFalse(source.contains("okButton"));
        assertTrue(settlement.contains("this.bottomButton.setText(R.string.nextDuel)"));
        assertTrue(settlement.contains("end.coinDelta"));
        assertTrue(settlement.contains("end.eloDelta"));
    }

    @Test public void matchCompletionStillAutomaticallyOpensSettlementAndPreservesReturnGuard() throws Exception {
        String source = source();
        int start = source.indexOf("public void onMatchEnd(");
        int end = source.indexOf("public void onRoomCreated(", start);
        assertTrue(source.substring(start, end).contains("showSettlementUi(end)"));
        start = source.indexOf("protected void onActivityResult(");
        end = source.indexOf("public void onStateChanged(", start);
        String returning = source.substring(start, end);
        assertTrue(returning.contains("getMatchId() == null"));
    }
}
