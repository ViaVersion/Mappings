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

import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import com.viaversion.nbt.tag.CompoundTag;
import java.io.IOException;

import static com.viaversion.mappingsgenerator.util.JsonConverter.collectStringList;

public final class BlockStates1_13 {

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-1.13.json");
        final CompoundTag tag = new CompoundTag();
        tag.put("blockstates", collectStringList(mappings.getAsJsonArray("blockstates")));
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/blockstates-1.13.nbt"));
    }
}
