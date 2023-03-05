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
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;

public final class MotionBlocking1_14 {

    public static void main(final String[] args) throws IOException {
        final JsonObject mappedData = MappingsLoader.load("mapping-1.14.json");
        final JsonArray mappedBlockStates = mappedData.getAsJsonArray("blockstates");
        final Object2IntMap<String> blockStateMap = new Object2IntOpenHashMap<>(mappedBlockStates.size());
        blockStateMap.defaultReturnValue(-1);
        for (int i = 0; i < mappedBlockStates.size(); i++) {
            final String state = mappedBlockStates.get(i).getAsString();
            blockStateMap.put(state, i);
        }

        final JsonArray motionBlocking = MappingsLoader.load("extra/motion-blocking-1.14.json").getAsJsonArray("motion_blocking");
        final int[] motionBlockingIds = new int[motionBlocking.size()];
        for (int i = 0; i < motionBlocking.size(); i++) {
            final String state = motionBlocking.get(i).getAsString();
            final int mappedId = blockStateMap.getInt(state);
            if (mappedId == -1) {
                System.err.println("Unknown blockstate " + state + " :(");
                continue;
            }

            motionBlockingIds[i] = mappedId;
        }

        final IntList nonFullBlocks = new IntArrayList();
        for (int i = 0; i < mappedBlockStates.size(); i++) {
            final String state = mappedBlockStates.get(i).getAsString();
            if (state.contains("_slab") || state.contains("_stairs") || state.contains("_wall[")
                    || state.equals("grass_path") || state.contains("farmland[")) {
                nonFullBlocks.add(i);
            }
        }

        final CompoundTag tag = new CompoundTag();
        tag.put("motionBlocking", new IntArrayTag(motionBlockingIds));
        tag.put("nonFullBlocks", new IntArrayTag(nonFullBlocks.toArray(new int[0])));
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/heightmap-1.14.nbt"));
    }
}
