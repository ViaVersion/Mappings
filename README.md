# Mappings

Generates and compiles mapping files for Via*. Current mapping files can be found in the `mappings/` directory.

## Non-technical editing of existing mappings

If you're just here to edit an existing mapping of a block, item, or an entity name, you can use the helper UI.
Run the `MappingUi` main function via IntelliJ or via the command line with `./gradlew runUi`. After generating the output NBT files,
you can find them in the `output/` directory.

## Generating json mapping files for a Minecraft version

Compile the project using `./gradlew build` and copy the jar from `build/libs/` to the project root as `MappingsGenerator.jar`.

Then run the jar with:

```bash
java -jar MappingsGenerator.jar <path to server jar> <mc-version>
```

The mapping file will then be generated in the `mappings/` directory.

## Compiling json mapping files into compact nbt files

If you want to generate the compact mapping files with already-present JSON files, you can also trigger the optimizer on its own by running the `MappingsOptimizer` class with the *from* and *to* Minecraft versions:

```bash
java -cp MappingsGenerator.jar com.viaversion.mappingsgenerator.MappingsOptimizer <from mc version> <to mc version> [options]
```

### Optional arguments

Optional arguments must follow the two version arguments.

* `--generateDiffStubs` to generate diff files with empty stubs for missing mappings
  * When generating backwards mappings, also assigns and increments `custom_model_data` IDs
* `--keepUnknownFields` to keep non-standard fields from JSON mappings in the compact files

## Using custom mappings in ViaBackwards

To test or override backwards mappings on a server without recompiling ViaBackwards, copy the generated NBT file from `output/backwards/` into the server's `plugins/ViaBackwards/` directory.

## Updating version files

When moving to new Minecraft updates, update these files manually with release version numbers, not snapshot strings:

* `next_release.txt`: the upcoming release being targeted.
* `last_release.txt`: the last release with existing files in `mappings/` (e.g. `26.1` rather than `26.1.2` if the latest is hotfixes with no registry changes).

## JSON format

JSON files in `mappings/mapping-<mc-version>.json` contain Minecraft registry arrays where array indices correspond to numeric protocol IDs.

Diff mappings between versions are in `mappings/diff/mapping-<from>to<to>.json`. These files need to be manually filled out. If required mappings are missing, the optimizer will give a warning noting the missing keys.

* Blockstates
  * Exact state mapping: `"old_block[prop=a]": "new_block[prop=b]"`
  * Bare block fallback: `"old_block": "new_block[prop=b]"`
  * Copy all source properties: `"old_block": "new_block["`
  * Intentionally unmapped: `""`
* Registries (`items`, `blocks`, `entities`, `sounds`, `menus`, `attributes`, etc.)
  * Identifier remaps: `"old_id": "new_id"` (or `""` if unmapped)
* Custom model data
  * Generated *item* remaps to *custom model data* IDs: `"item_id": 123`
* Tags
  * Sorted ranges: `"<registry>": { "<tag>": ["id", ...] }`
  * Preserved order: `"<registry>": { "<tag>": { "ordered": true, "values": ["id", ...] } }`

## Compact format

Compact files are always saved as [NBT](https://minecraft.fandom.com/wiki/NBT_format) using ViaVersion's own [ViaNBT](https://github.com/ViaVersion/ViaNBT) library and output to the `output/` or `output/backwards/` directories.

### Identifier files

Full identifiers for registries across all versions are deduplicated into `output/identifier-table.nbt`. Per-version files (`identifiers-<mc-version>.nbt`) store the mapping of each local registry ID to the global list.

### Mapping files

Each mapping file contains a `version` int tag with the format version, currently being `2`.

> *Note: Format version 1 (which used uncompressed int array tags and a `v` tag) was superseded in ViaVersion 5.x by version 2 to reduce file size. See the README in older git commits for the v1 spec.*

Mappings for blockstates, blocks, items, menus, sounds, blockentities, enchantments, paintings, entities, particles, argumenttypes, statistics, attributes, recipe_serializers, slot_displays, and data_component_type are stored as compound tags:

* `id` (byte tag) Storage strategy ID (0–3)
* `size` (int tag) Total entries in unmapped registry
* `mappedSize` (int tag, optional) Total entries in mapped registry
* `val` (byte array tag) Encoded mapping payload (strategies 0–2)

The rest of the content depends on the storage strategy, each resulting in vastly different storage sizes depending on the number and distribution of id changes, used to make the mapping files about as small as possible without sacrificing deserialization performance or making the formats *too* complex. Values are packed into a byte array tag called `val` using VarInts and ZigZag encoding.

#### Direct value storage

The direct storage (`id` is `0`) simply stores the mapped ids in order, packed into `val` as the ZigZag difference to the previous mapped id.

#### Shifted value storage

The shifted value storage (`id` is `1`) stores a sequence of boundary pairs (`at`, `to`) packed into `val`. For an index `i`, all unmapped ids between `at[i] + sequence` (inclusive) and `at[i + 1]` (exclusive) are mapped to `to[i] + sequence`.

#### Changed value storage

The changed value storage (`id` is `2`) stores the changed unmapped ids (`at`) and their corresponding mapped ids (`val`) in a simple int→int mapping, packed into `val` as alternating varint pairs.

* Optional: `nofill` (byte tag): Unless present, all `id`s between the ones found in `at` are mapped to their identity

#### Identity storage

The identity storage (`id` is `3`) signifies that every id between `0` and `size` is mapped to itself. This is sometimes used over simply leaving out the entry to make sure ids stay in bounds.

## License

The Java and Python code is licensed under the GNU GPL v3 license. The files under `mappings/` are free to copy, use, and expand upon in whatever way you like.
