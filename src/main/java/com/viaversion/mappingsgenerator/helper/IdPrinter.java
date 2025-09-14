package com.viaversion.mappingsgenerator.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import java.io.IOException;

public final class IdPrinter {

    private static final String VERSION = "1.19.3";
    private static final String DATA_TYPE = "blockstates";
    private static final int LOOKING_FOR_ID = 1525;

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-" + VERSION + ".json");
        final JsonArray array = mappings.getAsJsonArray(DATA_TYPE);
        System.out.println(array.get(LOOKING_FOR_ID));
    }
}
