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

import com.github.steveice10.opennbt.NBTIO;
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
import com.viaversion.mappingsgenerator.MappingsLoader.MappingsResult;
import com.viaversion.mappingsgenerator.util.JsonConverter;
import com.viaversion.mappingsgenerator.util.Version;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optimizes mapping files as nbt files with only the necessary data (mostly int to int mappings in form of int arrays).
 */
public final class MappingsOptimizer {

    public static final int VERSION = 1;
    public static final byte DIRECT_ID = 0;
    public static final byte SHIFTS_ID = 1;
    public static final byte CHANGES_ID = 2;
    public static final byte IDENTITY_ID = 3;
    public static final Path MAPPINGS_DIR = Path.of("mappings");
    public static final Path OUTPUT_DIR = Path.of("output");
    public static final Path OUTPUT_BACKWARDS_DIR = OUTPUT_DIR.resolve("backwards");
    public static final String DIFF_FILE_FORMAT = "diff/mapping-%sto%s.json";
    public static final String MAPPING_FILE_FORMAT = "mapping-%s.json";
    public static final String OUTPUT_FILE_FORMAT = "mappings-%sto%s.nbt";
    public static final String OUTPUT_IDENTIFIERS_FILE_FORMAT = "identifiers-%s.nbt";

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingsOptimizer.class.getSimpleName());
    private static final Set<String> STANDARD_FIELDS = Set.of("blockstates", "blocks", "items", "sounds", "blockentities", "enchantments", "paintings", "entities", "particles", "argumenttypes", "statistics", "tags");
    private static final Set<String> SAVED_IDENTIFIER_FILES = new HashSet<>();

    private final Set<String> ignoreMissing = new HashSet<>(Arrays.asList("blocks", "statistics"));
    private final CompoundTag output;
    private final String fromVersion;
    private final String toVersion;
    private final JsonObject unmappedObject;
    private final JsonObject mappedObject;
    private JsonObject diffObject;
    private boolean keepUnknownFields;

    public static void main(final String[] args) throws IOException {
        if (args.length < 2) {
            LOGGER.error("Required args: from version, to version");
            System.exit(1);
        }

        Files.createDirectories(MAPPINGS_DIR);
        Files.createDirectories(OUTPUT_DIR);

        final Set<String> argsSet = new HashSet<>(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
        final String from = args[0];
        final String to = args[1];

        final MappingsOptimizer optimizer = new MappingsOptimizer(from, to);
        if (argsSet.contains("--generateDiffStubs")) {
            optimizer.writeDiffStubs();
        }
        if (argsSet.contains("--keepUnknownFields")) {
            optimizer.keepUnknownFields();
        }
        optimizer.optimizeAndWrite();
    }

    /**
     * Creates a new MappingsOptimizer instance.
     *
     * @param from version to map from
     * @param to   version to map to
     * @see #optimizeAndWrite()
     */
    public MappingsOptimizer(final String from, final String to) throws IOException {
        this.fromVersion = from;
        this.toVersion = to;
        output = new CompoundTag();
        output.put("version", new IntTag(VERSION));

        unmappedObject = MappingsLoader.load(MAPPING_FILE_FORMAT.formatted(from));
        if (unmappedObject == null) {
            throw new IllegalArgumentException("Mapping file for version " + from + " does not exist");
        }

        mappedObject = MappingsLoader.load(MAPPING_FILE_FORMAT.formatted(to));
        if (mappedObject == null) {
            throw new IllegalArgumentException("Mapping file for version " + to + " does not exist");
        }

        diffObject = MappingsLoader.load(DIFF_FILE_FORMAT.formatted(from, to));
    }

    /**
     * Optimizes mapping files as nbt files with only the necessary data (int to int mappings in form of int arrays).
     */
    public void optimizeAndWrite() throws IOException {
        LOGGER.info("Compacting json mapping files for versions {} → {}...", fromVersion, toVersion);

        if (keepUnknownFields) {
            handleUnknownFields();
        }

        mappings(true, "blockstates");
        mappings(false, "blocks");
        mappings(false, "items");
        mappings(false, "sounds");
        mappings(false, "blockentities");
        mappings(false, "enchantments");
        mappings(false, "paintings");
        mappings(false, "entities");
        mappings(false, "particles");
        mappings(false, "argumenttypes");
        mappings(false, "statistics");

        if (diffObject != null) {
            names("items", "itemnames");
            fullNames("entitynames", "entitynames");

            if (Version.isBackwards(fromVersion, toVersion)) {
                fullNames("sounds", "soundnames");
            }

            if (diffObject.has("tags")) {
                tags();
            }
        }

        write(Version.isBackwards(fromVersion, toVersion) ? OUTPUT_BACKWARDS_DIR : OUTPUT_DIR);

        // Save full identifiers to a separate file per version
        saveIdentifierFiles(fromVersion, unmappedObject);
        saveIdentifierFiles(toVersion, mappedObject);
    }

    /**
     * Writes a diff file with empty mappings for all fields that require manual mapping.
     * The generated diff object will be used for further mappings generation on this instance.
     *
     * @return true if the diff stubs were written, false if they were not written because there were no changes
     */
    public boolean writeDiffStubs() throws IOException {
        final JsonObject diffObject = MappingsLoader.getDiffObjectStub(unmappedObject, mappedObject, this.diffObject, ignoreMissing);
        if (diffObject != null) {
            LOGGER.info("Writing diff stubs for versions {} → {}", fromVersion, toVersion);
            Files.writeString(MAPPINGS_DIR.resolve(DIFF_FILE_FORMAT.formatted(fromVersion, toVersion)), MappingsGenerator.GSON.toJson(diffObject));
            this.diffObject = diffObject;
            return true;
        }
        return false;
    }

    /**
     * Prevents warnings for missing diff mappings for the given key from being printed and does not include stubs in {@link #writeDiffStubs()}.
     *
     * @param key key to ignore missing mappings for
     */
    public void ignoreMissingMappingsFor(final String key) {
        ignoreMissing.add(key);
    }

    /**
     * Writing mappings will keep non-standard fields unchanged into the output file.
     */
    public void keepUnknownFields() {
        this.keepUnknownFields = true;
    }

    /**
     * Writes the current mappings output as an NBT file into the given directory.
     *
     * @param directory directory to write the output file to
     */
    public void write(final Path directory) throws IOException {
        write(output, directory.resolve(OUTPUT_FILE_FORMAT.formatted(fromVersion, toVersion)));
    }

    public void saveIdentifierFiles(final String version, final JsonObject object) throws IOException {
        final CompoundTag identifiers = new CompoundTag();
        storeIdentifiers(identifiers, object, "entities");
        storeIdentifiers(identifiers, object, "particles");
        storeIdentifiers(identifiers, object, "argumenttypes");
        if (SAVED_IDENTIFIER_FILES.add(version)) {
            write(identifiers, OUTPUT_DIR.resolve(OUTPUT_IDENTIFIERS_FILE_FORMAT.formatted(version)));
        }
    }

    /**
     * Checks for unknown fields in the unmapped object and writes them to the tag unchanged.
     */
    public void handleUnknownFields() {
        for (final String key : unmappedObject.keySet()) {
            if (STANDARD_FIELDS.contains(key)) {
                continue;
            }

            LOGGER.warn("NON-STANDARD FIELD: {} - writing it to the file without changes", key);
            final Tag asTag = JsonConverter.toTag(unmappedObject.get(key));
            output.put(key, asTag);
        }
    }

    /**
     * Reads mappings from the unmapped and mapped objects and writes them to the nbt tag.
     *
     * @param alwaysWriteIdentity whether to always write the identity mapping with size and mapped size, even if the two arrays are equal
     * @param key                 to read from and write to
     */
    public void mappings(final boolean alwaysWriteIdentity, final String key) {
        if (!unmappedObject.has(key) || !mappedObject.has(key)
                || !unmappedObject.get(key).isJsonArray() || !mappedObject.get(key).isJsonArray()) {
            return;
        }

        final JsonArray unmappedIdentifiers = unmappedObject.getAsJsonArray(key);
        final JsonArray mappedIdentifiers = mappedObject.getAsJsonArray(key);
        if (unmappedIdentifiers.equals(mappedIdentifiers) && !alwaysWriteIdentity) {
            LOGGER.debug("{}: Skipped", key);
            return;
        }

        LOGGER.debug("Mapping {}: {} → {}", key, unmappedIdentifiers.size(), mappedIdentifiers.size());
        final JsonObject diffIdentifiers = diffObject != null ? diffObject.getAsJsonObject(key) : null;
        final MappingsResult result = MappingsLoader.map(unmappedIdentifiers, mappedIdentifiers, diffIdentifiers, shouldWarn(key));
        serialize(result, output, key, alwaysWriteIdentity);
    }

    private boolean shouldWarn(final String key) {
        return !ignoreMissing.contains(key);
    }

    public void cursedMappings(final String unmappedKey, final String mappedKey, final String outputKey) {
        final JsonElement element = unmappedObject.get(unmappedKey);
        cursedMappings(unmappedKey, mappedKey, outputKey, element.isJsonArray() ? element.getAsJsonArray().size() : element.getAsJsonObject().size());
    }

    public void cursedMappings(
            final String unmappedKey,
            final String mappedKey,
            final String outputKey,
            final int size
    ) {
        final JsonObject mappedIdentifiers = JsonConverter.toJsonObject(mappedObject.get(mappedKey));
        final Int2IntMap map = MappingsLoader.map(
                JsonConverter.toJsonObject(unmappedObject.get(unmappedKey)),
                mappedIdentifiers,
                diffObject != null ? diffObject.getAsJsonObject(unmappedKey) : null,
                true
        );

        final CompoundTag changedTag = new CompoundTag();
        final int[] unmapped = new int[map.size()];
        final int[] mapped = new int[map.size()];
        int i = 0;
        for (final Int2IntMap.Entry entry : map.int2IntEntrySet()) {
            unmapped[i] = entry.getIntKey();
            mapped[i] = entry.getIntValue();
            i++;
        }

        changedTag.put("id", new ByteTag(MappingsOptimizer.CHANGES_ID));
        changedTag.put("nofill", new ByteTag((byte) 1));
        changedTag.put("size", new IntTag(size));
        changedTag.put("mappedSize", new IntTag(mappedIdentifiers.size()));
        changedTag.put("at", new IntArrayTag(unmapped));
        changedTag.put("val", new IntArrayTag(mapped));
        output.put(outputKey, changedTag);
    }

    /**
     * Writes int->string mappings to the given tag.
     *
     * @param key      key to read identifiers from
     * @param namesKey key to read names from and to write to
     */
    public void names(final String key, final String namesKey) {
        if (!unmappedObject.has(key) || !diffObject.has(namesKey)) {
            return;
        }

        final Object2IntMap<String> identifierMap = MappingsLoader.arrayToMap(unmappedObject.getAsJsonArray(key));
        final JsonObject nameMappings = diffObject.getAsJsonObject(namesKey);
        final CompoundTag tag = new CompoundTag();
        output.put(namesKey, tag);

        for (final Map.Entry<String, JsonElement> entry : nameMappings.entrySet()) {
            // Would be smaller as two arrays, but /shrug
            final String idAsString = Integer.toString(identifierMap.getInt(entry.getKey()));
            tag.put(idAsString, new StringTag(entry.getValue().getAsString()));
        }
    }

    /**
     * Writes string->string mappings to the given tag.
     *
     * @param key       key to read from
     * @param outputKey key to write to
     */
    public void fullNames(final String key, final String outputKey) {
        if (!diffObject.has(key)) {
            return;
        }

        final JsonObject nameMappings = diffObject.getAsJsonObject(key);
        final CompoundTag tag = new CompoundTag();
        output.put(outputKey, tag);

        for (final Map.Entry<String, JsonElement> entry : nameMappings.entrySet()) {
            tag.put(entry.getKey(), new StringTag(entry.getValue().getAsString()));
        }
    }

    /**
     * Writes mapped tag ids to the given tag.
     */
    public void tags() {
        final JsonObject tagsObject = diffObject.getAsJsonObject("tags");
        final CompoundTag tagsTag = new CompoundTag();
        for (final Map.Entry<String, JsonElement> entry : tagsObject.entrySet()) {
            final JsonObject object = entry.getValue().getAsJsonObject();
            final CompoundTag tag = new CompoundTag();
            final String type = entry.getKey();
            tagsTag.put(type, tag);

            final String typeKey = switch (type) {
                case "block" -> "blocks";
                case "item" -> "items";
                case "entity_types" -> "entities";
                default -> throw new IllegalArgumentException("Registry type not supported: " + type);
            };
            final JsonArray typeElements = mappedObject.get(typeKey).getAsJsonArray();
            final Object2IntMap<String> typeMap = MappingsLoader.arrayToMap(typeElements);

            for (final Map.Entry<String, JsonElement> tagEntry : object.entrySet()) {
                final JsonArray elements = tagEntry.getValue().getAsJsonArray();
                final int[] tagIds = new int[elements.size()];
                final String tagName = tagEntry.getKey();
                for (int i = 0; i < elements.size(); i++) {
                    final String element = elements.get(i).getAsString();
                    final int mappedId = typeMap.getInt(element.replace("minecraft:", ""));
                    if (mappedId == -1) {
                        LOGGER.error("Could not find id for {}", element);
                        continue;
                    }

                    tagIds[i] = mappedId;
                }

                tag.put(tagName, new IntArrayTag(tagIds));
            }
        }

        if (!tagsTag.isEmpty()) {
            output.put("tags", tagsTag);
        }
    }

    /**
     * Stores a list of string identifiers in the given tag.
     *
     * @param tag    tag to write to
     * @param object object to read identifiers from
     * @param key    to read from and write to
     */
    private static void storeIdentifiers(
            final CompoundTag tag,
            final JsonObject object,
            final String key
    ) {
        final JsonArray identifiers = object.getAsJsonArray(key);
        if (identifiers == null) {
            return;
        }

        final ListTag list = new ListTag(StringTag.class);
        for (final JsonElement identifier : identifiers) {
            list.add(new StringTag(identifier.getAsString()));
        }

        tag.put(key, list);
    }

    /**
     * Writes an int to int mappings result to the ntb tag.
     *
     * @param result              result with int to int mappings
     * @param parent              tag to write to
     * @param key                 key to write to
     * @param alwaysWriteIdentity whether to write identity mappings even if there are no changes
     */
    private static void serialize(final MappingsResult result, final CompoundTag parent, final String key, final boolean alwaysWriteIdentity) {
        final int[] mappings = result.mappings();
        final int numberOfChanges = mappings.length - result.identityMappings();
        final boolean hasChanges = numberOfChanges != 0 || result.emptyMappings() != 0;
        if (!hasChanges && !alwaysWriteIdentity) {
            LOGGER.debug("{}: Skipped due to no relevant id changes", key);
            return;
        }

        final CompoundTag tag = new CompoundTag();
        parent.put(key, tag);
        tag.put("mappedSize", new IntTag(result.mappedSize()));

        if (!hasChanges) {
            tag.put("id", new ByteTag(IDENTITY_ID));
            tag.put("size", new IntTag(mappings.length));
            return;
        }

        final int changedFormatSize = approximateChangedFormatSize(result);
        final int shiftFormatSize = approximateShiftFormatSize(result);
        final int plainFormatSize = mappings.length;
        if (changedFormatSize < plainFormatSize && changedFormatSize < shiftFormatSize) {
            writeChangedFormat(tag, result, key, numberOfChanges);
        } else if (shiftFormatSize < changedFormatSize && shiftFormatSize < plainFormatSize) {
            writeShiftFormat(tag, result, key);
        } else {
            LOGGER.debug("{}: Storing as direct values", key);
            tag.put("id", new ByteTag(DIRECT_ID));
            tag.put("val", new IntArrayTag(mappings));
        }
    }

    /**
     * Writes compact int to int mappings as changed values to the given tag.
     *
     * @param tag             tag to write to
     * @param result          result with int to int mappings
     * @param key             key to write to
     * @param numberOfChanges number of changed mappings
     */
    private static void writeChangedFormat(final CompoundTag tag, final MappingsResult result, final String key, final int numberOfChanges) {
        // Put two intarrays of only changed ids instead of adding an entry for every single identifier
        LOGGER.debug("{}: Storing as changed and mapped arrays", key);
        final int[] mappings = result.mappings();
        tag.put("id", new ByteTag(CHANGES_ID));
        tag.put("size", new IntTag(mappings.length));

        final int[] unmapped = new int[numberOfChanges];
        final int[] mapped = new int[numberOfChanges];
        int index = 0;
        for (int i = 0; i < mappings.length; i++) {
            final int mappedId = mappings[i];
            if (mappedId != i) {
                unmapped[index] = i;
                mapped[index] = mappedId;
                index++;
            }
        }

        if (index != numberOfChanges) {
            throw new IllegalStateException("Index " + index + " does not equal number of changes " + numberOfChanges);
        }

        tag.put("at", new IntArrayTag(unmapped));
        tag.put("val", new IntArrayTag(mapped));
    }

    /**
     * Writes compact int to int mappings as shifted values to the given tag.
     *
     * @param tag    tag to write to
     * @param result result with int to int mappings
     * @param key    key to write to
     */
    private static void writeShiftFormat(final CompoundTag tag, final MappingsResult result, final String key) {
        LOGGER.debug("{}: Storing as shifts", key);
        final int[] mappings = result.mappings();
        tag.put("id", new ByteTag(SHIFTS_ID));
        tag.put("size", new IntTag(mappings.length));

        final int[] shiftsAt = new int[result.shiftChanges()];
        final int[] shiftsTo = new int[result.shiftChanges()];

        int index = 0;
        // Check the first entry
        if (mappings[0] != 0) {
            shiftsAt[0] = 0;
            shiftsTo[0] = mappings[0];
            index++;
        }

        for (int id = 1; id < mappings.length; id++) {
            final int mappedId = mappings[id];
            if (mappedId != mappings[id - 1] + 1) {
                shiftsAt[index] = id;
                shiftsTo[index] = mappedId;
                index++;
            }
        }

        if (index != result.shiftChanges()) {
            throw new IllegalStateException("Index " + index + " does not equal number of changes " + result.shiftChanges() + " for " + key);
        }

        tag.put("at", new IntArrayTag(shiftsAt));
        tag.put("to", new IntArrayTag(shiftsTo));
    }

    public static void write(final CompoundTag tag, final Path path) throws IOException {
        NBTIO.writeFile(tag, path.toFile(), false, false);
    }

    private static int approximateChangedFormatSize(final MappingsResult result) {
        // Length of two arrays + more approximate length for extra tags
        return (result.mappings().length - result.identityMappings()) * 2 + 10;
    }

    private static int approximateShiftFormatSize(final MappingsResult result) {
        // One entry in two arrays each time the id is not shifted by 1 from the last id + more approximate length for extra tags
        return result.shiftChanges() * 2 + 10;
    }
}
