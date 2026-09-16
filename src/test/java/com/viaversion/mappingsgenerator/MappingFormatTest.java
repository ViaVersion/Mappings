package com.viaversion.mappingsgenerator;

import com.viaversion.mappingsgenerator.MappingsLoader.MappingsResult;
import com.viaversion.mappingsgenerator.util.VarInts;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.ByteArrayTag;
import com.viaversion.nbt.tag.ByteTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.Tag;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the packed mapping formats decode back to exactly the data they were encoded from.
 * Mirrors ViaVersion's MappingDataLoader and has to be kept in sync with it.
 */
final class MappingFormatTest {

    @Test
    void testStorageFormatsRoundTrip() {
        final List<int[]> cases = List.of(
            new int[]{0}, // Single identity entry
            new int[]{7}, // Single shifted entry
            new int[]{0, 1, 2, 90, 4, 5, 6}, // Middle change
            new int[]{10, 11, 12, 13}, // Shift starting at the first id
            new int[]{0, -1, -1, 3, 4}, // Consecutive unmapped entries
            new int[]{5, 3, 1, 0, 2}, // Descending values with negative deltas
            new int[]{1_000_000, 2_000_000, 0, 3_000_000}, // Multi-byte varints
            new int[]{-1, -1, -1} // Fully unmapped
        );
        for (final int[] mappings : cases) {
            final String name = Arrays.toString(mappings);
            Assertions.assertArrayEquals(mappings, decodeDirect(MappingsOptimizer.directValues(mappings), mappings.length), name);

            final MappingsResult result = result(mappings);
            final int changes = mappings.length - result.identityMappings();
            final int[][] changedPairs = decodePairs(MappingsOptimizer.changedValues(result, changes));
            Assertions.assertArrayEquals(mappings, reconstructChanged(changedPairs, mappings.length), name);

            final int[][] shiftPairs = decodePairs(MappingsOptimizer.shiftValues(result, name));
            Assertions.assertArrayEquals(mappings, reconstructShifts(shiftPairs, mappings.length), name);
        }
    }

    @Test
    void testOutputFilesRoundTrip() throws IOException {
        if (!Files.exists(MappingsOptimizer.OUTPUT_DIR.resolve("identifier-table.nbt"))) {
            ManualRunner.regenerateNbtOutputFiles(ErrorStrategy.ERROR);
        }

        int sections = 0;
        try (final Stream<Path> stream = Files.walk(MappingsOptimizer.OUTPUT_DIR)) {
            for (final Path path : stream.filter(path -> {
                final String name = path.getFileName().toString();
                return name.startsWith("mappings-") || name.startsWith("identifiers-");
            }).toList()) {
                final CompoundTag tag;
                try (final BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                    tag = NBTIO.reader(CompoundTag.class).named().read(in);
                }
                sections += checkSections(tag, path.getFileName().toString());
            }
        }
        Assertions.assertTrue(sections > 100, "Didn't check enough sections: " + sections);
    }

    /**
     * Checks for every packed mappings compound that decoding its value array and encoding the
     * result again reproduces the exact same bytes.
     *
     * @return the number of checked sections
     */
    private static int checkSections(final CompoundTag tag, final String name) {
        int sections = 0;
        for (final Map.Entry<String, Tag> entry : tag.entrySet()) {
            if (!(entry.getValue() instanceof final CompoundTag childTag) || !(childTag.get("id") instanceof final ByteTag idTag)) {
                continue;
            }

            final String at = name + "/" + entry.getKey();
            final byte strategy = idTag.asByte();
            if (strategy == MappingsOptimizer.IDENTITY_ID) {
                continue;
            }

            final byte[] values = childTag.getByteArrayTag("val").getValue();
            final ByteArrayTag reencoded;
            if (strategy == MappingsOptimizer.DIRECT_ID) {
                reencoded = MappingsOptimizer.directValues(decodeDirect(new ByteArrayTag(values), childTag.getInt("size")));
            } else if (strategy == MappingsOptimizer.CHANGES_ID || strategy == MappingsOptimizer.SHIFTS_ID) {
                final int[][] pairs = decodePairs(new ByteArrayTag(values));
                reencoded = MappingsOptimizer.atValuePairs(pairs[0], pairs[1]);
            } else {
                throw new IllegalArgumentException("Unknown storage strategy " + strategy + " in " + at);
            }

            Assertions.assertArrayEquals(values, reencoded.getValue(), at);
            sections++;
        }
        return sections;
    }

    private static MappingsResult result(final int[] mappings) {
        int emptyMappings = 0;
        int identityMappings = 0;
        int shiftChanges = mappings[0] != 0 ? 1 : 0;
        for (int id = 0; id < mappings.length; id++) {
            if (mappings[id] == -1) {
                emptyMappings++;
            }
            if (mappings[id] == id) {
                identityMappings++;
            }
            if (id > 0 && mappings[id] != mappings[id - 1] + 1) {
                shiftChanges++;
            }
        }
        return new MappingsResult(mappings, -1, emptyMappings, identityMappings, shiftChanges);
    }

    private static int[] decodeDirect(final ByteArrayTag valuesTag, final int size) {
        final ByteBuffer buf = ByteBuffer.wrap(valuesTag.getValue());
        final int[] mappings = new int[size];
        int prev = 0;
        for (int i = 0; i < size; i++) {
            prev += VarInts.readZigZag(buf);
            mappings[i] = prev;
        }
        Assertions.assertFalse(buf.hasRemaining(), "Leftover bytes after direct values");
        return mappings;
    }

    private static int[][] decodePairs(final ByteArrayTag valuesTag) {
        final ByteBuffer buf = ByteBuffer.wrap(valuesTag.getValue());
        final IntList at = new IntArrayList();
        final IntList values = new IntArrayList();
        int prevAt = -1;
        int prevValue = 0;
        while (buf.hasRemaining()) {
            prevAt = prevAt + 1 + VarInts.read(buf);
            prevValue += VarInts.readZigZag(buf);
            at.add(prevAt);
            values.add(prevValue);
        }
        return new int[][]{at.toIntArray(), values.toIntArray()};
    }

    private static int[] reconstructChanged(final int[][] pairs, final int size) {
        final int[] mappings = new int[size];
        for (int id = 0; id < size; id++) {
            mappings[id] = id;
        }
        for (int i = 0; i < pairs[0].length; i++) {
            mappings[pairs[0][i]] = pairs[1][i];
        }
        return mappings;
    }

    private static int[] reconstructShifts(final int[][] pairs, final int size) {
        final int[] at = pairs[0];
        final int[] to = pairs[1];
        final int[] mappings = new int[size];
        for (int id = 0; id < (at.length != 0 ? at[0] : size); id++) {
            mappings[id] = id;
        }
        for (int i = 0; i < at.length; i++) {
            final int end = i == at.length - 1 ? size : at[i + 1];
            int mappedId = to[i];
            for (int id = at[i]; id < end; id++) {
                mappings[id] = mappedId++;
            }
        }
        return mappings;
    }
}
