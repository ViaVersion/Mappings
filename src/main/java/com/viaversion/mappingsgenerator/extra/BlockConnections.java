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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import com.viaversion.nbt.tag.ByteArrayTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntArrayTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.ShortTag;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class BlockConnections {

    private static final List<String> HORIZONTAL_BLOCK_FACES = List.of("north", "south", "east", "west");
    private static final List<String> CONNECTION_TYPES = List.of("fence", "netherFence", "pane", "cobbleWall", "redstone", "allFalseIfStairPre1_12");

    public static void main(final String[] args) throws Exception {
        final JsonArray blockstates = MappingsLoader.load("mapping-1.13.json").getAsJsonArray("blockstates");
        final Object2IntMap<String> statesMap = new Object2IntOpenHashMap<>();
        statesMap.defaultReturnValue(-1);
        for (int id = 0; id < blockstates.size(); id++) {
            statesMap.put(blockstates.get(id).getAsString(), id);
        }

        final JsonObject object = MappingsLoader.load("extra/blockConnections.json");
        final CompoundTag tag = new CompoundTag();
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag.class);
        tag.put("data", list);
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final CompoundTag blockTag = new CompoundTag();
            final int blockStateId = statesMap.getInt(entry.getKey());
            if (blockStateId != -1) {
                blockTag.put("id", new ShortTag((short) blockStateId));
            } else {
                // Used for fences and glass panes, so the json file doesn't need to contain every single block state
                final int[] ids = statesMap.object2IntEntrySet().stream().filter(e -> {
                    final int propertiesIndex = e.getKey().indexOf('[');
                    if (propertiesIndex == -1) {
                        return false;
                    }

                    return e.getKey().substring(0, propertiesIndex).equals(entry.getKey());
                }).mapToInt(Object2IntMap.Entry::getIntValue).toArray();

                if (ids.length == 0) {
                    throw new IllegalArgumentException("Invalid block state " + entry.getKey());
                }

                blockTag.put("ids", new IntArrayTag(ids));
            }

            list.add(blockTag);

            final JsonObject blockObject = entry.getValue().getAsJsonObject();
            for (final Map.Entry<String, JsonElement> blockEntry : blockObject.entrySet()) {
                final byte connectionTypeId = connectionTypeToId(blockEntry.getKey());
                final JsonObject valuesObject = blockEntry.getValue().getAsJsonObject();
                if (valuesObject.size() != 4) {
                    throw new IllegalArgumentException("Invalid block connection " + blockEntry.getKey());
                }

                final IntList faces = new IntArrayList(4);
                for (final Map.Entry<String, JsonElement> faceEntry : valuesObject.entrySet()) {
                    if (faceEntry.getValue().getAsBoolean()) {
                        final byte faceId = blockFaceToId(faceEntry.getKey());
                        faces.add(faceId);
                    }
                }

                if (faces.isEmpty()) {
                    throw new IllegalArgumentException("Invalid block connection (empty faces) " + blockEntry.getKey());
                }

                final byte[] facesArray = new byte[faces.size()];
                for (int i = 0; i < facesArray.length; i++) {
                    facesArray[i] = (byte) faces.getInt(i);
                }
                blockTag.put(Integer.toString(connectionTypeId), new ByteArrayTag(facesArray));
            }

            if (blockTag.size() == 1) {
                throw new IllegalArgumentException("Invalid block state (blockTag only contains id tag?) " + entry.getKey());
            }

            // Before 1.12, stairs did not connect to fences
            if (entry.getKey().contains("stairs[")) {
                blockTag.put(Integer.toString(connectionTypeToId("allFalseIfStairPre1_12")), new ByteArrayTag());
            }
        }

        addOccludingBlockStates(tag, statesMap);
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/blockConnections.nbt"));
    }

    private static void addOccludingBlockStates(final CompoundTag tag, final Object2IntMap<String> statesMap) throws IOException {
        final JsonArray states = MappingsLoader.load("extra/occluding-states-1.13.json", JsonArray.class);
        final int[] value = new int[states.size()];
        int i = 0;
        for (final JsonElement stateElement : states) {
            final String state = stateElement.getAsString();
            final int id = statesMap.getInt(state);
            if (id == -1) {
                throw new IllegalArgumentException("Invalid occluding state " + state);
            }
            value[i++] = id;
        }
        tag.put("occluding-states", new IntArrayTag(value));
    }

    private static byte connectionTypeToId(final String type) {
        final byte id = (byte) CONNECTION_TYPES.indexOf(type);
        if (id == -1) {
            throw new IllegalArgumentException("Invalid connection type " + type);
        }
        return id;
    }

    private static byte blockFaceToId(final String type) {
        final byte id = (byte) HORIZONTAL_BLOCK_FACES.indexOf(type);
        if (id == -1) {
            throw new IllegalArgumentException("Invalid block face " + type);
        }
        return id;
    }
}
