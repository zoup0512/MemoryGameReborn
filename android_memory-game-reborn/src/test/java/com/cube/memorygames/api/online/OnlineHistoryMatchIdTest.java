package com.cube.memorygames.api.online;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class OnlineHistoryMatchIdTest {
    @Test public void missingIdsCannotBeCopied() {
        assertNull(OnlineHistoryMatchId.copyableId(null));
        assertNull(OnlineHistoryMatchId.copyableId(""));
        assertNull(OnlineHistoryMatchId.copyableId(" \t\r\n "));
    }

    @Test public void identifiersAreNotTruncatedOrReformatted() {
        String id = "01234567-89ab-cdef-0123-456789abcdef-long-match-id";
        assertEquals(id, OnlineHistoryMatchId.copyableId(id));
        assertEquals("abcdef123456", OnlineHistoryMatchId.copyableId("abcdef123456"));
        assertEquals("  " + id + "  ", OnlineHistoryMatchId.copyableId("  " + id + "  "));
    }

    @Test public void allBundledLanguagesHaveHistoryCopyAndAccessibilityStrings() throws Exception {
        String id = "01234567-89ab-cdef-0123-456789abcdef";
        for (String directory : new String[]{"values", "values-de", "values-es", "values-fr",
                "values-pt", "values-ru", "values-uk", "values-zh"}) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new File("src/main/res/" + directory + "/strings.xml"));
            for (String key : new String[]{"online_history_match_id",
                    "online_history_copy_match_id_accessibility"}) {
                String format = string(document, key);
                assertTrue(directory + ": " + key, format.contains("%1$s"));
                assertTrue(String.format(Locale.ROOT, format, id).contains(id));
            }
            for (String key : new String[]{"online_history_copy_match_id",
                    "online_history_match_id_copied", "online_history_match_id_unavailable"}) {
                assertFalse(directory + ": " + key, string(document, key).trim().isEmpty());
            }
        }
    }

    @Test public void bothHistoryScreensUseTheSharedIdView() throws Exception {
        for (String activity : new String[]{"OnlineHistoryActivity", "PartyHistoryActivity"}) {
            String source = source("src/main/java/com/cube/memorygames/api/online/" + activity + ".java");
            assertTrue(activity, source.contains("card.addView(new OnlineHistoryMatchIdView(this, entry.matchId))"));
            assertFalse(activity + " must not duplicate clipboard handling", source.contains("setPrimaryClip"));
        }
    }

    @Test public void offlinePreviewIsRegisteredOnlyInDebugManifest() throws Exception {
        String name = "com.cube.memorygames.api.online.DebugHistoryPreviewActivity";
        assertTrue(source("src/debug/AndroidManifest.xml").contains(name));
        assertFalse(source("src/main/AndroidManifest.xml").contains(name));
        String preview = source("src/debug/java/com/cube/memorygames/api/online/DebugHistoryPreviewActivity.java");
        assertTrue(preview.contains("new OnlineHistoryMatchIdView(this, id)"));
        assertTrue(preview.contains("new EditText(this)"));
        assertFalse(preview.contains("OnlineSession"));
        assertFalse(preview.contains("PartySession"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String string(Document document, String name) {
        NodeList entries = document.getElementsByTagName("string");
        String value = null;
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (name.equals(entry.getAttribute("name"))) {
                assertNull("Duplicate string: " + name, value);
                value = entry.getTextContent();
            }
        }
        assertNotNull("Missing string: " + name, value);
        return value;
    }
}
