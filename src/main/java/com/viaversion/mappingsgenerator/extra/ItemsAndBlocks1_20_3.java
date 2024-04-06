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
package com.viaversion.mappingsgenerator.extra;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;

import java.io.IOException;

import static com.viaversion.mappingsgenerator.util.JsonConverter.collectStringList;

public final class ItemsAndBlocks1_20_3 {

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-1.20.3.json");
        final CompoundTag tag = new CompoundTag();
        tag.put("items", collectStringList(mappings.getAsJsonArray("items")));
        tag.put("blocks", collectStringList(mappings.getAsJsonArray("blocks")));
        tag.put("sounds", collectStringList(mappings.getAsJsonArray("sounds")));
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/extra-identifiers-1.20.3.nbt"));
    }
}
