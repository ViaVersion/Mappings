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

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
    private static final Pattern SEM_VER = Pattern.compile("(?<a>0|[1-9]\\d*)\\.(?<b>0|[1-9]\\d*)(?:\\.(?<c>0|[1-9]\\d*))?(?:-(?<tag>[A-z0-9.-]*))?");
    private final int[] parts = new int[3];
    private final String tag;

    public Version(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Version can not be null");
        }

        final Matcher matcher = SEM_VER.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format");
        }

        parts[0] = Integer.parseInt(matcher.group("a"));
        parts[1] = Integer.parseInt(matcher.group("b"));
        parts[2] = matcher.group("c") == null ? 0 : Integer.parseInt(matcher.group("c"));

        tag = matcher.group("tag") == null ? "" : matcher.group("tag");
    }

    public static boolean isBackwards(final String from, final String to) {
        return compare(from, to) > 0;
    }

    public static int compare(final String from, final String to) {
        try {
            return new Version(from).compareTo(new Version(to));
        } catch (final IllegalArgumentException ignored) {
            return from.compareTo(to);
        }
    }

    /**
     * Compare two versions
     *
     * @param verA The first version
     * @param verB The second
     * @return 0 if they are the same, 1 if versionA is newer, -1 if versionA is older
     */
    public static int compare(final Version verA, final Version verB) {
        if (verA == verB) return 0;
        if (verA == null) return -1;
        if (verB == null) return 1;

        final int max = Math.max(verA.parts.length, verB.parts.length);

        for (int i = 0; i < max; i += 1) {
            final int partA = i < verA.parts.length ? verA.parts[i] : 0;
            final int partB = i < verB.parts.length ? verB.parts[i] : 0;
            if (partA < partB) return -1;
            if (partA > partB) return 1;
        }

        // Simple tag check
        if (verA.tag.isEmpty() && !verB.tag.isEmpty()) {
            return 1;
        }
        if (!verA.tag.isEmpty() && verB.tag.isEmpty()) {
            return -1;
        }

        return 0;
    }

    /**
     * Check if a version is the same
     *
     * @param verA The first
     * @param verB The second
     * @return True if they are the same
     */
    public static boolean equals(final Version verA, final Version verB) {
        return verA == verB || verA != null && verB != null && compare(verA, verB) == 0;
    }

    @Override
    public int compareTo(final Version that) {
        return compare(this, that);
    }

    @Override
    public boolean equals(final Object that) {
        return that instanceof Version && equals(this, (Version) that);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tag);
        result = 31 * result + Arrays.hashCode(parts);
        return result;
    }
}