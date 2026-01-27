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
import com.google.gson.JsonPrimitive;
import com.viaversion.mappingsgenerator.util.ServerJarUtil;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mappings generator to collect certain json mappings from the server jar.
 *
 * @see MappingsOptimizer for the compacting process
 */
public final class MappingsGenerator {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingsGenerator.class.getSimpleName());

    public static void main(final String[] args) throws Exception {
        if (args.length != 2) {
            LOGGER.error("Required args: path to server jar, version");
            System.exit(1);
        }

        MappingsGenerator.cleanup();

        final String serverPath = args[0];
        final String version = args[1];

        final File serverFile = new File(serverPath);
        if (!serverFile.exists()) {
            LOGGER.error("Server file does not exist at {}", serverFile);
            System.exit(1);
        }

        LOGGER.info("Loading net.minecraft.data.Main class...");
        final ClassLoader loader = URLClassLoader.newInstance(
                new URL[]{serverFile.toURI().toURL()},
                MappingsGenerator.class.getClassLoader()
        );

        final String[] serverArgs = {"--reports"};
        final Object serverMainConstructor = ServerJarUtil.loadMain(loader).getConstructor().newInstance();
        serverMainConstructor.getClass().getDeclaredMethod("main", String[].class).invoke(null, (Object) serverArgs);

        ServerJarUtil.waitForServerMain();

        MappingsGenerator.collectMappings(version);
    }

    /**
     * Deletes the previous generated and logs directories.
     */
    public static void cleanup() {
        delete(new File("generated"));
        delete(new File("logs"));
    }

    /**
     * Recursively deletes a directory or file.
     *
     * @param file file or directory to delete
     */
    public static void delete(final File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            for (final File f : file.listFiles()) {
                delete(f);
            }
        }

        file.delete();
    }

    /**
     * Collects registry mappings for the given Minecraft version and saves them to a json file.
     *
     * @param version Minecraft version
     */
    public static void collectMappings(final String version) throws IOException {
        LOGGER.info("Beginning mapping collection...");
        final String blocksContent = new String(Files.readAllBytes(new File("generated/reports/blocks.json").toPath()));
        final JsonObject blocksObject = GSON.fromJson(blocksContent, JsonObject.class);

        // Blocks and blockstates
        final Map<Integer, String> blockstatesById = new TreeMap<>();
        for (final Map.Entry<String, JsonElement> blocksEntry : blocksObject.entrySet()) {
            final JsonObject block = blocksEntry.getValue().getAsJsonObject();
            final JsonArray states = block.getAsJsonArray("states");
            for (final JsonElement state : states) {
                final JsonObject stateObject = state.getAsJsonObject();
                final int id = stateObject.getAsJsonPrimitive("id").getAsInt();
                if (blockstatesById.containsKey(id)) {
                    throw new IllegalArgumentException("Duplicate blockstate id: " + id);
                }

                blockstatesById.put(id, serializeBlockState(blocksEntry.getKey(), stateObject));
            }
        }

        final JsonArray blockstates = new JsonArray();
        final JsonArray blocks = new JsonArray();
        final JsonObject viaMappings = new JsonObject();
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

        final String registriesContent = new String(Files.readAllBytes(new File("generated/reports/registries.json").toPath()));
        final JsonObject registries = GSON.fromJson(registriesContent, JsonObject.class);
        addArray(viaMappings, registries, "minecraft:item", "items");
        addArray(viaMappings, registries, "minecraft:sound_event", "sounds");
        addArray(viaMappings, registries, "minecraft:custom_stat", "statistics");
        addArray(viaMappings, registries, "minecraft:particle_type", "particles");
        addArray(viaMappings, registries, "minecraft:block_entity_type", "blockentities");
        addArray(viaMappings, registries, "minecraft:command_argument_type", "argumenttypes");
        addArray(viaMappings, registries, "minecraft:enchantment", "enchantments");
        addArray(viaMappings, registries, "minecraft:entity_type", "entities");
        addArray(viaMappings, registries, "minecraft:motive", "paintings");
        addArray(viaMappings, registries, "minecraft:painting_variant", "paintings");
        addArray(viaMappings, registries, "minecraft:menu", "menus");
        addArray(viaMappings, registries, "minecraft:attribute", "attributes");
        addArray(viaMappings, registries, "minecraft:recipe_serializer", "recipe_serializers");
        addArray(viaMappings, registries, "minecraft:slot_display", "slot_displays");
        addArray(viaMappings, registries, "minecraft:data_component_type", "data_component_type");

        // Save
        new File("mappings").mkdir();
        try (final PrintWriter out = new PrintWriter("mappings/mapping-" + version + ".json")) {
            out.print(GSON.toJson(viaMappings));
        }

        new File("logs").deleteOnExit();
        LOGGER.info("Mapping file has been written to mappings/mapping-{}.json", version);
    }

    /**
     * Returns a blockstate string for the given block and properties.
     *
     * @param block       block identifier
     * @param blockObject json object holding properties
     * @return blockstate identifier
     */
    private static String serializeBlockState(String block, final JsonObject blockObject) {
        block = removeNamespace(block);
        if (!blockObject.has("properties")) {
            return block;
        }

        final StringBuilder value = new StringBuilder(block);
        value.append('[');
        final JsonObject properties = blockObject.getAsJsonObject("properties");
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
        return value.toString();
    }

    /**
     * Adds array mappings from a registry to the mappings object.
     *
     * @param mappings    mappings to add to
     * @param registry    registry to read from
     * @param registryKey registry key to read from
     * @param mappingsKey mappings key to write to
     */
    private static void addArray(final JsonObject mappings, final JsonObject registry, final String registryKey, final String mappingsKey) {
        if (!registry.has(registryKey)) {
            LOGGER.debug("Ignoring missing registry: {}", registryKey);
            return;
        }

        LOGGER.debug("Collecting {}...", registryKey);
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

    /**
     * Removes the Minecraft namespace from a potentially namespaced key.
     *
     * @param key key to remove the namespace from
     * @return key without the Minecraft namespace
     */
    private static String removeNamespace(final String key) {
        if (key.startsWith("minecraft:")) {
            return key.substring("minecraft:".length());
        }
        return key;
    }
}
