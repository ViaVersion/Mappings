/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023-2025 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.mappingsgenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.util.JsonConverter;
import com.viaversion.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RunContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunContext.class.getSimpleName());
    private final Map<String, Object2IntMap<String>> globalIdentifierMaps = new HashMap<>();
    private final Set<String> savedIdentifierFiles = new HashSet<>();
    private final int[] storageStrategyCounts = new int[MappingsOptimizer.IDENTITY_ID + 1];
    private final JsonObject globalIdentifiers;
    private final JsonObject fileHashes;
    private boolean globalIdentifiersUpdated;

    private RunContext(final JsonObject globalIdentifiers, final JsonObject fileHashes) {
        this.globalIdentifiers = globalIdentifiers;
        this.fileHashes = fileHashes;
    }

    /**
     * Loads the global identifier table and file hash data from disk.
     */
    public static RunContext load() throws IOException {
        final JsonObject globalIdentifiers = MappingsLoader.load(MappingsOptimizer.MAPPINGS_DIR, "identifier-table.json");
        final JsonObject fileHashes;
        try (final BufferedReader reader = Files.newBufferedReader(Path.of("output_hashes.json"))) {
            fileHashes = MappingsGenerator.GSON.fromJson(reader, JsonObject.class);
        }
        return new RunContext(globalIdentifiers, fileHashes);
    }

    /**
     * Returns the global identifiers array for the given key, creating it if not yet present.
     *
     * @param key registry key
     * @return global identifiers array
     */
    public JsonArray globalIdentifierArray(final String key) {
        JsonArray array = globalIdentifiers.getAsJsonArray(key);
        if (array == null) {
            array = new JsonArray();
            globalIdentifiers.add(key, array);
        }
        return array;
    }

    /**
     * Returns a cached identifier to global id lookup for the given key,
     * kept in sync with the array through {@link #addGlobalIdentifier(String, String)}.
     *
     * @param key registry key
     * @return identifier to global id lookup
     */
    public Object2IntMap<String> globalIdentifierMap(final String key) {
        return globalIdentifierMaps.computeIfAbsent(key, k -> MappingsLoader.arrayToMap(globalIdentifierArray(k)));
    }

    /**
     * Appends an identifier to the global identifier table.
     *
     * @param key        registry key
     * @param identifier identifier to add
     */
    public void addGlobalIdentifier(final String key, final String identifier) {
        final Object2IntMap<String> map = globalIdentifierMap(key);
        final JsonArray array = globalIdentifierArray(key);
        map.put(identifier, array.size());
        array.add(identifier);
        globalIdentifiersUpdated = true;
    }

    /**
     * Marks the identifier file of the given version as saved.
     *
     * @param version version of the identifier file
     * @return true if it had not been saved by another optimizer run yet
     */
    public boolean markIdentifierFileSaved(final String version) {
        return savedIdentifierFiles.add(version);
    }

    public void countStorageStrategy(final byte id) {
        storageStrategyCounts[id]++;
    }

    /**
     * Stores content hash and file size of an output file to keep track of changes.
     * The data is written to disk in {@link #finish()}.
     *
     * @param key  file key
     * @param path path of the written file
     */
    public void addFileData(final String key, final Path path) throws IOException {
        JsonObject fileData = fileHashes.getAsJsonObject(key);
        if (fileData == null) {
            fileData = new JsonObject();
            fileHashes.add(key, fileData);
        }

        // Hash the file contents
        final byte[] bytes = Files.readAllBytes(path);
        final CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        fileData.addProperty("object-hash", crc32.getValue());
        fileData.addProperty("size", bytes.length);
    }

    /**
     * Writes the global identifier table and file hash data collected over the run to disk.
     * Must be called once after all optimizer runs of a batch have finished.
     */
    public void finish() throws IOException {
        if (globalIdentifiersUpdated) {
            // Also keep a json file around for easier viewing
            MappingsOptimizer.writeJson(globalIdentifiers, MappingsOptimizer.MAPPINGS_DIR.resolve("identifier-table.json"));
            LOGGER.info("Updated global identifiers file");
            globalIdentifiersUpdated = false;
        }

        // Always create the nbt output file
        final Path outputPath = MappingsOptimizer.OUTPUT_DIR.resolve(MappingsOptimizer.OUTPUT_GLOBAL_IDENTIFIERS_FILE);
        final CompoundTag globalIdentifiersTag = (CompoundTag) JsonConverter.toTag(globalIdentifiers);
        MappingsOptimizer.write(globalIdentifiersTag, outputPath);
        addFileData("identifier-table", outputPath);

        MappingsOptimizer.writeJson(fileHashes, Path.of("output_hashes.json"));
    }

    public void printStats() {
        LOGGER.info("Storage format counts: direct={}, shifts={}, changes={}, identity={}",
            storageStrategyCounts[MappingsOptimizer.DIRECT_ID],
            storageStrategyCounts[MappingsOptimizer.SHIFTS_ID],
            storageStrategyCounts[MappingsOptimizer.CHANGES_ID],
            storageStrategyCounts[MappingsOptimizer.IDENTITY_ID]
        );

        long totalSize = 0;
        for (final Map.Entry<String, JsonElement> entry : fileHashes.entrySet()) {
            totalSize += entry.getValue().getAsJsonObject().getAsJsonPrimitive("size").getAsLong();
        }
        LOGGER.info("Total size of all mapping and identifier files: {}kb", totalSize / 1024);
    }
}
