package com.viaversion.mappingsgenerator.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Utility to more easily map blockstates.
 * <p>
 * Copy the stubbed lines from diff mappings into a file called states.txt and update the CONSUMER contents.
 * Different methods will do different things, the result will be printed to the console.
 */
final class BlockStateMapper {

    private static final Function<BlockState, BlockState> CONSUMER = state -> {
        state.addProperty(5, "waterlogged", "false");
        state.setState("glow_lichen");
        return state;
    };

    public static void main(final String[] args) throws IOException {
        applyFunction();
    }

    public static void applyFunction() throws IOException {
        final String content = Files.readString(Path.of("states.txt"));
        for (final String line : content.split("\n")) {
            String trimmedLine = line.replace("\"", "").trim();
            if (trimmedLine.endsWith(": ,")) {
                trimmedLine = trimmedLine.substring(0, trimmedLine.length() - 3);
            }

            final BlockState state = new BlockState(trimmedLine);
            System.out.println("\"" + trimmedLine + "\": \"" + CONSUMER.apply(state) + "\",");
        }
    }

    public static void replace(final String... replacements) throws IOException {
        final String content = Files.readString(Path.of("states.txt"));
        for (final String line : content.split("\n")) {
            boolean found = false;
            for (int i = 0; i < replacements.length; i += 2) {
                final String from = replacements[i];
                if (!line.contains(from)) {
                    continue;
                }

                final String to = replacements[i + 1];
                final String[] split = line.split("\": \"", 2);
                System.out.println(split[0] + "\": \"" + split[1].replace(from, to));
                found = true;
                break;
            }

            if (!found) {
                System.out.println(line);
            }
        }
    }

    public static void editKey() throws IOException {
        final String newName = "dark_oak_slab";
        final String content = Files.readString(Path.of("states.txt"));
        for (String line : content.split("\"\",")) {
            if (line.trim().equals("}")) {
                continue;
            }

            final String firstPart = line;
            line = line.replace("\"", "").replace(": ", "").trim();
            final String[] split = line.split("\\[", 2);
            System.out.println(firstPart + "\"minecraft:" + newName + "[" + split[1] + "\",");
        }
    }

    public static final class BlockState {

        private final List<Property> properties = new ArrayList<>();
        private String state;

        public BlockState(final String state) {
            final int start = state.indexOf('[');
            if (start == -1) {
                this.state = state;
                return;
            }

            if (!state.endsWith("]")) {
                throw new IllegalArgumentException("Invalid block state: " + state);
            }

            this.state = state.substring(0, start);

            int lastCommaIndex = start;
            int commaIndex;
            while ((commaIndex = state.indexOf(',', lastCommaIndex + 1)) != -1) {
                final String part = state.substring(lastCommaIndex + 1, commaIndex);
                final String[] split = part.split("=", 2);
                properties.add(new Property(split[0], split[1]));

                lastCommaIndex = commaIndex;
            }

            final String part = state.substring(lastCommaIndex + 1, state.length() - 1);
            final String[] split = part.split("=", 2);
            properties.add(new Property(split[0], split[1]));
        }

        public @Nullable Property getProperty(final String key) {
            for (final Property property : properties) {
                if (property.key.equals(key)) {
                    return property;
                }
            }
            return null;
        }

        public void addProperty(final String key, final String value) {
            properties.add(new Property(key, value));
        }

        public void addProperty(final int index, final String key, final String value) {
            properties.add(index, new Property(key, value));
        }

        public @Nullable Property removeProperty(final String key) {
            for (int i = 0; i < properties.size(); i++) {
                final Property property = properties.get(i);
                if (property.key.equals(key)) {
                    properties.remove(i);
                    return property;
                }
            }
            return null;
        }

        public List<Property> getProperties() {
            return properties;
        }

        public String getState() {
            return state;
        }

        public void setState(final String state) {
            this.state = state;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder(state).append('[');
            for (final Property property : properties) {
                builder.append(property.getKey()).append('=').append(property.getValue()).append(',');
            }
            return builder.substring(0, builder.length() - 1) + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final BlockState that = (BlockState) o;
            if (!properties.equals(that.properties)) return false;
            return state.equals(that.state);
        }

        @Override
        public int hashCode() {
            int result = properties.hashCode();
            result = 31 * result + state.hashCode();
            return result;
        }

        public BlockState copy() {
            return new BlockState(toString());
        }
    }

    public static final class Property {

        private String key;
        private String value;

        public Property(final String key, final String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public void setKey(final String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Property property = (Property) o;
            if (!key.equals(property.key)) return false;
            return value.equals(property.value);
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }
    }
}
