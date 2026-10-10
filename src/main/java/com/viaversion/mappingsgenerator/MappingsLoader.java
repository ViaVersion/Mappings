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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MappingsLoader {

    public static final Logger LOGGER = LoggerFactory.getLogger(MappingsLoader.class.getSimpleName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static @Nullable JsonObject load(final String name) throws IOException {
        return load(name, JsonObject.class);
    }

    public static <T> @Nullable T load(final String name, final Class<T> outputClass) throws IOException {
        return load(MappingsOptimizer.MAPPINGS_DIR, name, outputClass);
    }

    /**
     * Loads and return the json mappings file.
     *
     * @param mappingsDir mappings directory
     * @param name        name of the mappings file
     * @return the mappings file as a JsonObject, or null if it does not exist
     */
    public static @Nullable JsonObject load(final Path mappingsDir, final String name) throws IOException {
        return load(mappingsDir, name, JsonObject.class);
    }

    public static <T> @Nullable T load(final Path mappingsDir, final String name, final Class<T> outputClass) throws IOException {
        final Path path = mappingsDir.resolve(name);
        if (!Files.exists(path)) {
            return null;
        }

        try (final BufferedReader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, outputClass);
        }
    }

    /**
     * Returns a mappings result with int to int array mappings.
     *
     * @param unmappedIdentifiers array of unmapped identifiers
     * @param mappedIdentifiers   array of mapped identifiers
     * @param diffIdentifiers     diff identifiers
     * @param errorStrategy       whether to warn on missing mappings
     * @return mappings result with int to int array mappings
     */
    public static MappingsResult map(final JsonArray unmappedIdentifiers, final JsonArray mappedIdentifiers, @Nullable final JsonObject diffIdentifiers, final ErrorStrategy errorStrategy) {
        return map(unmappedIdentifiers, new DiffMapper(MappingsLoader.arrayToMap(mappedIdentifiers), diffIdentifiers), errorStrategy);
    }

    /**
     * Returns a mappings result with int to int array mappings.
     *
     * @param unmappedIdentifiers array of unmapped identifiers
     * @param mapper              mapper for the target identifiers
     * @param errorStrategy       whether to warn on missing mappings
     * @return mappings result with int to int array mappings
     */
    static MappingsResult map(final JsonArray unmappedIdentifiers, final DiffMapper mapper, final ErrorStrategy errorStrategy) {
        final int[] output = new int[unmappedIdentifiers.size()];
        int emptyMappings = 0;
        int identityMappings = 0;
        int shiftChanges = 0;
        for (int id = 0; id < unmappedIdentifiers.size(); id++) {
            final JsonElement unmappedIdentifier = unmappedIdentifiers.get(id);
            final int mappedId = mapper.map(id, unmappedIdentifier.getAsString(), errorStrategy);
            output[id] = mappedId;

            if (mappedId == -1) {
                emptyMappings++;
            } else if (mappedId == id) {
                identityMappings++;
            }

            // Check the first entry/if the mapped id is equal to the expected sequential id
            if (id == 0 && mappedId != 0
                || id != 0 && mappedId != output[id - 1] + 1) {
                shiftChanges++;
            }
        }

        for (final String key : mapper.unusedEntries()) {
            LOGGER.warn("Unused diff mapping: {}", key);
        }
        for (final String key : mapper.redundantEntries()) {
            LOGGER.warn("Diff mapping doesn't change anything: {}", key);
        }
        for (final Map.Entry<String, String> entry : mapper.swappableEmptyEntries().entrySet()) {
            LOGGER.warn("Empty diff mapping could be resolved by a material swap: {} → {}", entry.getKey(), entry.getValue());
        }
        return new MappingsResult(output, mapper.mappedSize(), emptyMappings, identityMappings, shiftChanges);
    }

    /**
     * Returns a mappings result of two identifier objects keyed by their int id.
     *
     * @param unmappedIdentifiers object of unmapped identifiers, keyed by their int id
     * @param mappedIdentifiers   object of mapped identifiers, keyed by their int id
     * @param diffIdentifiers     diff identifiers
     * @param errorStrategy       whether to warn on missing mappings
     * @return mappings result
     */
    public static Int2IntMap map(final JsonObject unmappedIdentifiers, final JsonObject mappedIdentifiers, @Nullable final JsonObject diffIdentifiers, final ErrorStrategy errorStrategy) {
        final Int2IntMap output = new Int2IntLinkedOpenHashMap();
        output.defaultReturnValue(-1);
        final DiffMapper mapper = new DiffMapper(MappingsLoader.indexedObjectToMap(mappedIdentifiers), diffIdentifiers);
        for (final Map.Entry<String, JsonElement> entry : unmappedIdentifiers.entrySet()) {
            final int id = Integer.parseInt(entry.getKey());
            final int mappedId = mapper.map(id, entry.getValue().getAsString(), errorStrategy);
            output.put(id, mappedId);
        }
        return output;
    }

    /**
     * Returns a diff object stub for the given unmapped and mapped objects with empty mappings to be filled.
     *
     * @param unmappedObject     unmapped object
     * @param mappedObject       mapped object
     * @param existingDiffObject existing diff object
     * @param toIgnore           fields to ignore missing mappings for
     * @return diff object stub, or null if no diff is needed
     */
    public static @Nullable JsonObject getDiffObjectStub(
        final JsonObject unmappedObject,
        final JsonObject mappedObject,
        @Nullable final JsonObject existingDiffObject,
        final Set<String> toIgnore
    ) {
        final JsonObject diffObject = new JsonObject();
        final MaterialSwaps materialSwaps = MaterialSwaps.of(existingDiffObject);
        for (final Map.Entry<String, JsonElement> entry : unmappedObject.entrySet()) {
            final String key = entry.getKey();
            if (!entry.getValue().isJsonArray() || !mappedObject.has(key) || toIgnore.contains(key)) {
                continue;
            }

            final JsonArray unmappedIdentifiers = entry.getValue().getAsJsonArray();
            final JsonArray mappedIdentifiers = mappedObject.getAsJsonArray(key);
            final JsonObject diffIdentifiers = new JsonObject();
            final JsonObject existingDiffIdentifiers = existingDiffObject != null && existingDiffObject.has(key) ? existingDiffObject.getAsJsonObject(key) : null;
            final DiffMapper mapper = new DiffMapper(MappingsLoader.arrayToMap(mappedIdentifiers), existingDiffIdentifiers, materialSwaps);
            for (int id = 0; id < unmappedIdentifiers.size(); id++) {
                final String unmappedIdentifier = unmappedIdentifiers.get(id).getAsString();
                final int mappedId = mapper.map(id, unmappedIdentifier, ErrorStrategy.IGNORE);
                if (mappedId != -1) {
                    continue;
                }

                diffIdentifiers.addProperty(unmappedIdentifier, "");
            }

            if (!diffIdentifiers.isEmpty()) {
                diffObject.add(key, diffIdentifiers);
            }
        }

        if (diffObject.isEmpty()) {
            return null;
        }

        if (existingDiffObject == null) {
            return diffObject;
        }

        // Merge with existing diff object
        for (final Map.Entry<String, JsonElement> entry : diffObject.entrySet()) {
            final String key = entry.getKey();
            final JsonObject value = entry.getValue().getAsJsonObject();
            if (!existingDiffObject.has(key)) {
                existingDiffObject.add(key, value);
                continue;
            }

            final JsonObject diffIdentifiers = existingDiffObject.getAsJsonObject(key);
            for (final Map.Entry<String, JsonElement> diffEntry : value.entrySet()) {
                if (!diffIdentifiers.has(diffEntry.getKey())) {
                    diffIdentifiers.add(diffEntry.getKey(), diffEntry.getValue());
                }
            }
        }
        return existingDiffObject;
    }

    /**
     * Returns a map of the object entries hashed by their id value.
     *
     * @param object json object
     * @return map with indexes hashed by their id value
     */
    public static Object2IntMap<String> indexedObjectToMap(final JsonObject object) {
        final Object2IntMap<String> map = new Object2IntOpenHashMap<>(object.size());
        map.defaultReturnValue(-1);
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getValue().getAsString(), Integer.parseInt(entry.getKey()));
        }
        return map;
    }

    /**
     * Returns a map of the array entries hashed by their id value.
     *
     * @param array json array
     * @return map with indexes hashed by their id value
     */
    public static Object2IntMap<String> arrayToMap(final JsonArray array) {
        final Object2IntMap<String> map = new Object2IntOpenHashMap<>(array.size());
        map.defaultReturnValue(-1);
        for (int i = 0; i < array.size(); i++) {
            map.put(array.get(i).getAsString(), i);
        }
        return map;
    }

    /**
     * Result of a mapping data loader operation.
     *
     * @param mappings         int to int id mappings
     * @param mappedSize       number of mapped ids, most likely greater than the length of the mappings array
     * @param emptyMappings    number of empty (-1) mappings
     * @param identityMappings number of identity mappings
     * @param shiftChanges     number of shift changes where a mapped id is not the last mapped id + 1
     */
    public record MappingsResult(int[] mappings, int mappedSize, int emptyMappings, int identityMappings,
                                 int shiftChanges) {
    }
}
