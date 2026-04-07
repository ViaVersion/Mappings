package com.viaversion.mappingsgenerator.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.viaversion.mappingsgenerator.MappingsOptimizer.MAPPINGS_DIR;

/**
 * Utility to generate `itemnames` mappings for VB.
 */
// TODO Just directly write to the file
// TODO or better, let diff stubs handle it
// TODO also entity names
public final class VBItemNamesMapper {

    public static void main(final String[] args) throws IOException {
        final String newVer = "26.2";
        final String oldVer = "26.1";
        final JsonObject oldMapping = MappingsLoader.load("mapping-" + oldVer + ".json");
        final JsonObject newMapping = MappingsLoader.load("mapping-" + newVer + ".json");
        final JsonObject diffMapping = MappingsLoader.load(MAPPINGS_DIR.resolve("diff")
            .resolve("mapping-" + newVer + "to" + oldVer + ".json"), "itemnames");
        final JsonObject missing = new JsonObject();

        final Set<String> oldItems = new HashSet<>();
        for (final JsonElement element : oldMapping.getAsJsonArray("items")) {
            oldItems.add(element.getAsString());
        }

        for (final JsonElement element : newMapping.getAsJsonArray("items")) {
            final String namespacedKey = element.getAsString();
            if (oldItems.contains(namespacedKey)) continue;
            if (diffMapping != null && diffMapping.has(namespacedKey)) continue;

            missing.addProperty(namespacedKey, newVer + " " + nameFromIdentifier(namespacedKey));
        }

        System.out.println(missing);
    }

    private static String nameFromIdentifier(final String identifier) {
        final StringBuilder result = new StringBuilder();
        for (final String split : identifier.replace("minecraft:", "").split("_")) {
            result.append(' ').append(Character.toUpperCase(split.charAt(0))).append(split.substring(1));
        }
        return result.substring(1);
    }
}
