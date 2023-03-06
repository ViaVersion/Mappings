/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023 ViaVersion and contributors
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

import com.viaversion.mappingsgenerator.util.ServerJarUtil;
import com.viaversion.mappingsgenerator.util.Version;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManualRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualRunner.class.getSimpleName());
    private static final Set<String> SPECIAL_BACKWARDS_ONLY = Set.of("1.9.4", "1.10", "1.11");
    private static final boolean ALL = true;

    public static void main(final String[] args) throws IOException {
        if (ALL) {
            runAll();
            return;
        }

        final String from = "1.13.2";
        final String to = "1.13";
        MappingsOptimizer mappingsOptimizer = new MappingsOptimizer(from, to);
        mappingsOptimizer.writeDiffStubs();
        mappingsOptimizer.optimizeAndWrite();

        //mappingsOptimizer = new MappingsOptimizer(to, from);
        //mappingsOptimizer.writeDiffStubs();
        //mappingsOptimizer.optimizeAndWrite();
    }

    /**
     * Runs the optimizer for all mapping files present in the mappings/ directory.
     */
    public static void runAll() throws IOException {
        final List<String> versions = new ArrayList<>();
        for (final File file : MappingsOptimizer.MAPPINGS_DIR.toFile().listFiles()) {
            final String name = file.getName();
            if (name.startsWith("mapping-")) {
                final String version = name.substring("mapping-".length(), name.length() - ".json".length());
                versions.add(version);
            }
        }

        versions.sort(Version::compare);

        for (int i = 0; i < versions.size() - 1; i++) {
            final String from = versions.get(i);
            final String to = versions.get(i + 1);
            LOGGER.info("=============================");
            if (from.equals("1.12") && to.equals("1.13")) {
                CursedMappings.optimizeAndSaveOhSoSpecial1_12AsNBT();
                CursedMappings.optimizeAndSaveOhSoSpecial1_12AsNBTBackwards();
                continue;
            }

            final boolean special = SPECIAL_BACKWARDS_ONLY.contains(from);
            if (!special) {
                new MappingsOptimizer(from, to).optimizeAndWrite();
                LOGGER.info("-----------------------------");
            }

            final MappingsOptimizer backwardsOptimizer = new MappingsOptimizer(to, from);
            if (special) {
                backwardsOptimizer.ignoreMissingMappingsFor("sounds");
            }

            backwardsOptimizer.optimizeAndWrite();
            LOGGER.info("");
        }
    }

    private static void runMappingsGen() throws Exception {
        MappingsGenerator.cleanup();

        try {
            // Server jar bundle since 21w39a
            // Alternatively, java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
            System.setProperty("bundlerMainClass", "net.minecraft.data.Main");
            Class.forName("net.minecraft.bundler.Main").getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--reports"});
            ServerJarUtil.waitForServerMain();
        } catch (final ClassNotFoundException ignored) {
            final Class<?> mainClass = Class.forName("net.minecraft.data.Main");
            mainClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[]{"--reports"});
        }

        MappingsGenerator.collectMappings("23w08a");
    }
}
