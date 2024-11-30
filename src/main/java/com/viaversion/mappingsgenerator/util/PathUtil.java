package com.viaversion.mappingsgenerator.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtil {

    public static Path minecraftDir() {
        // Windows path
        Path minecraftDir = Paths.get(home(), "AppData", "Roaming", ".minecraft");
        if (!Files.isDirectory(minecraftDir)) {
            // MacOS path
            minecraftDir = Paths.get(home(), "Library", "Application Support", "minecraft");
        }
        return minecraftDir;
    }

    private static String home() {
        return System.getProperty("user.home");
    }
}
