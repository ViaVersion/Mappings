package com.viaversion.mappingsgenerator.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.util.GsonUtil;
import com.viaversion.mappingsgenerator.util.Version;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

import static com.viaversion.mappingsgenerator.MappingsOptimizer.MAPPINGS_DIR;
import static com.viaversion.mappingsgenerator.MappingsOptimizer.MAPPING_FILE_FORMAT;

/**
 * Local helper UI for filling blockstate diff mappings.
 * <p>
 * This directly edits the diff json.
 */
public final class BlockStateMappingUi {

    private static final String INDEX_RESOURCE = "/com/viaversion/mappingsgenerator/helper/blockstate-mapping-ui.html";
    private static final String[] SIMPLE_SECTIONS = {
        "blocks",
        "items",
        "sounds",
        "blockentities",
        "enchantments",
        "paintings",
        "entities",
        "particles",
        "argumenttypes",
        "statistics",
        "menus",
        "attributes",
        "recipe_serializers",
        "slot_displays",
        "data_component_type"
    };

    private final String defaultFrom;
    private final String defaultTo;

    private BlockStateMappingUi(final String defaultFrom, final String defaultTo) {
        this.defaultFrom = defaultFrom;
        this.defaultTo = defaultTo;
    }

    public static void main(final String[] args) throws IOException {
        final VersionPair defaultPair = defaultPair();
        final String defaultFrom = args.length > 0 ? args[0] : defaultPair.from();
        final String defaultTo = args.length > 1 ? args[1] : defaultPair.to();
        final int port = args.length > 2 ? Integer.parseInt(args[2]) : 8765;
        new BlockStateMappingUi(defaultFrom, defaultTo).start(port);
    }

