package com.cube.memorygames;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class AndroidXMigrationSourceTest {
    @Test public void productionSourcesAndLayoutsDoNotReferenceLegacySupportPackages() throws Exception {
        List<String> legacyFiles = new ArrayList<>();
        for (String directory : new String[] {"src/main", "src/debug"}) {
            try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".xml"))
                        .forEach(path -> {
                            try {
                                if (text(path).contains("android.support.")) legacyFiles.add(path.toString());
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        });
            }
        }
        assertTrue("Legacy Support Library references: " + legacyFiles, legacyFiles.isEmpty());
    }

    @Test public void androidXIsEnabledWithoutBinaryRewriting() throws Exception {
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(Paths.get("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("true", properties.getProperty("android.useAndroidX"));
        assertEquals("false", properties.getProperty("android.enableJetifier"));
        assertEquals("false", properties.getProperty("android.nonFinalResIds"));
    }

    @Test public void percentageLayoutsUseThePackagedAndroidXWidget() throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/res"))) {
            List<Path> layouts = new ArrayList<>();
            paths.filter(path -> path.getFileName().toString().equals("view_game12.xml")).forEach(layouts::add);
            assertEquals(3, layouts.size());
            for (Path layout : layouts) {
                assertTrue(layout.toString(), text(layout).contains("androidx.percentlayout.widget.PercentFrameLayout"));
            }
        }
    }

    @Test public void activityResultsReachAndroidXDispatch() throws Exception {
        String activity = text(Paths.get("src/main/java/com/cube/memorygames/api/online/OnlineMatchActivity.java"));
        assertTrue(activity.contains("super.onActivityResult(requestCode, resultCode, data);"));
    }

    @Test public void imageLayoutsUseAppCompatTint() throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/res"))) {
            List<Path> layouts = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                    .filter(path -> path.toString().endsWith(".xml"))
                    .forEach(layouts::add);
            for (Path layout : layouts) {
                assertFalse(layout.toString(), text(layout).contains("android:tint="));
            }
        }
    }

    private static String text(Path path) throws java.io.IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
