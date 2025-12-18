package com.viaversion.mappingsgenerator.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to compare generate a diff of translations between two Minecraft versions.
 * Copy the output into VB's translations file.
 */
final class TranslationMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationMapper.class.getSimpleName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(final String[] args) throws IOException {
        final String oldVer = "1.21.9";
        final String newVer = "1.21.11";

        final Map<String, String> oldTranslations = load(oldVer);
        final Set<String> oldValues = new HashSet<>(oldTranslations.values());
        final Map<String, String> newTranslations = load(newVer);

        final JsonObject diff = new JsonObject();
        for (final Map.Entry<String, String> entry : newTranslations.entrySet()) {
            if (oldTranslations.containsKey(entry.getKey())) {
                continue;
            }

            if (oldValues.contains(entry.getValue())) {
                LOGGER.warn("Changed value: {}", entry.getValue());
                continue;
            }

            diff.addProperty(entry.getKey(), entry.getValue());
        }

        // Check for removed translations
        for (final Map.Entry<String, String> entry : oldTranslations.entrySet()) {
            if (!newTranslations.containsKey(entry.getKey())) {
                //LOGGER.warn("mappings.put(\"" + entry.getKey() + "\", \"" + entry.getValue() + "\");");
            }
        }

        LOGGER.info(diff.toString());
        LOGGER.info("Mappings size: {}", diff.size());
    }

    private static Map<String, String> load(final String version) throws IOException {
        final File jarFile = PathUtil.minecraftDir().resolve("versions").resolve(version).resolve(version + ".jar").toFile();
        if (!jarFile.exists()) {
            throw new IllegalArgumentException("File " + jarFile + " does not exist");
        }

        final String contents;
        try (final ZipFile file = new ZipFile(jarFile)) {
            ZipEntry langEntry = file.getEntry("assets/minecraft/lang/en_us.json");
            if (langEntry == null) {
                // Pre 1.13 translations
                langEntry = file.getEntry("assets/minecraft/lang/en_us.lang");
                if (langEntry != null) {
                    try (final InputStream inputStream = file.getInputStream(langEntry)) {
                        contents = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    return loadLegacyTranslations(contents);
                }
                throw new IllegalArgumentException("File " + jarFile + " does not contain en_us.json");
            }

            try (final InputStream inputStream = file.getInputStream(langEntry)) {
                contents = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        final JsonObject object = GSON.fromJson(contents, JsonObject.class);
        final Map<String, String> translations = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().getAsString());
        }
        return translations;
    }

    private static Map<String, String> loadLegacyTranslations(final String contents) {
        final Map<String, String> translations = new LinkedHashMap<>();
        contents.lines().forEach(line -> {
            final int index = line.indexOf('=');
            if (index != -1) {
                translations.put(line.substring(0, index), line.substring(index + 1));
            }
        });
        return translations;
    }
}
