package com.viaversion.mappingsgenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class DiffMapperTest {

    private static final JsonArray TARGET_STATES = states(
        "stone",
        "sign[rotation=0,waterlogged=false]",
        "sign[rotation=0,waterlogged=true]",
        "sign[rotation=1,waterlogged=false]",
        "sign[rotation=1,waterlogged=true]",
        "slab[type=bottom,waterlogged=false]",
        "slab[type=bottom,waterlogged=true]",
        "slab[type=top,waterlogged=false]",
        "slab[type=top,waterlogged=true]",
        "wall[east=false,up=false]",
        "wall[east=false,up=true]",
        "wall[east=true,up=false]",
        "wall[east=true,up=true]",
        "log[axis=x]",
        "log[axis=y]"
    );

    @Test
    void testUnresolvedTargets() {
        // Target property that can't be filled
        assertMapped("{\"sensor\": \"slab\"}", "sensor[power=3,waterlogged=true]", null);
        // Copied value that is invalid for the target
        assertMapped("{\"sensor\": \"sign\"}", "sensor[rotation=7,waterlogged=true]", null);
        // Unknown target property
        assertMapped("{\"sensor\": \"slab[type=bottom,foo=bar]\"}", "sensor[waterlogged=true]", null);
    }

    @Test
    void testPartialKeys() {
        final String diff = """
            {
              "sensor": "slab[type=bottom]",
              "sensor[power=1]": "slab[type=top]",
              "sensor[power=1,waterlogged=true]": "stone"
            }""";
        // Exact keys, then the matching key with the most properties, then the bare key
        assertMapped(diff, "sensor[power=1,waterlogged=true]", "stone");
        assertMapped(diff, "sensor[power=1,waterlogged=false]", "slab[type=top,waterlogged=false]");
        assertMapped(diff, "sensor[power=0,waterlogged=true]", "slab[type=bottom,waterlogged=true]");

        // Same number of matching properties with different values
        final String ambiguousDiff = "{\"sensor[power=1]\": \"slab[type=top]\", \"sensor[waterlogged=true]\": \"stone\"}";
        assertMapped(ambiguousDiff, "sensor[power=1,waterlogged=true]", null);
        Assertions.assertThrows(RuntimeException.class, () -> MappingsLoader.map(states("sensor[power=1,waterlogged=true]"), TARGET_STATES,
            JsonParser.parseString(ambiguousDiff).getAsJsonObject(), ErrorStrategy.ERROR));
    }

    @Test
    void testRules() {
        // Explicit target properties win over remapped ones
        assertMapped("{\"wall\": {\"to\": \"wall[east=false]\", \"remap\": {\"east\": {\"low\": \"true\"}}}}", "wall[east=low,up=true]", "wall[east=false,up=true]");
        // Remap and rename both use source property names
        assertMapped("{\"pillar\": {\"to\": \"log\", \"rename\": {\"facing\": \"axis\"}, \"remap\": {\"facing\": {\"east\": \"x\", \"up\": \"y\"}}}}", "pillar[facing=up]", "log[axis=y]");

        // Unknown or conflicting properties
        assertMapped("{\"wall\": {\"to\": \"wall\", \"remap\": {\"north\": {\"none\": \"false\"}}}}", "wall[east=true,up=true]", null);
        assertMapped("{\"wall\": {\"to\": \"wall\", \"rename\": {\"north\": \"east\"}}}", "wall[east=true,up=true]", null);
        assertMapped("{\"wall\": {\"to\": \"wall\", \"rename\": {\"up\": \"east\"}}}", "wall[east=true,up=true]", null);
        // Invalid rules
        Assertions.assertThrows(IllegalArgumentException.class, () -> assertMapped("{\"wall\": {\"target\": \"wall\"}}", "wall[east=true,up=true]", null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> assertMapped("{\"wall\": {\"remap\": {}}}", "wall[east=true,up=true]", null));
    }

    private static void assertMapped(final String diff, final String source, final String expectedTarget) {
        final JsonObject diffObject = JsonParser.parseString(diff).getAsJsonObject();
        final MappingsLoader.MappingsResult result = MappingsLoader.map(states(source), TARGET_STATES, diffObject, ErrorStrategy.IGNORE);
        final int expectedId = expectedTarget != null ? MappingsLoader.arrayToMap(TARGET_STATES).getInt(expectedTarget) : -1;
        Assertions.assertNotEquals(-1, expectedTarget != null ? expectedId : 0, "Unknown expected target " + expectedTarget);
        Assertions.assertEquals(expectedId, result.mappings()[0], source + " with " + diff);
    }

    private static JsonArray states(final String... states) {
        final JsonArray array = new JsonArray();
        for (final String state : states) {
            array.add(state);
        }
        return array;
    }
}
