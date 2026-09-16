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
package com.viaversion.mappingsgenerator.extra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntArrayTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Could be furhter improved and re-used if we ever NEED to include every block state string
public final class BlockStates1_13 {

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-1.13.json");
        final JsonArray blockstates = mappings.getAsJsonArray("blockstates");

        // Store each block once with its properties instead of every full state string.
        // Properties shared between blocks (same name and values) are stored once in a table and referenced by index
        final ListTag<CompoundTag> propertyTable = new ListTag<>(CompoundTag.class);
        final Map<List<String>, Integer> propertyIndexes = new HashMap<>();
        final ListTag<CompoundTag> blocks = new ListTag<>(CompoundTag.class);
        String currentName = null;
        Map<String, List<String>> currentProperties = null;
        for (final JsonElement element : blockstates) {
            final String state = element.getAsString();
            final int bracketIndex = state.indexOf('[');
            final String name = bracketIndex != -1 ? state.substring(0, bracketIndex) : state;
            if (!name.equals(currentName)) {
                if (currentName != null) {
                    blocks.add(toBlockTag(currentName, currentProperties, propertyTable, propertyIndexes));
                }
                currentName = name;
                currentProperties = new LinkedHashMap<>();
            }

            if (bracketIndex != -1) {
                for (final String property : state.substring(bracketIndex + 1, state.length() - 1).split(",")) {
                    final int equalsIndex = property.indexOf('=');
                    final String value = property.substring(equalsIndex + 1);
                    final List<String> values = currentProperties.computeIfAbsent(property.substring(0, equalsIndex), $ -> new ArrayList<>());
                    if (!values.contains(value)) {
                        values.add(value);
                    }
                }
            }
        }
        blocks.add(toBlockTag(currentName, currentProperties, propertyTable, propertyIndexes));

        final CompoundTag tag = new CompoundTag();
        tag.put("properties", propertyTable);
        tag.put("blockstates", blocks);

        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/blockstates-1.13.nbt"));
    }

    private static CompoundTag toBlockTag(
        final String name,
        final Map<String, List<String>> properties,
        final ListTag<CompoundTag> propertyTable,
        final Map<List<String>, Integer> propertyIndexes
    ) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        if (properties.isEmpty()) {
            return tag;
        }

        final int[] indexes = new int[properties.size()];
        int i = 0;
        for (final Map.Entry<String, List<String>> entry : properties.entrySet()) {
            final List<String> key = new ArrayList<>(entry.getValue().size() + 1);
            key.add(entry.getKey());
            key.addAll(entry.getValue());
            indexes[i++] = propertyIndexes.computeIfAbsent(key, $ -> {
                final CompoundTag propertyTag = new CompoundTag();
                propertyTag.putString("name", entry.getKey());
                final ListTag<StringTag> valuesTag = new ListTag<>(StringTag.class);
                for (final String value : entry.getValue()) {
                    valuesTag.add(new StringTag(value));
                }
                propertyTag.put("values", valuesTag);
                propertyTable.add(propertyTag);
                return propertyTable.size() - 1;
            });
        }
        tag.put("properties", new IntArrayTag(indexes));
        return tag;
    }
}