    private void start(final int port) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/versions", this::handleVersions);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/apply", this::handleApply);
        server.start();
        System.out.println("UI running at: http://127.0.0.1:" + port + "/");
    }

    private void handleIndex(final HttpExchange exchange) throws IOException {
        if (!method(exchange, RequestMethod.GET)) {
            return;
        }
        send(exchange, 200, "text/html", index());
    }

    private void handleVersions(final HttpExchange exchange) throws IOException {
        if (!method(exchange, RequestMethod.GET)) {
            return;
        }

        final JsonObject response = new JsonObject();
        response.addProperty("defaultFrom", defaultFrom);
        response.addProperty("defaultTo", defaultTo);
        response.add("versions", versions());
        send(exchange, 200, "application/json", GsonUtil.GSON.toJson(response));
    }

    private void handleState(final HttpExchange exchange) throws IOException {
        if (!method(exchange, RequestMethod.GET)) {
            return;
        }

        final Map<String, String> query = query(exchange);
        final VersionPair pair = pair(query.get("from"), query.get("to"));
        final Path diffPath = diffPath(pair);
        final JsonObject response = new JsonObject();
        response.addProperty("from", pair.from());
        response.addProperty("to", pair.to());
        response.addProperty("diffPath", diffPath.toString());
        response.add("sourceStates", blockStates(pair.from()));
        response.add("targetStates", blockStates(pair.to()));
        response.add("diffEntries", diffEntries(diffPath));
        response.add("simpleMappings", simpleMappings(pair, diffPath));
        response.add("nameMappings", nameMappings(pair, diffPath));
        send(exchange, 200, "application/json", GsonUtil.GSON.toJson(response));
    }

    private void handleApply(final HttpExchange exchange) throws IOException {
        if (!method(exchange, RequestMethod.POST)) {
            return;
        }

        final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        final JsonObject request = GsonUtil.GSON.fromJson(body, JsonObject.class);
        final VersionPair pair = pair(string(request, "from"), string(request, "to"));
        final Path diffPath = diffPath(pair);
        final String section = request.has("section") ? request.get("section").getAsString() : "blockstates";
        final JsonObject entries = request.getAsJsonObject("entries");
        final boolean overwrite = !request.has("overwrite") || request.get("overwrite").getAsBoolean();
        if (entries == null || entries.isEmpty()) {
            send(exchange, 400, "application/json", "{\"error\":\"No entries provided\"}");
            return;
        }

        final ApplyResult result;
        try {
            result = "blockstates".equals(section)
                ? applyBlockstates(diffPath, entries, overwrite)
                : applySection(diffPath, section, entries, overwrite);
        } catch (final ApplyConflictException e) {
            send(exchange, 409, "application/json", e.getMessage());
            return;
        }

        final JsonObject response = new JsonObject();
        response.addProperty("written", result.written());
        response.addProperty("skipped", result.skipped());
        response.addProperty("removed", result.removed());
        response.addProperty("path", diffPath.toString());
        send(exchange, 200, "application/json", GsonUtil.GSON.toJson(response));
    }

    private ApplyResult applyBlockstates(final Path diffPath, final JsonObject entries, final boolean overwrite) throws IOException {
        final JsonObject diff = loadOrCreateDiff(diffPath);
        JsonObject blockstates = diff.getAsJsonObject("blockstates");
        if (blockstates == null) {
            blockstates = new JsonObject();
            diff.add("blockstates", blockstates);
        }

        final JsonArray conflicts = new JsonArray();
        for (final Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue().getAsString();
            collectBareFallbackConflict(blockstates, key, overwrite, conflicts);
            if (blockstates.has(key) && blockstates.get(key).getAsString().equals(value) && !isCompact(key, value)) {
                continue;
            }
            if (!overwrite && blockstates.has(key) && !blockstates.get(key).getAsString().isEmpty()) {
                conflicts.add(key);
            }
            collectCompactConflicts(blockstates, key, value, overwrite, conflicts);
        }
        if (!conflicts.isEmpty()) {
            final JsonObject response = new JsonObject();
            response.addProperty("error", "Conflicting non-empty mappings");
            response.add("conflicts", conflicts);
            throw new ApplyConflictException(GsonUtil.GSON.toJson(response));
        }

        int written = 0;
        int skipped = 0;
        int removed = 0;
        for (final Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue().getAsString();
            removed += removeBareFallbackForSpecific(blockstates, key, overwrite);
            if (isCoveredByExistingCompact(blockstates, key, value)) {
                skipped++;
                continue;
            }
            if (blockstates.has(key) && blockstates.get(key).getAsString().equals(value)) {
                removed += removeEntriesCoveredByCompact(blockstates, key, value, overwrite);
                skipped++;
                continue;
            }

            blockstates.addProperty(key, value);
            removed += removeEntriesCoveredByCompact(blockstates, key, value, overwrite);
            written++;
        }
        if (written != 0 || removed != 0) {
            Files.createDirectories(diffPath.getParent());
            Files.writeString(diffPath, GsonUtil.GSON.toJson(diff), StandardCharsets.UTF_8);
        }
        return new ApplyResult(written, skipped, removed);
    }

    private ApplyResult applySection(final Path diffPath, final String section, final JsonObject entries, final boolean overwrite) throws IOException {
        final JsonObject diff = loadOrCreateDiff(diffPath);
        JsonObject sectionObject = diff.getAsJsonObject(section);
        if (sectionObject == null) {
            sectionObject = new JsonObject();
            diff.add(section, sectionObject);
        }

        final JsonArray conflicts = new JsonArray();
        for (final Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue().getAsString();
            if (!overwrite && sectionObject.has(key) && !sectionObject.get(key).getAsString().equals(value)) {
                conflicts.add(key);
            }
        }
        if (!conflicts.isEmpty()) {
            final JsonObject response = new JsonObject();
            response.addProperty("error", "Conflicting mappings");
            response.add("conflicts", conflicts);
            throw new ApplyConflictException(GsonUtil.GSON.toJson(response));
        }

        int written = 0;
        int skipped = 0;
        for (final Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue().getAsString();
            if (sectionObject.has(key) && sectionObject.get(key).getAsString().equals(value)) {
                skipped++;
                continue;
            }

            sectionObject.addProperty(key, value);
            written++;
        }
        if (written != 0) {
            Files.createDirectories(diffPath.getParent());
            Files.writeString(diffPath, GsonUtil.GSON.toJson(diff), StandardCharsets.UTF_8);
        }
        return new ApplyResult(written, skipped, 0);
    }

    private JsonArray blockStates(final String version) throws IOException {
        final JsonObject mapping = MappingsLoader.load("mapping-" + version + ".json");
        if (mapping == null || !mapping.has("blockstates")) {
            throw new IllegalStateException("No blockstates found for " + version);
        }

        final JsonArray states = new JsonArray();
        for (final JsonElement element : mapping.getAsJsonArray("blockstates")) {
            states.add(parseState(element.getAsString()));
        }
        return states;
    }

    private JsonArray diffEntries(final Path diffPath) throws IOException {
        final JsonArray entries = new JsonArray();
        final JsonObject diff = loadOrCreateDiff(diffPath);
        final JsonObject blockstates = diff.getAsJsonObject("blockstates");
        if (blockstates == null) {
            return entries;
        }

        for (final Map.Entry<String, JsonElement> entry : blockstates.entrySet()) {
            final JsonObject object = parseState(entry.getKey());
            object.addProperty("value", entry.getValue().getAsString());
            entries.add(object);
        }
        return entries;
    }

    private JsonObject simpleMappings(final VersionPair pair, final Path diffPath) throws IOException {
        final JsonObject sourceMapping = MappingsLoader.load(MAPPING_FILE_FORMAT.formatted(pair.from()));
        final JsonObject targetMapping = MappingsLoader.load(MAPPING_FILE_FORMAT.formatted(pair.to()));
        final JsonObject diff = loadOrCreateDiff(diffPath);
        final JsonObject mappings = new JsonObject();
        for (final String section : SIMPLE_SECTIONS) {
            if (sourceMapping == null || targetMapping == null || !sourceMapping.has(section) || !targetMapping.has(section)
                || !sourceMapping.get(section).isJsonArray() || !targetMapping.get(section).isJsonArray()) {
                continue;
            }

            final JsonObject object = new JsonObject();
            object.add("source", sourceMapping.getAsJsonArray(section));
            object.add("target", targetMapping.getAsJsonArray(section));
            object.add("entries", entries(diff.getAsJsonObject(section)));
            mappings.add(section, object);
        }
        return mappings;
    }

    private JsonObject nameMappings(final VersionPair pair, final Path diffPath) throws IOException {
        final JsonObject sourceMapping = MappingsLoader.load(MAPPING_FILE_FORMAT.formatted(pair.from()));
        final JsonObject diff = loadOrCreateDiff(diffPath);
        final JsonObject mappings = new JsonObject();
        addNameMapping(mappings, sourceMapping, diff, "itemnames", "items");
        addNameMapping(mappings, sourceMapping, diff, "entitynames", "entities");
        return mappings;
    }

    private static void addNameMapping(final JsonObject mappings, final JsonObject sourceMapping, final JsonObject diff, final String section, final String sourceKey) {
        if (sourceMapping == null || !sourceMapping.has(sourceKey) || !sourceMapping.get(sourceKey).isJsonArray()) {
            return;
        }

        final JsonObject object = new JsonObject();
        object.add("source", sourceMapping.getAsJsonArray(sourceKey));
        object.add("entries", entries(diff.getAsJsonObject(section)));
        mappings.add(section, object);
    }

    private static JsonArray entries(final JsonObject object) {
        final JsonArray entries = new JsonArray();
        if (object == null) {
            return entries;
        }

        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final JsonObject item = new JsonObject();
            item.addProperty("key", entry.getKey());
            item.addProperty("value", entry.getValue().getAsString());
            entries.add(item);
        }
        return entries;
    }

    private JsonObject loadOrCreateDiff(final Path diffPath) throws IOException {
        if (!Files.exists(diffPath)) {
            return new JsonObject();
        }

        try (final BufferedReader reader = Files.newBufferedReader(diffPath)) {
            return GsonUtil.GSON.fromJson(reader, JsonObject.class);
        }
    }

    private JsonArray versions() throws IOException {
        final JsonArray array = new JsonArray();
        mappingVersions().forEach(array::add);
        return array;
    }

    private static VersionPair defaultPair() throws IOException {
        final TreeSet<String> versions = mappingVersions();
        if (versions.size() < 2) {
            throw new IllegalStateException("Need at least two mapping files to choose default versions");
        }

        final String newest = versions.pollLast();
        return new VersionPair(versions.last(), newest);
    }

    private static TreeSet<String> mappingVersions() throws IOException {
        final TreeSet<String> versions = new TreeSet<>(Version::compare);
        try (final Stream<@NotNull Path> paths = Files.list(MAPPINGS_DIR)) {
            paths.map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("mapping-") && name.endsWith(".json"))
                .map(name -> name.substring("mapping-".length(), name.length() - ".json".length()))
                .forEach(versions::add);
        }
        return versions;
    }

    private static void collectCompactConflicts(
        final JsonObject blockstates,
        final String key,
        final String value,
        final boolean overwrite,
        final JsonArray conflicts
    ) {
        if (!isCompact(key, value)) {
            return;
        }

        final String keyPrefix = key + "[";
        for (final Map.Entry<String, JsonElement> entry : blockstates.entrySet()) {
            final String existingKey = entry.getKey();
            if (!existingKey.startsWith(keyPrefix)) {
                continue;
            }

            final String existingValue = entry.getValue().getAsString();
            if (existingValue.isEmpty() || existingValue.equals(compactValue(value, existingKey))) {
                continue;
            }
            if (!overwrite) {
                conflicts.add(existingKey);
            }
        }
    }

    private static void collectBareFallbackConflict(
        final JsonObject blockstates,
        final String key,
        final boolean overwrite,
        final JsonArray conflicts
    ) {
        if (overwrite) {
            return;
        }

        final String bareKey = bareKey(key);
        if (bareKey == null) {
            return;
        }

        final JsonElement bareElement = blockstates.get(bareKey);
        if (bareElement != null && !bareElement.getAsString().isEmpty()) {
            conflicts.add(bareKey);
        }
    }

    private static int removeBareFallbackForSpecific(final JsonObject blockstates, final String key, final boolean overwrite) {
        final String bareKey = bareKey(key);
        if (bareKey == null || !blockstates.has(bareKey)) {
            return 0;
        }

        final String bareValue = blockstates.get(bareKey).getAsString();
        if (!overwrite && !bareValue.isEmpty()) {
            return 0;
        }

        blockstates.remove(bareKey);
        return 1;
    }

    private static int removeEntriesCoveredByCompact(
        final JsonObject blockstates,
        final String key,
        final String value,
        final boolean overwrite
    ) {
        if (!isCompact(key, value)) {
            return 0;
        }

        int removed = 0;
        final String keyPrefix = key + "[";
        for (final String existingKey : Set.copyOf(blockstates.keySet())) {
            if (!existingKey.startsWith(keyPrefix)) {
                continue;
            }

            final String existingValue = blockstates.get(existingKey).getAsString();
            if (overwrite || existingValue.isEmpty() || existingValue.equals(compactValue(value, existingKey))) {
                blockstates.remove(existingKey);
                removed++;
            }
        }
        return removed;
    }

    private static boolean isCoveredByExistingCompact(final JsonObject blockstates, final String key, final String value) {
        final int propertyStart = key.indexOf('[');
        if (propertyStart == -1) {
            return false;
        }

        final String compactKey = key.substring(0, propertyStart);
        final JsonElement compactElement = blockstates.get(compactKey);
        if (compactElement == null) {
            return false;
        }

        final String compactValue = compactElement.getAsString();
        return isCompact(compactKey, compactValue) && compactValue(compactValue, key).equals(value);
    }

    private static boolean isCompact(final String key, final String value) {
        return key.indexOf('[') == -1 && value.endsWith("[");
    }

    private static String compactValue(final String compactValue, final String fullKey) {
        return compactValue + fullKey.substring(fullKey.indexOf('[') + 1);
    }

    private static String bareKey(final String key) {
        final int propertyStart = key.indexOf('[');
        return propertyStart == -1 ? null : key.substring(0, propertyStart);
    }

    private VersionPair pair(final String from, final String to) {
        return new VersionPair(
            from == null || from.isBlank() ? defaultFrom : from,
            to == null || to.isBlank() ? defaultTo : to
        );
    }

    private static Path diffPath(final VersionPair pair) {
        return MAPPINGS_DIR.resolve("diff").resolve("mapping-" + pair.from() + "to" + pair.to() + ".json");
    }

    private static Map<String, String> query(final HttpExchange exchange) {
        final String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        final Map<String, String> values = new HashMap<>();
        for (final String part : query.split("&")) {
            final String[] split = part.split("=", 2);
            final String key = URLDecoder.decode(split[0], StandardCharsets.UTF_8);
            final String value = split.length == 2 ? URLDecoder.decode(split[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static String string(final JsonObject object, final String key) {
        return object.has(key) ? object.get(key).getAsString() : null;
    }

    private static JsonObject parseState(final String raw) {
        final JsonObject object = new JsonObject();
        object.addProperty("raw", raw);

        final int start = raw.indexOf('[');
        if (start == -1 || !raw.endsWith("]")) {
            object.addProperty("block", raw);
            object.add("properties", new JsonObject());
            return object;
        }

        object.addProperty("block", raw.substring(0, start));
        final JsonObject properties = new JsonObject();
        final String propertyString = raw.substring(start + 1, raw.length() - 1);
        for (final String property : propertyString.split(",")) {
            final String[] split = property.split("=", 2);
            if (split.length == 2) {
                properties.addProperty(split[0], split[1]);
            }
        }
        object.add("properties", properties);
        return object;
    }

    private static String index() throws IOException {
        try (final InputStream stream = BlockStateMappingUi.class.getResourceAsStream(INDEX_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing resource " + INDEX_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void send(final HttpExchange exchange, final int status, final String contentType, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static boolean method(final HttpExchange exchange, final RequestMethod method) throws IOException {
        if (method.matches(exchange)) {
            return true;
        }

        exchange.getResponseHeaders().set("Allow", method.value());
        if (exchange.getRequestURI().getPath().startsWith("/api/")) {
            send(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        } else {
            send(exchange, 405, "text/plain", "Method not allowed");
        }
        return false;
    }

    private record VersionPair(String from, String to) {
    }

    private record ApplyResult(int written, int skipped, int removed) {
    }

    private static final class ApplyConflictException extends RuntimeException {
        private ApplyConflictException(final String message) {
            super(message);
        }
    }

    private enum RequestMethod {
        GET,
        POST;

        private boolean matches(final HttpExchange exchange) {
            return value().equals(exchange.getRequestMethod());
        }

        private String value() {
            return name();
        }
    }
}
