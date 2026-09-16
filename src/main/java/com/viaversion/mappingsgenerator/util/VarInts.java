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

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.nio.ByteBuffer;

public final class VarInts {

    public static int read(final ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static void write(final ByteArrayList out, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative value " + value);
        }
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.add((byte) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        out.add((byte) remaining);
    }

    public static int readZigZag(final ByteBuffer buf) {
        final int value = read(buf);
        return (value >>> 1) ^ -(value & 1);
    }

    public static void writeZigZag(final ByteArrayList out, final int value) {
        write(out, (value << 1) ^ (value >> 31));
    }
}
