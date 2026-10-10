package com.viaversion.mappingsgenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class MaterialSwapsTest {

    @Test
    void testSwap() {
        final MaterialSwaps swaps = swaps("""
            {
              "poplar": "birch",
              "sulfur": "sandstone",
              "sulfur_brick": "end_stone_brick",
              "crimson": {"to": "acacia", "words": {"stem": "log"}}
            }""");
        // Longest material wins
        assertSwap(swaps, "sulfur_brick_slab", "end_stone_brick_slab");
        assertSwap(swaps, "sulfur_bricks", "sandstone_bricks");
        // Additional words are only replaced in identifiers with the material
        assertSwap(swaps, "oak_stem", null);
        // Whole words only
        assertSwap(swaps, "poplarish_planks", null);
        // Different materials with the same number of words
        assertSwap(swaps, "poplar_sulfur", null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> swaps("{\"poplar\": \"\"}"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> swaps("{\"poplar\": {\"to\": \"birch\", \"tokens\": {}}}"));
    }

    @Test
    void testMapping() {
        final MaterialSwaps swaps = swaps("{\"poplar\": \"birch\", \"unused\": \"stone\"}");
        final JsonObject diff = JsonParser.parseString("{\"poplar_planks\": \"\"}").getAsJsonObject();
        final DiffMapper mapper = new DiffMapper(MappingsLoader.arrayToMap(states("birch_planks", "birch_log")), diff, swaps);
        Assertions.assertEquals(1, mapper.map(0, "poplar_log", ErrorStrategy.IGNORE));
        // Swap target doesn't exist
        Assertions.assertEquals(-1, mapper.map(0, "poplar_door", ErrorStrategy.IGNORE));
        // Explicitly unmapped, even though the swap could resolve it
        Assertions.assertEquals(-1, mapper.map(1, "poplar_planks", ErrorStrategy.IGNORE));

        Assertions.assertEquals(Map.of("poplar_planks", "birch_planks"), mapper.swappableEmptyEntries());
        Assertions.assertEquals(List.of("unused"), swaps.unusedMaterials());
    }

    @Test
    void testDiffStubs() {
        final JsonObject unmapped = new JsonObject();
        unmapped.add("items", states("birch_planks", "poplar_planks", "poplar_fence"));
        final JsonObject mapped = new JsonObject();
        mapped.add("items", states("birch_planks"));
        final JsonObject diff = JsonParser.parseString("{\"materials\": {\"poplar\": \"birch\"}}").getAsJsonObject();

        final JsonObject stub = MappingsLoader.getDiffObjectStub(unmapped, mapped, diff, Set.of());
        Assertions.assertNotNull(stub);
        Assertions.assertEquals(JsonParser.parseString("{\"poplar_fence\": \"\"}"), stub.get("items"));
    }

    private static void assertSwap(final MaterialSwaps swaps, final String identifier, final String expected) {
        final MaterialSwaps.Swap swap = swaps.swap(identifier);
        Assertions.assertEquals(expected, swap != null ? swap.target() : null, identifier);
    }

    private static MaterialSwaps swaps(final String materials) {
        final JsonObject diff = new JsonObject();
        diff.add("materials", JsonParser.parseString(materials));
        return MaterialSwaps.of(diff);
    }

    private static JsonArray states(final String... states) {
        final JsonArray array = new JsonArray();
        for (final String state : states) {
            array.add(state);
        }
        return array;
    }
}
