/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023 ViaVersion and contributors
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

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.util.JsonConverter;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public final class CursedMappings {

    public static void optimizeAndSaveOhSoSpecial1_12AsNBT() throws IOException {
        final JsonObject unmappedObject = MappingsLoader.load("mapping-1.12.json");
        final JsonObject mappedObject = MappingsLoader.load("mapping-1.13.json");
        final CompoundTag tag = new CompoundTag();
        tag.put("v", new IntTag(MappingsOptimizer.VERSION));
        MappingsOptimizer.handleUnknownFields(tag, unmappedObject);
        cursedMappings(tag, unmappedObject, mappedObject, null, "blocks", "blockstates", "blockstates", 4084);
        cursedMappings(tag, unmappedObject, mappedObject, null, "items", "items", "items", unmappedObject.getAsJsonObject("items").size());
        cursedMappings(tag, unmappedObject, mappedObject, null, "legacy_enchantments", "enchantments", "enchantments", 72);
        MappingsOptimizer.mappings(tag, unmappedObject, mappedObject, null, true, false, "sounds");
        NBTIO.writeFile(tag, new File(MappingsOptimizer.OUTPUT_DIR, "mappings-1.12to1.13.nbt"), false, false);
    }

    public static void optimizeAndSaveOhSoSpecial1_12AsNBTBackwards() throws IOException {
        final JsonObject unmappedObject = MappingsLoader.load("mapping-1.13.json");
        final JsonObject mappedObject = MappingsLoader.load("mapping-1.12.json");
        final JsonObject diffObject = MappingsLoader.load("mappingdiff-1.13to1.12.json");
        final CompoundTag tag = new CompoundTag();
        tag.put("v", new IntTag(MappingsOptimizer.VERSION));
        MappingsOptimizer.handleUnknownFields(tag, unmappedObject);
        cursedMappings(tag, unmappedObject, mappedObject, diffObject, "blockstates", "blocks", "blockstates", 8582);
        cursedMappings(tag, unmappedObject, mappedObject, diffObject, "items", "items", "items", unmappedObject.getAsJsonArray("items").size());
        cursedMappings(tag, unmappedObject, mappedObject, diffObject, "enchantments", "legacy_enchantments", "enchantments", unmappedObject.getAsJsonArray("enchantments").size());
        MappingsOptimizer.names(tag, unmappedObject, diffObject, "items", "itemnames");
        MappingsOptimizer.fullNames(tag, diffObject, "entitynames", "entitynames");
        MappingsOptimizer.fullNames(tag, diffObject, "sounds", "soundnames");
        MappingsOptimizer.mappings(tag, unmappedObject, mappedObject, diffObject, true, false, "sounds");
        NBTIO.writeFile(tag, new File(MappingsOptimizer.OUTPUT_BACKWARDS_DIR, "mappings-1.13to1.12.nbt"), false, false);
    }

    private static void cursedMappings(
            final CompoundTag tag,
            final JsonObject unmappedObject,
            final JsonObject mappedObject,
            @Nullable final JsonObject diffObject,
            final String unmappedKey,
            final String mappedKey,
            final String outputKey,
            final int size
    ) {
        final JsonObject mappedIdentifiers = JsonConverter.toJsonObject(mappedObject.get(mappedKey));
        final Int2IntMap map = MappingsLoader.map(
                JsonConverter.toJsonObject(unmappedObject.get(unmappedKey)),
                mappedIdentifiers,
                diffObject != null ? diffObject.getAsJsonObject(unmappedKey) : null,
                true
        );

        final CompoundTag changedTag = new CompoundTag();
        final int[] unmapped = new int[map.size()];
        final int[] mapped = new int[map.size()];
        int i = 0;
        for (final Int2IntMap.Entry entry : map.int2IntEntrySet()) {
            unmapped[i] = entry.getIntKey();
            mapped[i] = entry.getIntValue();
            i++;
        }

        changedTag.put("id", new ByteTag(MappingsOptimizer.CHANGES_ID));
        changedTag.put("nofill", new ByteTag((byte) 1));
        changedTag.put("size", new IntTag(size));
        changedTag.put("mappedSize", new IntTag(mappedIdentifiers.size()));
        changedTag.put("at", new IntArrayTag(unmapped));
        changedTag.put("val", new IntArrayTag(mapped));
        tag.put(outputKey, changedTag);
    }
}
