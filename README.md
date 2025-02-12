# Mappings

These are mappings made for 1.21.4 to 1.8, your players will get the best performance using these mappings.

Generates and compiles mapping files for Via*. Current mapping files can be found in the `mappings/` directory.

## Generating json mapping files for a Minecraft version

Compile the project using `./gradlew build` and put the jar in some directory, ideally the project root.

Then run the jar with:

```
java -jar MappingsGenerator.jar <path to server jar> <version>
```

The mapping file will then be generated in the `mappings/` directory.

## Compiling json mapping files into compact nbt files

If you want to generate the compact mapping files with already present json files, you can also trigger the optimizer on
its own by starting the `MappingsOptimizer` class with the two arguments flipped:

```
java -cp MappingsGenerator.jar com.viaversion.mappingsgenerator.MappingsOptimizer <from version> <to version>
```

### Optional arguments

Optional arguments must follow the two version arguments.

* `--generateDiffStubs` to generate diff files with empty stubs for missing mappings
* `--keepUnknownFields` to keep non-standard fields from json mappings in the compact files

## Updating version files
On Minecraft updates, the `next_release.txt` and `last_release.txt` files need to be updated manually.
`last_release.txt` needs the last release **ViaVersion requires mappings for**.

## Json format

The json files contain a number of Minecraft registries in form of json arrays, where the index corresponds to the id of
the entry.

Diff files for either ViaVersion or ViaBackwards then contain additional entries for changed identifiers, either in form
of string→string or int→string mappings. These files need to be manually filled. If any such entries are required, the
optimizer will give a warning with the missing keys.

Json mapping files are found in the `mapping/` directory and are named `mapping-<version>.json`. Files containing
diff-mappings for added or removed identifiers between versions must be named `mapping-<from>to<to>.json` and put into
the `mapping/diff/` directory.

## Compact format

Compact files are always saved as [NBT](https://minecraft.fandom.com/wiki/NBT_format). ViaVersion uses its
own [ViaNBT](https://github.com/ViaVersion/ViaNBT) as the NBT reader/writer. Compact files are found in the
`output/` directory and subdirectories.

### Identifier files

Next to a standardized compact format for int id mappings, the full identifiers of some registries are also required.
For this, we generate a list of *all* identifiers in the registry across all versions, so that their names only need to
be stored once, as opposed to storing them again in every new version they are still in. Wherever needed, these
identifiers are then referred to via their index in the global list.

### Mapping files

Each mapping file contains a `v` int tag with the format version, currently being `1`.

In each mapping file, a number of extra objects may be contained, such as string→string mappings for sounds. Most other
parts (including blockstates, blocks, items, blockentities, enchantments, paintings, entities, particles, argumenttypes,
and statistics) are stored as compound tags, containing:

* `id` (byte tag) determining the storage type as defined below
* `size` (int tag) the number of unmapped entries in the registry
* `mappedSize` (int tag) the number of mapped entries in the registry

The rest of the content depends on the storage type, each resulting in vastly different storage sizes depending on the
number and distribution of id changes, used to make the mapping files about as small as possible without sacrifing
deserialization performance or making the formats *too* complex.

### Direct value storage

The direct storage simply stores an array of ints exactly as they can be used in the protocol.

* `id` (byte tag) is `0`
* `val` (int array tag) contains the mapped ids, where their array index corresponds to the unmapped id

### Shifted value storage

The shifted value storage stores two int arrays: One containing the unmapped ids that end a sequence of mapped ids. For
an index `i`, all unmapped ids between `at[i] + sequence` (inclusive) and `at[i + 1]` (exclusive) are mapped
to `to[i] + sequence`.

* `id` (byte tag) is `1`
* `at` (int array tag) contains the unmapped ids, where their mapped is is *not* simply the last mapped id + 1
* `to` (int array tag) contains the mapped ids, indexed by the same index as the unmapped id in `at`

### Changed value storage

The changed value storage stores two int arrays: One containing the changed unmapped ids, and one their corresponding
mapped ids in a simple int→int mapping over the two arrays.

* `id` (byte tag) is `2`
* `at` (int array tag) contains the unmapped ids that have been changed
* `val` (int array tag) contains the mapped ids, indexed by the same index as the unmapped id in `at`
* Optional: `nofill` (byte tag): Unless present, all ids between the ones found in `at` are mapped to their identity

### Identity storage

The identity storage signifies that every id between `0` and `size` is mapped to itself. This is sometimes used over
simply leaving out the entry to make sure ids stay in bounds.

* `id` (byte tag) is `3`

## License

The Java and Python code is licensed under the GNU GPL v3 license. The files under `mappings/` are free to copy, use,
and expand upon in whatever way you like.
