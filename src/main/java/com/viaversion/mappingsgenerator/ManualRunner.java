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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.viaversion.mappingsgenerator.util.ServerJarUtil;
import com.viaversion.mappingsgenerator.util.Version;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManualRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingsOptimizer.class.getSimpleName());

    private static final Set<String> SPECIAL_BACKWARDS_ONLY = Set.of("1.9.4", "1.10", "1.11");

    private static final boolean ALL = true;

    private static final boolean ALL_SPECIAL = true; // This will also update the identifier-table
    private static final Map<String, String> SPECIAL_VERSIONS = new LinkedHashMap<>();
    private static final Map<String, String> SPECIAL_BACKWARDS_VERSIONS = new LinkedHashMap<>();

    static {
        SPECIAL_VERSIONS.put("1.21.5", "25w14craftmine");

        SPECIAL_BACKWARDS_VERSIONS.put("3D_Shareware", "1.14");
        SPECIAL_BACKWARDS_VERSIONS.put("20w14infinite", "1.16");
        SPECIAL_BACKWARDS_VERSIONS.put("25w14craftmine", "1.21.5");
    }

    public static void main(final String[] args) throws IOException {
        if (ALL) {
            runAll(ErrorStrategy.WARN);
            MappingsOptimizer.printStats();
            return;
        }

        final String from = "1.20.3";
        final String to = "1.20.5";
        MappingsOptimizer mappingsOptimizer = new MappingsOptimizer(from, to);
        mappingsOptimizer.writeDiffStubs();
        mappingsOptimizer.optimizeAndWrite();

        mappingsOptimizer = new MappingsOptimizer(to, from);
        mappingsOptimizer.writeDiffStubs();
        mappingsOptimizer.optimizeAndWrite();
    }

    /**
     * Runs the optimizer for all mapping files present in the 'mappings' directory.
     */
    public static void runAll(final ErrorStrategy errorStrategy) throws IOException {
        // Going backwards wil result in less index shifts in the versions that matter most/have the most entries
        final List<String> versions = allVersions();
        for (int i = versions.size() - 1; i > 0; i--) {
            final String from = versions.get(i - 1);
            final String to = versions.get(i);
            if (from.equals("1.12") && to.equals("1.13")) {
                CursedMappings.optimizeAndSaveOhSoSpecial1_12AsNBTBackwards();
                CursedMappings.optimizeAndSaveOhSoSpecial1_12AsNBT();
                continue;
            }

            final boolean special = SPECIAL_BACKWARDS_ONLY.contains(from);

            final MappingsOptimizer backwardsOptimizer = new MappingsOptimizer(to, from);
            backwardsOptimizer.setErrorStrategy(errorStrategy);
            if (special) {
                backwardsOptimizer.ignoreMissingMappingsFor("sounds");
            }
            backwardsOptimizer.optimizeAndWrite();

            if (!special) {
                final MappingsOptimizer mappingsOptimizer = new MappingsOptimizer(from, to);
                mappingsOptimizer.setErrorStrategy(errorStrategy);
                mappingsOptimizer.optimizeAndWrite();
            }
        }

        if (ALL_SPECIAL) {
            for (Map.Entry<String, String> entry : SPECIAL_BACKWARDS_VERSIONS.entrySet()) {
                final MappingsOptimizer mappingsOptimizer = new MappingsOptimizer(entry.getKey(), entry.getValue(), true, false);
                mappingsOptimizer.setErrorStrategy(errorStrategy);
                mappingsOptimizer.optimizeAndWrite();
            }
            for (Map.Entry<String, String> entry : SPECIAL_VERSIONS.entrySet()) {
                final MappingsOptimizer mappingsOptimizer = new MappingsOptimizer(entry.getKey(), entry.getValue(), false, true);
                mappingsOptimizer.setErrorStrategy(errorStrategy);
                mappingsOptimizer.optimizeAndWrite();
            }
        }

        int totalSize = 0;
        for (final Map.Entry<String, JsonElement> entry : MappingsOptimizer.fileHashesObject.entrySet()) {
            final JsonPrimitive size = entry.getValue().getAsJsonObject().getAsJsonPrimitive("size");
            totalSize += size.getAsInt();
        }
        LOGGER.info("Total size of all mapping and identifier files: {}kb", totalSize / 1024);
    }

    private static List<String> allVersions() {
        final List<String> versions = new ArrayList<>();
        for (final File file : MappingsOptimizer.MAPPINGS_DIR.toFile().listFiles()) {
            final String name = file.getName();
            if (name.startsWith("mapping-")) {
                final String version = name.substring("mapping-".length(), name.length() - ".json".length());
                versions.add(version);
            }
        }
        versions.sort(Version::compare);
        return versions;
    }

    private static void runMappingsGen() throws Exception {
        MappingsGenerator.cleanup();

        try {
            // Server jar bundle since 21w39a
            // Alternatively, java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --all
            System.setProperty("bundlerMainClass", "net.minecraft.data.Main");
            Class.forName("net.minecraft.bundler.Main").getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--all"});
            ServerJarUtil.waitForServerMain();
        } catch (final ClassNotFoundException ignored) {
            final Class<?> mainClass = Class.forName("net.minecraft.data.Main");
            mainClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--reports"});
        }

        MappingsGenerator.collectMappings("1.21.2-pre3");
    }
}
