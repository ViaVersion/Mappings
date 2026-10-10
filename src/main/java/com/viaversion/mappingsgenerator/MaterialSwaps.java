/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023-2026 ViaVersion and contributors
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
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Material swaps from the {@code materials} section of a diff file, e.g. {@code "poplar": "birch"}.
 * <p>
 * A swap replaces the material's words in an identifier, e.g. {@code stripped_poplar_log} → {@code stripped_birch_log}.
 * Words are separated by {@code _}, {@code .}, {@code /} or {@code :}, and materials only match whole words. The material
 * with the most words wins, multiple different materials with the same number of words are ambiguous.
 * Object values can also replace other words of matching identifiers:
 * <pre>{@code
 * "crimson": {"to": "acacia", "words": {"stem": "log", "hyphae": "wood"}}
 * }</pre>
 */
final class MaterialSwaps {

    private final List<Material> materials;
    private final Set<String> usedMaterials = new HashSet<>();

    private MaterialSwaps(final List<Material> materials) {
        this.materials = materials;
    }

    static @Nullable MaterialSwaps of(@Nullable final JsonObject diffObject) {
        if (diffObject == null || !diffObject.has("materials")) {
            return null;
        }

        final List<Material> materials = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> entry : diffObject.getAsJsonObject("materials").entrySet()) {
            materials.add(Material.parse(entry.getKey(), entry.getValue()));
        }
        materials.sort(Comparator.comparingInt((Material material) -> material.words().length).reversed());
        return new MaterialSwaps(materials);
    }

    /**
     * Returns the identifier with its material swapped, without blockstate properties, or null if no material matches.
     *
     * @param identifier source identifier
     * @return swap, or null if none or multiple materials match
     */
    @Nullable Swap swap(final String identifier) {
        final int dataIndex = identifier.indexOf('[');
        final String base = dataIndex == -1 ? identifier : identifier.substring(0, dataIndex);
        final List<String> words = new ArrayList<>();
        final List<Character> separators = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < base.length(); i++) {
            final char c = base.charAt(i);
            if (c == '_' || c == '.' || c == '/' || c == ':') {
                words.add(base.substring(start, i));
                separators.add(c);
                start = i + 1;
            }
        }
        words.add(base.substring(start));

        Material match = null;
        for (final Material material : materials) {
            if (match != null && material.words().length < match.words().length) {
                break;
            }
            if (material.indexIn(words, 0) == -1) {
                continue;
            }
            if (match != null) {
                return null; // Ambiguous
            }
            match = material;
        }
        if (match == null) {
            return null;
        }

        final StringBuilder builder = new StringBuilder();
        int i = 0;
        while (i < words.size()) {
            if (match.indexIn(words, i) == i) {
                builder.append(match.to());
                i += match.words().length;
            } else {
                builder.append(match.replacedWords().getOrDefault(words.get(i), words.get(i)));
                i++;
            }
            if (i < words.size()) {
                builder.append(separators.get(i - 1));
            }
        }
        return new Swap(match.name(), builder.toString());
    }

    void markUsed(final String material) {
        usedMaterials.add(material);
    }

    List<String> unusedMaterials() {
        return materials.stream().map(Material::name).filter(name -> !usedMaterials.contains(name)).toList();
    }

    record Swap(String material, String target) {

        String base(final String identifier) {
            final int dataIndex = identifier.indexOf('[');
            return dataIndex == -1 ? identifier : identifier.substring(0, dataIndex);
        }
    }

    private record Material(String name, String[] words, String to, Map<String, String> replacedWords) {

        static Material parse(final String name, final JsonElement element) {
            String to = null;
            final Map<String, String> replacedWords = new HashMap<>();
            if (element.isJsonPrimitive()) {
                to = element.getAsString();
            } else {
                for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    switch (entry.getKey()) {
                        case "to" -> to = entry.getValue().getAsString();
                        case "words" -> {
                            for (final Map.Entry<String, JsonElement> word : entry.getValue().getAsJsonObject().entrySet()) {
                                replacedWords.put(word.getKey(), word.getValue().getAsString());
                            }
                        }
                        default -> throw new IllegalArgumentException("Unknown field " + entry.getKey() + " in material " + name);
                    }
                }
            }

            if (name.isEmpty() || to == null || to.isEmpty()) {
                throw new IllegalArgumentException("Invalid material swap " + name + ": " + element);
            }
            return new Material(name, name.split("_"), to, replacedWords);
        }

        int indexIn(final List<String> identifierWords, final int from) {
            for (int i = from; i <= identifierWords.size() - words.length; i++) {
                if (Arrays.equals(words, identifierWords.subList(i, i + words.length).toArray())) {
                    return i;
                }
            }
            return -1;
        }
    }
}
