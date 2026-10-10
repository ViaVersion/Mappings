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
        "slab[type=top,waterlogged=true]"
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
