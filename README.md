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

Version-tracking files in the project root:

* `last_release.txt` the last release ViaVersion needs mappings for
* `next_release.txt` set this to the upcoming release being targeted (e.g., 27.3)
* `last_snapshot.txt` the latest development build ID updated automatically by `download_server.py` (snapshots, prereleases, RCs, e.g., 27.3-snapshot-5)
* `last_custom_model_data.txt` a counter tracking the last assigned CustomModelData number for backwards item remaps (incremented automatically by `--generateDiffStubs`)

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

### Direct value storage

The direct storage simply stores the mapped ids in order, packed into `val` as the ZigZag difference to the previous mapped id.

* `id` (byte tag) is `0`

### Shifted value storage

The shifted value storage stores a sequence of boundary pairs (`at`, `to`) packed into `val`. For an index `i`, all unmapped ids between `at[i] + sequence` (inclusive) and `at[i + 1]` (exclusive) are mapped to `to[i] + sequence`.

* `id` (byte tag) is `1`

### Changed value storage

The changed value storage stores pairs of changed unmapped ids and their corresponding mapped ids (`at`, `val`) packed into `val`. All ids between the ones found in `at` are mapped to their identity.

* `id` (byte tag) is `2`

### Identity storage

The identity storage signifies that every id between `0` and `size` is mapped to itself. This is sometimes used over simply leaving out the entry to make sure ids stay in bounds.

* `id` (byte tag) is `3`

## License

The Java and Python code is licensed under the GNU GPL v3 license. The files under `mappings/` are free to copy, use, and expand upon in whatever way you like.
