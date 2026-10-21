package com.cube.memorygames.api.online;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class OnlineHudLocalizationTest {
    @Test public void duelLabelsAreLocalizedAndKeepOpponentBeforeSelf() throws Exception {
        assertEquals("OPP 80 : 120 ME", format("values", "strings_online_hud.xml", "online_hud_score", 80, 120));
        assertEquals("对手 80 : 120 我", format("values-zh", "strings_online_hud.xml", "online_hud_score", 80, 120));
        assertEquals("对手 0 : 0 我", format("values-zh", "strings_online_hud.xml", "online_hud_score", 0, 0));
    }

    @Test public void partySeparatesRoundScoreFromTotalPointsAndOffersStandings() throws Exception {
        assertEquals("本轮 120 分\n总积分 6 · 排行 ›",
                format("values-zh", "strings_party.xml", "party_hud", 120, PartyState.points(12)));
        assertEquals("本轮 0 分\n总积分 2.5 · 排行 ›",
                format("values-zh", "strings_party.xml", "party_hud", 0, PartyState.points(5)));
        assertEquals("Round score 120\nTotal pts 2.5 · Standings ›",
                format("values", "strings_party.xml", "party_hud", 120, PartyState.points(5)));
    }

    @Test public void standingsIdentifyTheCurrentPlayerWithoutChangingTheirNickname() throws Exception {
        assertEquals("小明（我）", format("values-zh", "strings_party.xml", "party_self_name", "小明"));
        assertEquals("Alice (You)", format("values", "strings_party.xml", "party_self_name", "Alice"));
        String source = source("PartyUi.java");
        assertTrue(source.contains("p.playerId == PartySession.get().userId()"));
        assertTrue(source.contains("c.getString(R.string.party_self_name, p.nickname) : p.nickname"));
        assertTrue(source.contains("if (self) row.setTypeface(OnlineUi.medium(c))"));
    }

    @Test public void hudUsesLocalizedScoresAndKeepsPartyStandingsClickable() throws Exception {
        String source = source("OnlineHud.java");
        assertTrue(source.contains("getString(R.string.online_hud_score, oppScore, myScore)"));
        assertFalse(source.contains("\"OPP \""));
        assertTrue(source.contains("strip.setOnClickListener(v -> PartyUi.showStandings(activity))"));
        assertTrue(source.contains("strip.setMaxLines(2)"));
        assertTrue(source.contains("activity.getString(R.string.party_hud, p.score, PartyState.points(p.points2))"));
    }

    private static String format(String directory, String file, String key, Object... args) throws Exception {
        NodeList entries = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/" + directory + "/" + file)).getElementsByTagName("string");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (key.equals(entry.getAttribute("name"))) {
                return String.format(Locale.ROOT, entry.getTextContent().replace("\\n", "\n"), args);
            }
        }
        throw new AssertionError("Missing string: " + key);
    }

    private static String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/cube/memorygames/api/online/", file)),
                StandardCharsets.UTF_8);
    }
}
