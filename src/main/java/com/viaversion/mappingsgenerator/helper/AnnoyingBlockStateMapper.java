package com.viaversion.mappingsgenerator.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.util.GsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.viaversion.mappingsgenerator.MappingsOptimizer.MAPPINGS_DIR;

/**
 * Similar to {@link BlockStateMapper} in use, except for the array of wood/stone-based building blocks.
 * <p>
 * Directly edits the diff file.
 */
final class AnnoyingBlockStateMapper {

    private static final List<Function<String, String>> MAPPERS = new ArrayList<>();
    private static final List<String> WONDERFUL_STATES = List.of(
            "_wood", "_log", "_sapling", "_wall", "_slab", "_stairs",
            "_trapdoor", "_door", "_button", "_hanging_sign", "_wall_sign", "_sign",
            "_leaves", "_fence_gate", "_fence", "_pressure_plate"
    );

    public static void main(final String[] args) throws IOException {
        // Input examples
        final String from = "1.21.4";
        final String to = "1.21.2";
        replace("resin_brick", "brick");
        replace("tuff", "andesite");
        contains("copper", "brick");

        final Path path = MAPPINGS_DIR.resolve("diff").resolve(String.format("mapping-%sto%s.json", from, to));
        final JsonObject object = GsonUtil.GSON.fromJson(Files.readString(path), JsonObject.class);
        final JsonObject blockStates = object.getAsJsonObject("blockstates");
        final JsonObject outputStates = new JsonObject();
        final Set<String> handled = new HashSet<>();
        for (final Map.Entry<String, JsonElement> entry : blockStates.entrySet()) {
            final String value = entry.getValue().getAsString();
            final String key = entry.getKey();
            if (!value.isEmpty()) {
                outputStates.add(key, entry.getValue());
                continue;
            }

            final String keyPart = key.split("\\[")[0];
            if (handled.contains(keyPart)) {
                outputStates.add(key, entry.getValue());
                continue;
            }

            final String wonderfulState = WONDERFUL_STATES.stream().filter(keyPart::endsWith).findAny().orElse(null);
            if (wonderfulState == null) {
                outputStates.add(key, entry.getValue());
                continue;
            }

            handled.add(key);
            String outputKey = keyPart.replace(wonderfulState, "");
            for (final Function<String, String> mapper : MAPPERS) {
                outputKey = mapper.apply(outputKey);
            }
            outputStates.addProperty(keyPart, outputKey + wonderfulState + "[");
        }

        object.add("blockstates", outputStates);
        Files.writeString(path, GsonUtil.GSON.toJson(object));
    }

    private static void equals(final String from, final String to) {
        MAPPERS.add(s -> s.equals(from) ? to : s);
    }

    private static void contains(final String from, final String to) {
        MAPPERS.add(s -> s.contains(from) ? to : s);
    }

    private static void replace(final String from, final String to) {
        MAPPERS.add(s -> s.replace(from, to));
    }
}
