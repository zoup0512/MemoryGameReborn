package com.cube.memorygames.api.online;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class OnlineRatingCopyTest {
    private static final Pattern ALGORITHM_NAME = Pattern.compile("\\belo\\b", Pattern.CASE_INSENSITIVE);

    @Test public void translatedUiStringsDoNotExposeTheAlgorithmName() throws Exception {
        File resources = new File("src/main/res");
        File[] directories = resources.listFiles(file -> file.isDirectory() && file.getName().startsWith("values"));
        assertNotNull(directories);
        int checked = 0;
        for (File directory : directories) {
            File[] files = directory.listFiles(file -> file.getName().endsWith(".xml"));
            assertNotNull(files);
            for (File file : files) {
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
                for (String tag : new String[]{"string", "plurals", "string-array"}) {
                    NodeList entries = document.getElementsByTagName(tag);
                    for (int i = 0; i < entries.getLength(); i++) {
                        Element entry = (Element) entries.item(i);
                        assertFalse(file + ": " + entry.getAttribute("name"),
                                ALGORITHM_NAME.matcher(entry.getTextContent()).find());
                        checked++;
                    }
                }
            }
        }
        assertTrue("No UI strings were checked", checked > 0);
    }

    @Test public void accountAndAccessibilityFormatsUseTheLocalizedRatingLabel() throws Exception {
        Document defaults = readStrings("values");
        for (String locale : new String[]{"values", "values-zh"}) {
            String label = string(readStrings(locale), "online_elo");
            assertFalse(ALGORITHM_NAME.matcher(label).find());
            assertEquals(label + " 1200", String.format(Locale.ROOT,
                    string(defaults, "online_account_rating"), label, 1200));
            assertEquals(label + ": 1200", String.format(Locale.ROOT,
                    string(defaults, "online_ranking_rating_accessibility"), label, 1200));
        }
        assertEquals("排位分", string(readStrings("values-zh"), "online_elo"));
    }

    private static Document readStrings(String directory) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/" + directory + "/strings.xml"));
    }

    private static String string(Document document, String name) {
        NodeList entries = document.getElementsByTagName("string");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (name.equals(entry.getAttribute("name"))) return entry.getTextContent();
        }
        throw new AssertionError("Missing string: " + name);
    }
}
