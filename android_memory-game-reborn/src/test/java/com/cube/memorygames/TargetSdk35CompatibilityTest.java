package com.cube.memorygames;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Guards the API 31-35 requirements that only fail on newer devices. */
public class TargetSdk35CompatibilityTest {
    @Test public void targetSdkIs35() throws Exception {
        assertTrue(text("build.gradle").contains("targetSdkVersion 35"));
    }

    @Test public void componentsWithIntentFiltersDeclareExported() throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        org.w3c.dom.Document manifest = factory.newDocumentBuilder()
                .parse(Paths.get("src/main/AndroidManifest.xml").toFile());
        org.w3c.dom.Element splash = component(manifest, "activity", "SplashActivity");
        assertEquals("true", splash.getAttributeNS(NS, "exported"));
        org.w3c.dom.Element boot = component(manifest, "receiver", "DeviceBootReceiver");
        assertEquals("false", boot.getAttributeNS(NS, "exported"));
        assertFalse(boot.getElementsByTagName("intent-filter").getLength() == 0);
    }

    @Test public void pendingIntentsAreImmutable() throws Exception {
        java.util.regex.Pattern call = java.util.regex.Pattern.compile(
                "PendingIntent\\.get\\w+\\((?:[^;])*?\\);", java.util.regex.Pattern.DOTALL);
        for (String file : new String[] {
                "src/main/java/com/cube/memorygames/NotificationManager.java",
                "src/main/java/com/cube/memorygames/reminder/AppReminder.java"}) {
            java.util.regex.Matcher matcher = call.matcher(text(file));
            int count = 0;
            while (matcher.find()) {
                count++;
                assertTrue(file + ": " + matcher.group(), matcher.group().contains("FLAG_IMMUTABLE"));
            }
            assertTrue(file + " has pending intents", count > 0);
        }
    }

    @Test public void dynamicReceiverUsesExportFlag() throws Exception {
        String dispatcher = text("src/main/java/com/squareup/picasso/Dispatcher.java");
        assertTrue(dispatcher.contains("ContextCompat.registerReceiver"));
        assertTrue(dispatcher.contains("ContextCompat.RECEIVER_NOT_EXPORTED"));
    }

    @Test public void api35ThemeKeepsLegacyWindowFitting() throws Exception {
        assertTrue(text("src/main/res/values-v35/styles.xml")
                .contains("windowOptOutEdgeToEdgeEnforcement"));
        assertTrue(text("src/main/res/values/styles.xml").contains("GdxThemeBase"));
    }

    private static final String NS = "http://schemas.android.com/apk/res/android";

    private static org.w3c.dom.Element component(org.w3c.dom.Document manifest,
                                                 String tag, String nameSuffix) {
        org.w3c.dom.NodeList nodes = manifest.getElementsByTagName(tag);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) nodes.item(i);
            String name = element.getAttributeNS(NS, "name");
            names.add(name);
            if (name.endsWith(nameSuffix)) {
                return element;
            }
        }
        throw new AssertionError("No <" + tag + "> ending with " + nameSuffix + ": " + names);
    }

    private static String text(String relative) throws Exception {
        Path path = Paths.get(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
