package com.viaversion.mappingsgenerator.extra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntArrayTag;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.Set;

public final class Fluids26_1 {

    private static final Set<String> FLUID_BLOCKS = Set.of("water", "lava", "kelp", "seagrass", "tall_seagrass", "bubble_column", "kelp_plant");

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-26.1.json");
        final JsonArray array = mappings.getAsJsonArray("blockstates");
        int i = 0;
        final IntList list = new IntArrayList();
        for (final JsonElement element : array) {
            final String blockState = element.getAsString();
            final String block = blockState.split("\\[")[0];
            if (FLUID_BLOCKS.contains(block) || blockState.contains("waterlogged=true")) {
                list.add(i);
            }
            i++;
        }
        final IntArrayTag arrayTag = new IntArrayTag(list.toIntArray());
        final CompoundTag tag = new CompoundTag();
        tag.put("fluids", arrayTag);
        NBTIO.writer().named().write(MappingsOptimizer.OUTPUT_DIR.resolve("extra/fluids-26.1.nbt"), tag, false);
    }
}
