# Mappings

Generates and compiles mapping files for Via*. Current mapping files can be found in the `mappings/` directory.

## Generating json mapping files for a Minecraft version

Compile the project using `mvn clean build` and put the jar in some directory, ideally the project root.

Then run the jar with: `java -jar MappingsGenerator.jar <path to server jar> <version>`,
e.g. `java -jar MappingsGenerator.jar server.jar 20w22a`. The mapping file will then be generated in the `mappings/`
directory.

## Compiling json mapping files into compact nbt files

## License

The Java code is licensed under the GNU GPL v3 license. The files under `mappings/` are free to copy, use, and expand
upon in whatever way you like.