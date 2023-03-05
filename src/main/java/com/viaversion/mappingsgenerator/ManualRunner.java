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

public final class ManualRunner {

    private static final boolean ALL = false;

    public static void main(final String[] args) throws IOException {
        if (ALL) {
            MappingsOptimizer.runAll();
            return;
        }

        final String from = "1.19.3";
        final String to = "1.19.4";
        MappingsOptimizer.optimizeAndSaveAsNBT(from, to);
        MappingsOptimizer.optimizeAndSaveAsNBT(to, from);
    }
}
