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

import java.io.IOException;

public final class CursedMappings {

    public static void optimizeAndSaveOhSoSpecial1_12AsNBT() throws IOException {
        final MappingsOptimizer optimizer = new MappingsOptimizer("1.12", "1.13");
        optimizer.handleUnknownFields();
        optimizer.cursedMappings("blocks", "blockstates", "blockstates", 4084);
        optimizer.cursedMappings("items", "items", "items");
        optimizer.cursedMappings("legacy_enchantments", "enchantments", "enchantments", 72);
        optimizer.mappings(true, false, "sounds");
        optimizer.write(MappingsOptimizer.OUTPUT_DIR);
    }

    public static void optimizeAndSaveOhSoSpecial1_12AsNBTBackwards() throws IOException {
        final MappingsOptimizer optimizer = new MappingsOptimizer("1.13", "1.12");
        optimizer.handleUnknownFields();
        optimizer.cursedMappings("blockstates", "blocks", "blockstates", 8582);
        optimizer.cursedMappings("items", "items", "items");
        optimizer.cursedMappings("enchantments", "legacy_enchantments", "enchantments");
        optimizer.names("items", "itemnames");
        optimizer.fullNames("entitynames", "entitynames");
        optimizer.fullNames("sounds", "soundnames");
        optimizer.mappings(true, false, "sounds");
        optimizer.write(MappingsOptimizer.OUTPUT_BACKWARDS_DIR);
    }
}
