package com.viaversion.mappingsgenerator.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import java.io.IOException;

public final class IdByStringPrinter {

    private static final String VERSION = "26.2";
    private static final String DATA_TYPE = "blockstates";
    private static final String LOOKING_FOR_ID = "white_carpet";

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-" + VERSION + ".json");
        final JsonArray array = mappings.getAsJsonArray(DATA_TYPE);
        int i = 0;
        for (final JsonElement element : array) {
            if (element.getAsString().equals(LOOKING_FOR_ID)) {
                System.out.println(i);
                return;
            }
            i++;
        }
    }
}
