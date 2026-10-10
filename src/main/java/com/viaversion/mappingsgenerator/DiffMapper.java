/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023-2026 ViaVersion and contributors
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Maps identifiers to their target ids, applying diff mappings on top of direct matches.
 * <p>
 * Blockstate targets may leave out properties: target properties missing from the mapped value are copied from the
 * source state, and source properties unknown to the target block are dropped. A value ending with {@code [}
 * is a target without any explicit properties.
 * <p>
 * Blockstate keys may leave out properties as well, matching every source state with the given property values.
 * Exact keys take precedence, then the matching key with the most properties, down to the bare block key.
 * Multiple matching keys with the same number of properties and different values are ambiguous and leave the state unmapped.
 */
final class DiffMapper {

    private final Object2IntMap<String> mappedIdentifiers;
    private final @Nullable JsonObject diffIdentifiers;
    private @Nullable Map<String, List<String>> blockProperties;
    private @Nullable Map<String, List<PartialKey>> partialKeys;

    DiffMapper(final Object2IntMap<String> mappedIdentifiers, @Nullable final JsonObject diffIdentifiers) {
        this.mappedIdentifiers = mappedIdentifiers;
        this.diffIdentifiers = diffIdentifiers;
    }

    /**
     * Returns the mapped id of the given entry, or -1 if not found.
     *
     * @param id            id of the entry
     * @param value         value of the entry
     * @param errorStrategy whether to warn on missing mappings
     * @return mapped id, or -1 if it was not found
     */
    int map(final int id, final String value, final ErrorStrategy errorStrategy) {
        int mappedId = mappedIdentifiers.getInt(value);
        String mappedName = null;
        if (diffIdentifiers != null) {
            // Always check diff mappings to allow overrides for already existing entries
            JsonElement diffElement = diffIdentifiers.get(value);
            final PartialKey partialKey;
            if (diffElement != null || (diffElement = diffIdentifiers.get(Integer.toString(id))) != null) {
                // Direct match by id or value
                mappedName = diffElement.getAsString();
                if (mappedName.startsWith("id:")) {
                    // Special case for cursed mappings
                    return Integer.parseInt(mappedName.substring("id:".length()));
                }
            } else if (value.indexOf('[') != -1 && (partialKey = matchPartialKey(value, errorStrategy)) != null) {
                // Mapping for all states of the block with the given properties
                mappedName = partialKey.value().getAsString();
            }

            if (mappedName != null) {
                if (mappedName.isEmpty()) {
                    return -1; // "empty" remaps without warnings
                }

                mappedName = resolveTarget(value, mappedName);
                mappedId = mappedIdentifiers.getInt(mappedName);
            }
        }

        if (mappedId == -1) {
            if (diffIdentifiers == null) {
                errorStrategy.apply("No direct mapping or diff file for " + value + " :( ");
            } else if (mappedName != null) {
                errorStrategy.apply("No mapping for " + value + " (resolved to " + mappedName + ") :( ");
            } else {
                errorStrategy.apply("No mapping for " + value + " :( ");
            }
        }
        return mappedId;
    }

    /**
     * Returns the most specific partial key matching the given state, or null if there is none or the match is ambiguous.
     *
     * @param state         source state
     * @param errorStrategy strategy for ambiguous matches
     * @return most specific matching partial key, or null
     */
    private @Nullable PartialKey matchPartialKey(final String state, final ErrorStrategy errorStrategy) {
        final List<PartialKey> candidates = partialKeys().get(state.substring(0, state.indexOf('[')));
        if (candidates == null) {
            return null;
        }

        final Map<String, String> properties = properties(state);
        PartialKey match = null;
        PartialKey ambiguousMatch = null;
        for (final PartialKey candidate : candidates) {
            if (!candidate.matches(properties)) {
                continue;
            }

            if (match == null || candidate.properties().size() > match.properties().size()) {
                match = candidate;
                ambiguousMatch = null;
            } else if (candidate.properties().size() == match.properties().size() && !candidate.value().equals(match.value())) {
                ambiguousMatch = candidate;
            }
        }

        if (ambiguousMatch != null) {
            errorStrategy.apply("Ambiguous mappings for " + state + ": " + match.key() + " and " + ambiguousMatch.key());
            return null;
        }
        return match;
    }

    private Map<String, List<PartialKey>> partialKeys() {
        if (partialKeys != null) {
            return partialKeys;
        }

        partialKeys = new HashMap<>();
        for (final Map.Entry<String, JsonElement> entry : diffIdentifiers.entrySet()) {
            final String key = entry.getKey();
            final int dataIndex = key.indexOf('[');
            final String block = dataIndex == -1 ? key : key.substring(0, dataIndex);
            partialKeys.computeIfAbsent(block, $ -> new ArrayList<>()).add(new PartialKey(key, properties(key), entry.getValue()));
        }
        return partialKeys;
    }

    /**
     * Returns the full target identifier, filling in target block properties missing from the mapped value.
     * Properties that can't be filled are set to {@code ?}, so the result won't resolve.
     *
     * @param source source identifier
     * @param target mapped value
     * @return full target identifier
     */
    private String resolveTarget(final String source, final String target) {
        if (mappedIdentifiers.containsKey(target)) {
            return target;
        }

        final int dataIndex = target.indexOf('[');
        final String block = dataIndex == -1 ? target : target.substring(0, dataIndex);
        final List<String> propertyNames = blockProperties().get(block);
        if (propertyNames == null) {
            return target;
        }
        if (propertyNames.isEmpty()) {
            return block;
        }

        final Map<String, String> targetProperties = properties(target);
        if (!propertyNames.containsAll(targetProperties.keySet())) {
            return target;
        }

        final Map<String, String> properties = properties(source);
        properties.putAll(targetProperties);

        final StringBuilder builder = new StringBuilder(block).append('[');
        for (int i = 0; i < propertyNames.size(); i++) {
            final String name = propertyNames.get(i);
            if (i != 0) {
                builder.append(',');
            }
            builder.append(name).append('=').append(properties.getOrDefault(name, "?"));
        }
        return builder.append(']').toString();
    }

    private Map<String, List<String>> blockProperties() {
        if (blockProperties != null) {
            return blockProperties;
        }

        // Property names in the order used by the target states
        blockProperties = new HashMap<>();
        for (final String identifier : mappedIdentifiers.keySet()) {
            final int dataIndex = identifier.indexOf('[');
            if (dataIndex == -1) {
                blockProperties.put(identifier, List.of());
                continue;
            }

            final String block = identifier.substring(0, dataIndex);
            if (!blockProperties.containsKey(block)) {
                blockProperties.put(block, new ArrayList<>(properties(identifier).keySet()));
            }
        }
        return blockProperties;
    }

    /**
     * Returns the properties of a possibly partial blockstate, e.g. {@code block[a=b,c=d]}, {@code block[a=b} or {@code block[}.
     *
     * @param state blockstate
     * @return mutable map of property names to values
     */
    static Map<String, String> properties(final String state) {
        final Map<String, String> properties = new LinkedHashMap<>();
        final int dataIndex = state.indexOf('[');
        if (dataIndex == -1) {
            return properties;
        }

        final int end = state.endsWith("]") ? state.length() - 1 : state.length();
        if (dataIndex + 1 >= end) {
            return properties;
        }

        for (final String property : state.substring(dataIndex + 1, end).split(",")) {
            final int separator = property.indexOf('=');
            if (separator == -1) {
                throw new IllegalArgumentException("Invalid property " + property + " in " + state);
            }
            properties.put(property.substring(0, separator), property.substring(separator + 1));
        }
        return properties;
    }

    private record PartialKey(String key, Map<String, String> properties, JsonElement value) {

        boolean matches(final Map<String, String> stateProperties) {
            for (final Map.Entry<String, String> entry : properties.entrySet()) {
                if (!entry.getValue().equals(stateProperties.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }
}
