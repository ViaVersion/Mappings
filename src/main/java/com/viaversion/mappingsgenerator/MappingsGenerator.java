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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

public final class MappingsGenerator {

    /**
     * For in-IDE execution.
     */
    public static void main(final String[] args) throws Exception {
        cleanup();

        try {
            // Server jar bundle since 21w39a
            // Alternatively, java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
            System.setProperty("bundlerMainClass", "net.minecraft.data.Main");
            Class.forName("net.minecraft.bundler.Main").getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--reports"});
            Main.waitForServerMain();
        } catch (final ClassNotFoundException ignored) {
            final Class<?> mainClass = Class.forName("net.minecraft.data.Main");
            mainClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--reports"});
        }

        collectMappings("23w08a");
    }

    public static void cleanup() {
        delete(new File("generated"));
        delete(new File("logs"));
    }

    public static void delete(final File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            for (final File f : file.listFiles()) {
                delete(f);
            }
        }

        file.delete();
    }

    public static void collectMappings(final String version) throws IOException {
        System.out.println("Beginning mapping collection...");
        String content = new String(Files.readAllBytes(new File("generated/reports/blocks.json").toPath()));

        final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonObject object = gson.fromJson(content, JsonObject.class);

        final JsonObject viaMappings = new JsonObject();

        // Blocks and blockstates
        final Map<Integer, String> blockstatesById = new TreeMap<>();
        for (final Map.Entry<String, JsonElement> blocksEntry : object.entrySet()) {
            final JsonObject block = blocksEntry.getValue().getAsJsonObject();
            final JsonArray states = block.getAsJsonArray("states");
            for (final JsonElement state : states) {
                final JsonObject stateObject = state.getAsJsonObject();
                final int id = stateObject.getAsJsonPrimitive("id").getAsInt();
                if (blockstatesById.containsKey(id)) {
                    throw new IllegalArgumentException("Duplicate blockstate id: " + id);
                }

                final StringBuilder value = new StringBuilder(removeNamespace(blocksEntry.getKey()));
                if (stateObject.has("properties")) {
                    value.append('[');
                    final JsonObject properties = stateObject.getAsJsonObject("properties");
                    boolean first = true;
                    for (final Map.Entry<String, JsonElement> propertyEntry : properties.entrySet()) {
                        if (first) {
                            first = false;
                        } else {
                            value.append(',');
                        }
                        value.append(propertyEntry.getKey()).append('=').append(propertyEntry.getValue().getAsJsonPrimitive().getAsString());
                    }
                    value.append(']');
                }
                blockstatesById.put(id, value.toString());
            }
        }

        final JsonArray blockstates = new JsonArray();
        final JsonArray blocks = new JsonArray();
        viaMappings.add("blockstates", blockstates);
        viaMappings.add("blocks", blocks);

        String lastBlock = "";
        for (final Map.Entry<Integer, String> entry : blockstatesById.entrySet()) {
            final String blockstate = entry.getValue();
            blockstates.add(blockstate);

            final String block = blockstate.split("\\[", 2)[0];
            if (!lastBlock.equals(block)) {
                lastBlock = block;
                blocks.add(new JsonPrimitive(lastBlock));
            }
        }

        content = new String(Files.readAllBytes(new File("generated/reports/registries.json").toPath()));
        object = gson.fromJson(content, JsonObject.class);

        addArray(viaMappings, object, "minecraft:item", "items");
        addArray(viaMappings, object, "minecraft:sound_event", "sounds");
        addArray(viaMappings, object, "minecraft:particle_type", "particles");
        addArray(viaMappings, object, "minecraft:block_entity_type", "blockentities");
        addArray(viaMappings, object, "minecraft:command_argument_type", "argumenttypes");
        addArray(viaMappings, object, "minecraft:enchantment", "enchantments");
        addArray(viaMappings, object, "minecraft:entity_type", "entities");
        addArray(viaMappings, object, "minecraft:motive", "paintings");
        addArray(viaMappings, object, "minecraft:painting_variant", "paintings");

        // Save
        new File("mappings").mkdir();
        try (final PrintWriter out = new PrintWriter("mappings/mapping-" + version + ".json")) {
            out.print(gson.toJson(viaMappings));
        }

        new File("logs").deleteOnExit();
        System.out.println("Done!");
    }

    private static void addArray(final JsonObject mappings, final JsonObject registry, final String registryKey, final String mappingsKey) {
        if (!registry.has(registryKey)) {
            System.out.println("Ignoring missing registry: " + registryKey);
            return;
        }

        System.out.println("Collecting " + registryKey + "...");
        final JsonObject entries = registry.getAsJsonObject(registryKey).getAsJsonObject("entries");
        final String[] keys = new String[entries.size()];
        for (final Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            final int protocolId = entry.getValue().getAsJsonObject().getAsJsonPrimitive("protocol_id").getAsInt();
            if (protocolId < 0 || protocolId >= keys.length) {
                throw new IllegalArgumentException("Out of bounds protocol id: " + protocolId + " in " + registryKey);
            }
            if (keys[protocolId] != null) {
                throw new IllegalArgumentException("Duplicate protocol id: " + protocolId + " in " + registryKey);
            }

            keys[protocolId] = removeNamespace(entry.getKey());
        }

        final JsonArray array = new JsonArray();
        mappings.add(mappingsKey, array);
        for (final String key : keys) {
            array.add(new JsonPrimitive(key));
        }
    }

    private static String removeNamespace(final String key) {
        if (key.startsWith("minecraft:")) {
            return key.substring("minecraft:".length());
        }
        return key;
    }
}