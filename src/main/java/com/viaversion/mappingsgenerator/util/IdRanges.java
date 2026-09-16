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
package com.viaversion.mappingsgenerator.util;

import com.viaversion.nbt.tag.ByteArrayTag;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Reads sets of non-negative ints stored as ranges, packed into a byte array of varints.
 * Each range is stored as an offset and length pair. The offset is the start of the first range and the
 * difference to the previous range's exclusive end; the length is the inclusive end minus the start.
 */
public final class IdRanges {

    public static ByteArrayTag encode(final IntList ids) {
        if (ids.isEmpty()) {
            return new ByteArrayTag(new byte[0]);
        }

        ids.sort(null);

        final ByteArrayList out = new ByteArrayList();
        int start = ids.getInt(0);
        int prevEnd = -1;
        int prev = start;
        for (int i = 1; i <= ids.size(); i++) {
            final int id = i != ids.size() ? ids.getInt(i) : -1;
            if (id == prev) {
                throw new IllegalArgumentException("Duplicate id " + id);
            }
            if (id != prev + 1) {
                VarInts.write(out, prevEnd == -1 ? start : start - prevEnd);
                VarInts.write(out, prev - start);
                prevEnd = prev + 1;
                start = id;
            }
            prev = id;
        }
        return new ByteArrayTag(out.toByteArray());
    }
}
