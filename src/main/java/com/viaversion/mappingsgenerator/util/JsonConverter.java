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
package com.viaversion.mappingsgenerator.util;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;

public final class JsonConverter {

    public static Tag toTag(final JsonElement element) {
        if (element.isJsonObject()) {
            final JsonObject object = element.getAsJsonObject();
            final CompoundTag tag = new CompoundTag();
            for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
                tag.put(entry.getKey(), toTag(entry.getValue()));
            }
            return tag;
        } else if (element.isJsonArray()) {
            final JsonArray array = element.getAsJsonArray();
            // Special case int arrays
            if (!array.isEmpty() && array.get(0).isJsonPrimitive() && array.get(0).getAsJsonPrimitive().isNumber()) {
                final int[] ints = new int[array.size()];
                for (int i = 0; i < array.size(); i++) {
                    ints[i] = array.get(i).getAsInt();
                }
                return new IntArrayTag(ints);
            }

            final ListTag tag = new ListTag();
            for (final JsonElement arrayElement : array) {
                tag.add(toTag(arrayElement));
            }
            return tag;
        } else if (element.isJsonPrimitive()) {
            final JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return new IntTag(primitive.getAsInt());
            } else if (primitive.isString()) {
                return new StringTag(primitive.getAsString());
            } else if (primitive.isBoolean()) {
                return new ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));
            }
        } else if (element.isJsonNull()) {
            return new StringTag("null");
        }
        throw new IllegalArgumentException("Unknown element " + element.getClass());
    }

    public static JsonObject toJsonObject(final JsonArray array) {
        final JsonObject object = new JsonObject();
        for (int i = 0; i < array.size(); i++) {
            final JsonElement element = array.get(i);
            object.add(Integer.toString(i), element);
        }
        return object;
    }

    public static JsonObject toJsonObject(final JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else if (element.isJsonArray()) {
            return toJsonObject(element.getAsJsonArray());
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
