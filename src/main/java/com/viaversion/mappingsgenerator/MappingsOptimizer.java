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
package com.viaversion.mappingsgenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader.MappingsResult;
import com.viaversion.mappingsgenerator.util.JsonConverter;
import com.viaversion.mappingsgenerator.util.Version;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.io.TagWriter;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntArrayTag;
import com.viaversion.nbt.tag.Tag;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
    public static final String DIFF_FILE_FORMAT = "mapping-%sto%s.json";
    public static final String MAPPING_FILE_FORMAT = "mapping-%s.json";
    public static final String OUTPUT_FILE_FORMAT = "mappings-%sto%s.nbt";
    public static final String OUTPUT_IDENTIFIERS_FILE_FORMAT = "identifiers-%s.nbt";
    public static final String OUTPUT_GLOBAL_IDENTIFIERS_FILE = "identifier-table.nbt";

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingsOptimizer.class.getSimpleName());
    private static final TagWriter TAG_WRITER = NBTIO.writer().named();
    private static final Set<String> STANDARD_FIELDS = Set.of(
        "blockstates",
        "blocks",
        "items",
        "menus",
        "sounds",
        "blockentities",
        "enchantments",
        "paintings",
        "entities",
        "particles",
        "argumenttypes",
        "statistics",
        "tags",
        "attributes"
    );
    private static final int[] storageStrategyCounts = new int[IDENTITY_ID + 1];
    private static final Set<String> savedIdentifierFiles = new HashSet<>();
    static JsonObject globalIdentifiersObject;
    static JsonObject fileHashesObject;

    private final Set<String> ignoreMissing = new HashSet<>(Arrays.asList("blocks", "statistics"));
    private final CompoundTag output = new CompoundTag();
    private final String fromVersion;
    private final String toVersion;
    private final JsonObject unmappedObject;
    private final JsonObject mappedObject;
    private final boolean specialFrom;
    private final boolean specialTo;
    private final boolean backwards;
    private ErrorStrategy errorStrategy = ErrorStrategy.WARN;
    private JsonObject diffObject;
    private boolean keepUnknownFields;
    private boolean updatedGlobalIdentifiers;

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

    static void loadGlobalFiles() throws IOException {
        // Load and reuse identifiers file, being a global table across all versions
        if (globalIdentifiersObject == null) {
            globalIdentifiersObject = MappingsLoader.load(MAPPINGS_DIR, "identifier-table.json");
        }
        if (fileHashesObject == null) {
            try (final BufferedReader reader = Files.newBufferedReader(Path.of("output_hashes.json"))) {
                fileHashesObject = MappingsGenerator.GSON.fromJson(reader, JsonObject.class);
            }
        }
    }

    public MappingsOptimizer(final String from, final String to) throws IOException {
        this(from, to, false, false);
    }

    private Path getMappingsDir(final boolean special) {
        return special ? MAPPINGS_DIR.resolve("special") : MAPPINGS_DIR;
    }

    private Path getDiffDir(final boolean special) {
        final Path diffDir = MAPPINGS_DIR.resolve("diff");
        return special ? diffDir.resolve("special") : diffDir;
    }

    /**
     * Creates a new MappingsOptimizer instance.
     *
     * @param from        version to map from
     * @param to          version to map to
     * @param specialFrom If true, the special folders will be used for input
     * @param specialTo   If true, the special folders will be used for output
     * @see #optimizeAndWrite()
     */
    public MappingsOptimizer(final String from, final String to, final boolean specialFrom, final boolean specialTo) throws IOException {
        this.fromVersion = from;
        this.toVersion = to;
        this.specialFrom = specialFrom;
        this.specialTo = specialTo;
        this.backwards = specialFrom || Version.isBackwards(from, to);
        output.putInt("version", VERSION);

        unmappedObject = MappingsLoader.load(getMappingsDir(specialFrom), MAPPING_FILE_FORMAT.formatted(from));
        if (unmappedObject == null) {
            throw new IllegalArgumentException("Mapping file for version " + from + " does not exist");
        }

        mappedObject = MappingsLoader.load(getMappingsDir(specialTo), MAPPING_FILE_FORMAT.formatted(to));
        if (mappedObject == null) {
            throw new IllegalArgumentException("Mapping file for version " + to + " does not exist");
        }

        diffObject = MappingsLoader.load(getDiffDir(specialFrom || specialTo), DIFF_FILE_FORMAT.formatted(from, to));

        loadGlobalFiles();
    }

    /**
     * Optimizes mapping files as nbt files with only the necessary data (int to int mappings in form of int arrays).
     */
    public void optimizeAndWrite() throws IOException {
        LOGGER.info("=== Compacting json mapping files for versions {} → {}...", fromVersion, toVersion);

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
        mappings(false, "menus");
        mappings(false, "attributes");
        mappings(false, "recipe_serializers");
        mappings(false, "slot_displays");
        mappings(false, "data_component_type");

        if (diffObject != null) {
            names("items", "itemnames");
            names("enchantments", "enchantmentnames");
            fullNames("entitynames", "entitynames");

            if (diffObject.has("tags")) {
                tags();
            }
            if (diffObject.has("blockstates")) {
                changedBlockStateProperties();
            }
        }

        Path outputDir = backwards ? OUTPUT_BACKWARDS_DIR : OUTPUT_DIR;
        if (specialFrom || specialTo) {
            outputDir = outputDir.resolve("special");
        }

        final Path outputPath = outputDir.resolve(OUTPUT_FILE_FORMAT.formatted(fromVersion, toVersion));
        write(output, outputPath);

        // Save full identifiers to a separate file per version
        saveIdentifierFiles(fromVersion, unmappedObject);
        saveIdentifierFiles(toVersion, mappedObject);

        // Store object/file data to keep track of changes
        addFileData(fromVersion + ":" + toVersion, output.hashCode(), outputPath);
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
            Files.writeString(getDiffDir(specialFrom || specialTo).resolve(DIFF_FILE_FORMAT.formatted(fromVersion, toVersion)), MappingsGenerator.GSON.toJson(diffObject));
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
    public void writeToDir(final Path directory) throws IOException {
        write(output, directory.resolve(OUTPUT_FILE_FORMAT.formatted(fromVersion, toVersion)));
    }

    public void saveIdentifierFiles(final String version, final JsonObject object) throws IOException {
        final CompoundTag identifiers = new CompoundTag();
        storeIdentifierIndexes(identifiers, object, "entities");
        storeIdentifierIndexes(identifiers, object, "items");
        storeIdentifierIndexes(identifiers, object, "sounds");
        storeIdentifierIndexes(identifiers, object, "blocks");
        storeIdentifierIndexes(identifiers, object, "particles");
        storeIdentifierIndexes(identifiers, object, "argumenttypes");
        storeIdentifierIndexes(identifiers, object, "attributes");
        storeIdentifierIndexes(identifiers, object, "recipe_serializers");
        storeIdentifierIndexes(identifiers, object, "slot_displays");
        storeIdentifierIndexes(identifiers, object, "data_component_type");
        storeIdentifierIndexes(identifiers, object, "blockentities");

        // No need to save the same identifiers multiple times if one version appears in multiple runs
        if (savedIdentifierFiles.add(version) && !identifiers.isEmpty()) {
            final Path outputDir = (specialFrom || specialTo) ? OUTPUT_DIR.resolve("special") : OUTPUT_DIR;
            final Path outputPath = outputDir.resolve(OUTPUT_IDENTIFIERS_FILE_FORMAT.formatted(version));

            write(identifiers, outputPath);
            addFileData(version, identifiers.hashCode(), outputPath);
        }

        // Update global identifiers file if necessary
        if (updatedGlobalIdentifiers) {
            // Also keep a json file around for easier viewing
            writeJson(globalIdentifiersObject, MAPPINGS_DIR.resolve("identifier-table.json"));
            LOGGER.info("Updated global identifiers file");
        }

        // Always create output file
        final Path outputPath = OUTPUT_DIR.resolve(OUTPUT_GLOBAL_IDENTIFIERS_FILE);
        final CompoundTag globalIdentifiersTag = (CompoundTag) JsonConverter.toTag(globalIdentifiersObject);
        write(globalIdentifiersTag, outputPath);
        addFileData("identifier-table", globalIdentifiersTag.hashCode(), outputPath);
        updatedGlobalIdentifiers = false;
    }

    private static void addFileData(final String key, final int hash, final Path path) throws IOException {
        JsonObject fileData = fileHashesObject.getAsJsonObject(key);
        if (fileData == null) {
            fileData = new JsonObject();
            fileHashesObject.add(key, fileData);
        }

        // The object hash is good enough
        fileData.addProperty("object-hash", hash);
        fileData.addProperty("size", Files.size(path));

        writeJson(fileHashesObject, Path.of("output_hashes.json"));
    }

    static void writeJson(final JsonObject object, final Path path) throws IOException {
        try (final BufferedWriter writer = Files.newBufferedWriter(path)) {
            MappingsGenerator.GSON.toJson(object, writer);
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

            errorStrategy.apply("NON-STANDARD FIELD: " + key + " - writing it to the file without changes");

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

    private ErrorStrategy shouldWarn(final String key) {
        return ignoreMissing.contains(key) ? ErrorStrategy.IGNORE : errorStrategy;
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
            errorStrategy
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

        changedTag.putByte("id", MappingsOptimizer.CHANGES_ID);
        changedTag.putByte("nofill", (byte) 1);
        changedTag.putInt("size", size);
        changedTag.putInt("mappedSize", mappedIdentifiers.size());
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
            tag.putString(idAsString, entry.getValue().getAsString());
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
            tag.putString(entry.getKey(), entry.getValue().getAsString());
        }
    }

    /**
     * Collects changed block states, used for the debug stick.
     * This checks for any change whether it's the base type or a property, but does not list changed properties,
     * as that would increase file size by a lot for no real value.
     */
    private void changedBlockStateProperties() throws IOException {
        if (fromVersion.equals("1.13.2") && toVersion.equals("1.13")
            || fromVersion.equals("1.13") && toVersion.equals("1.13.2")) {
            return;
        }

        final IntSet changedProperties = new IntOpenHashSet();
        for (final Map.Entry<String, JsonElement> entry : diffObject.getAsJsonObject("blockstates").entrySet()) {
            final String block = entry.getKey().split("\\[", 2)[0];
            changedProperties.add(idOf("blocks", block, false));
        }

        if (!changedProperties.isEmpty()) {
            output.put("changed_blocks", new IntArrayTag(changedProperties.toIntArray()));
        }
    }

    private int idOf(final String key, final String value, final boolean mapped) {
        final JsonArray array = (mapped ? mappedObject : unmappedObject).getAsJsonArray(key);
        for (int i = 0; i < array.size(); i++) {
            final JsonElement element = array.get(i);
            if (element.getAsString().equals(value)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Could not find id for " + key + ": " + value);
    }

    /**
     * Writes mapped tag ids to the given tag.
     */
    private void tags() {
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
                case "entity_type" -> "entities";
                case "enchantment" -> "enchantments";
                default -> throw new IllegalArgumentException("Registry type not supported: " + type);
            };
            if (!mappedObject.has(typeKey)) {
                throw new IllegalArgumentException("Could not find mapped object for " + typeKey);
            }

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
     * Stores a list of global identifier indexes in the given tag.
     *
     * @param tag    tag to write to
     * @param object object to read identifiers from
     * @param key    to read from and write to
     */
    private void storeIdentifierIndexes(
        final CompoundTag tag,
        final JsonObject object,
        final String key
    ) {
        final JsonElement identifiersElement = object.get(key);
        if (identifiersElement == null) {
            return;
        }

        if (identifiersElement.isJsonObject()) {
            // Pre 1.13
            LOGGER.debug("Identifiers for {} are not an array", key);
            return;
        }

        // Add to global identifiers if not already present
        final JsonArray identifiers = identifiersElement.getAsJsonArray();
        JsonArray globalIdentifiersArray = globalIdentifiersObject.getAsJsonArray(key);
        if (globalIdentifiersArray == null) {
            globalIdentifiersArray = new JsonArray();
            globalIdentifiersObject.add(key, globalIdentifiersArray);
        }

        final Object2IntMap<String> globalIdentifiers = new Object2IntOpenHashMap<>(globalIdentifiersArray.size());
        globalIdentifiers.defaultReturnValue(-1);
        for (int globalId = 0; globalId < globalIdentifiersArray.size(); globalId++) {
            final String identifier = globalIdentifiersArray.get(globalId).getAsString();
            globalIdentifiers.put(identifier, globalId);
        }

        for (int id = 0; id < identifiers.size(); id++) {
            final JsonElement entry = identifiers.get(id);
            if (entry.isJsonNull()) {
                continue;
            }

            final String identifier = entry.getAsString();
            if (globalIdentifiers.containsKey(identifier)) {
                continue;
            }

            final int addedGlobalIndex = globalIdentifiersArray.size();
            globalIdentifiersArray.add(identifier);
            globalIdentifiers.put(identifier, addedGlobalIndex);
            updatedGlobalIdentifiers = true;
        }

        // Use the same compact storage on the identifier->global identifier files, just about halves the size
        // Remove mapped size to avoid unnecessary file changes
        MappingsResult result = MappingsLoader.map(identifiers, globalIdentifiersArray, null, errorStrategy);
        result = new MappingsResult(result.mappings(), -1, result.emptyMappings(), result.identityMappings(), result.shiftChanges());
        serialize(result, tag, key, true);
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
        if (result.mappedSize() != -1) {
            tag.putInt("mappedSize", result.mappedSize());
        }

        if (!hasChanges) {
            tag.putByte("id", IDENTITY_ID);
            tag.putInt("size", mappings.length);
            storageStrategyCounts[IDENTITY_ID]++;
            return;
        }

        final int changedFormatSize = approximateChangedFormatSize(result);
        final int shiftFormatSize = approximateShiftFormatSize(result);
        final int plainFormatSize = mappings.length;
        if (changedFormatSize < plainFormatSize && changedFormatSize < shiftFormatSize) {
            writeChangedFormat(tag, result, key, numberOfChanges);
            storageStrategyCounts[CHANGES_ID]++;
        } else if (shiftFormatSize < changedFormatSize && shiftFormatSize < plainFormatSize) {
            writeShiftFormat(tag, result, key);
            storageStrategyCounts[SHIFTS_ID]++;
        } else {
            tag.putByte("id", DIRECT_ID);
            tag.put("val", new IntArrayTag(mappings));
            storageStrategyCounts[DIRECT_ID]++;
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
        tag.putByte("id", CHANGES_ID);
        tag.putInt("size", mappings.length);

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
        tag.putByte("id", SHIFTS_ID);
        tag.putInt("size", mappings.length);

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

    public static void printStats() {
        LOGGER.info("Storage format counts: direct={}, shifts={}, changes={}, identity={}", storageStrategyCounts[DIRECT_ID], storageStrategyCounts[SHIFTS_ID], storageStrategyCounts[CHANGES_ID], storageStrategyCounts[IDENTITY_ID]);
    }

    public static void write(final CompoundTag tag, final Path path) throws IOException {
        TAG_WRITER.write(path, tag, false);
    }

    private static int approximateChangedFormatSize(final MappingsResult result) {
        // Length of two arrays + more approximate length for extra tags
        return (result.mappings().length - result.identityMappings()) * 2 + 10;
    }

    private static int approximateShiftFormatSize(final MappingsResult result) {
        // One entry in two arrays each time the id is not shifted by 1 from the last id + more approximate length for extra tags
        return result.shiftChanges() * 2 + 10;
    }

    public void setErrorStrategy(final ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
    }
}
