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
import com.viaversion.mappingsgenerator.util.IdRanges;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockConnections {

    private static final List<String> HORIZONTAL_BLOCK_FACES = List.of("north", "south", "east", "west");
    private static final List<String> CONNECTION_TYPES = List.of("fence", "netherFence", "pane", "cobbleWall", "redstone", "allFalseIfStairPre1_12");

    public static void main(final String[] args) throws Exception {
        final JsonArray blockstates = MappingsLoader.load("mapping-1.13.json").getAsJsonArray("blockstates");
        final Object2IntMap<String> statesMap = new Object2IntOpenHashMap<>();
        statesMap.defaultReturnValue(-1);
        final Map<String, IntList> blockStates = new HashMap<>();
        for (int id = 0; id < blockstates.size(); id++) {
            final String state = blockstates.get(id).getAsString();
            statesMap.put(state, id);

            final int propertiesIndex = state.indexOf('[');
            if (propertiesIndex != -1) {
                blockStates.computeIfAbsent(state.substring(0, propertiesIndex), $ -> new IntArrayList()).add(id);
            }
        }

        // Store each unique connection profile once, along with the ranges of block state ids it applies to,
        // instead of writing the connection data for every single block state
        final JsonObject object = MappingsLoader.load("extra/blockConnections.json");
        final Map<CompoundTag, IntList> profiles = new LinkedHashMap<>();
        final Int2ObjectMap<CompoundTag> stateProfiles = new Int2ObjectOpenHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final CompoundTag profile = toProfileTag(entry.getKey(), entry.getValue().getAsJsonObject());
            final int blockStateId = statesMap.getInt(entry.getKey());
            if (blockStateId != -1) {
                addStateId(profiles, stateProfiles, profile, blockStateId, entry.getKey());
            } else {
                // Used for fences and glass panes, so the json file doesn't need to contain every single block state
                final IntList stateIds = blockStates.get(entry.getKey());
                if (stateIds == null) {
                    throw new IllegalArgumentException("Invalid block state " + entry.getKey());
                }
                for (final int stateId : stateIds) {
                    addStateId(profiles, stateProfiles, profile, stateId, entry.getKey());
                }
            }
        }

        final ListTag<CompoundTag> profilesTag = new ListTag<>(CompoundTag.class);
        for (final Map.Entry<CompoundTag, IntList> entry : profiles.entrySet()) {
            final CompoundTag profileTag = entry.getKey().copy();
            profileTag.put("ids", IdRanges.encode(entry.getValue()));
            profilesTag.add(profileTag);
        }

        final CompoundTag tag = new CompoundTag();
        tag.put("profiles", profilesTag);

        addOccludingBlockStates(tag, statesMap);
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/blockConnections.nbt"));
    }

    private static void addStateId(final Map<CompoundTag, IntList> profiles, final Int2ObjectMap<CompoundTag> stateProfiles, final CompoundTag profile, final int stateId, final String key) {
        final CompoundTag existingProfile = stateProfiles.putIfAbsent(stateId, profile);
        if (existingProfile == null) {
            profiles.computeIfAbsent(profile, $ -> new IntArrayList()).add(stateId);
        } else if (!existingProfile.equals(profile)) {
            throw new IllegalArgumentException("Conflicting connection profiles for state id " + stateId + " (from " + key + ")");
        }
    }

    private static CompoundTag toProfileTag(final String state, final JsonObject blockObject) {
        final CompoundTag profile = new CompoundTag();
        for (final Map.Entry<String, JsonElement> blockEntry : blockObject.entrySet()) {
            final byte connectionTypeId = connectionTypeToId(blockEntry.getKey());
            final JsonObject valuesObject = blockEntry.getValue().getAsJsonObject();
            if (valuesObject.size() != 4) {
                throw new IllegalArgumentException("Invalid block connection " + blockEntry.getKey());
            }

            byte faces = 0;
            for (final Map.Entry<String, JsonElement> faceEntry : valuesObject.entrySet()) {
                if (faceEntry.getValue().getAsBoolean()) {
                    faces |= (byte) (1 << blockFaceToId(faceEntry.getKey()));
                }
            }

            if (faces == 0) {
                throw new IllegalArgumentException("Invalid block connection (empty faces) " + blockEntry.getKey());
            }

            profile.putByte(Integer.toString(connectionTypeId), faces);
        }

        if (profile.isEmpty()) {
            throw new IllegalArgumentException("Invalid block state (no connection data?) " + state);
        }

        // Before 1.12, stairs did not connect to fences
        if (state.contains("stairs[")) {
            profile.putByte(Integer.toString(connectionTypeToId("allFalseIfStairPre1_12")), (byte) 0);
        }
        return profile;
    }

    private static void addOccludingBlockStates(final CompoundTag tag, final Object2IntMap<String> statesMap) throws IOException {
        final JsonArray states = MappingsLoader.load("extra/occluding-states-1.13.json", JsonArray.class);
        final IntList ids = new IntArrayList(states.size());
        for (final JsonElement stateElement : states) {
            final String state = stateElement.getAsString();
            final int id = statesMap.getInt(state);
            if (id == -1) {
                throw new IllegalArgumentException("Invalid occluding state " + state);
            }
            ids.add(id);
        }

        tag.put("occluding-states", IdRanges.encode(ids));
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
