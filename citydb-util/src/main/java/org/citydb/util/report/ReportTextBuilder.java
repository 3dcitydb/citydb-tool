/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.report;

import org.citydb.core.json.JsonMapping;
import org.citydb.database.metadata.DatabaseSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class ReportTextBuilder {
    private String titlePrefix = "=== ";
    private String titleSuffix = " ===";
    private String listMarker = "  - ";
    private String quote = "\"";
    private boolean withSectionSpacing;
    private boolean withEndOfReport = true;

    protected ReportTextBuilder() {
    }

    public static ReportTextBuilder newInstance() {
        return new ReportTextBuilder();
    }

    public ReportTextBuilder withTitlePrefix(String titlePrefix) {
        this.titlePrefix = titlePrefix != null ? titlePrefix : this.titlePrefix;
        return this;
    }

    public ReportTextBuilder withTitleSuffix(String titleSuffix) {
        this.titleSuffix = titleSuffix != null ? titleSuffix : this.titleSuffix;
        return this;
    }

    public ReportTextBuilder withListMarker(String listMarker) {
        this.listMarker = listMarker != null ? listMarker : this.listMarker;
        return this;
    }

    public ReportTextBuilder withQuote(String quote) {
        this.quote = quote != null ? quote : this.quote;
        return this;
    }

    public ReportTextBuilder withSectionSpacing(boolean withSectionSpacing) {
        this.withSectionSpacing = withSectionSpacing;
        return this;
    }

    public ReportTextBuilder withAddEndOfReport(boolean withEndOfReport) {
        this.withEndOfReport = withEndOfReport;
        return this;
    }

    public void build(DatabaseReport report, Consumer<String> consumer) {
        ObjectNode jsonReport = report.toJSON();
        ReportOptions options = report.getOptions();

        consumer.accept(getTitle("Overview"));
        buildSummary(getObject(jsonReport, "summary"), consumer);

        if (options.isIncludeDatabaseSize()) {
            addSectionSpacing(consumer);
            consumer.accept(getTitle("Database Size"));
            buildDatabaseSize(getObject(getObject(jsonReport, "database"), "size"), consumer);
        }

        if (!options.isCompact()) {
            addSectionSpacing(consumer);
            consumer.accept(getTitle("Feature Statistics"));
            buildFeatures(getObject(jsonReport, "features"), consumer);

            addSectionSpacing(consumer);
            consumer.accept(getTitle("Geometry Statistics"));
            buildGeometries(getObject(jsonReport, "geometries"), consumer);

            addSectionSpacing(consumer);
            consumer.accept(getTitle("Address Statistics"));
            buildAddresses(getObject(jsonReport, "addresses"), consumer);

            addSectionSpacing(consumer);
            consumer.accept(getTitle("Appearance Statistics"));
            buildAppearances(getObject(jsonReport, "appearances"), consumer);

            addSectionSpacing(consumer);
            consumer.accept(getTitle("Extensions"));
            buildExtensions(getArray(jsonReport, "extensions"), consumer);

            addSectionSpacing(consumer);
            consumer.accept(getTitle("Codelists"));
            buildCodelists(getObject(jsonReport, "codeLists"), consumer);
        }

        if (options.isIncludeGenericAttributes()) {
            addSectionSpacing(consumer);
            consumer.accept(getTitle("Generic Attributes"));
            buildGenericAttributes(getObject(jsonReport, "genericAttributes"), consumer);
        }

        if (withEndOfReport) {
            buildEndOfReport(consumer);
        }
    }

    private void buildSummary(ObjectNode summary, Consumer<String> consumer) {
        consumer.accept("Top-level features: " + join(getArray(summary, "topLevelFeatures")));
        consumer.accept("Levels of detail: " + quoteAndJoin(getArray(summary, "lods")));
        consumer.accept("Themes: " + quoteAndJoin(getArray(summary, "themes")));
        consumer.accept("Extent: " + getExtent(getArray(summary, "extent")));
        consumer.accept("WGS84 extent: " + getExtent(getArray(summary, "wgs84Extent")));
    }

    private void buildDatabaseSize(ObjectNode databaseSize, Consumer<String> consumer) {
        consumer.accept("Total database: " + DatabaseSize.formatSize(databaseSize.path("databaseSize").asLong()));
        consumer.accept("Schema: " + DatabaseSize.formatSize(databaseSize.path("schemaSize").asLong()));
        consumer.accept("Content tables by size:");
        getObject(databaseSize, "byTable").properties().stream()
                .sorted(Map.Entry.<String, JsonNode>comparingByValue(
                        (a, b) -> Long.compare(b.asLong(), a.asLong())))
                .forEach(e -> consumer.accept(getListItem(e.getKey() + ": " +
                        DatabaseSize.formatSize(e.getValue().asLong()))));
    }

    private void buildFeatures(ObjectNode features, Consumer<String> consumer) {
        consumer.accept("Total features: " + getFeatureCount(getObject(features, "featureCount")));
        consumer.accept("Top-level features: " + getFeatureCount(getObject(features, "topLevelFeatureCount")));

        ObjectNode byType = getObject(features, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Feature types:");
            byType.properties().stream()
                    .filter(e -> e.getValue().isObject())
                    .forEach(e -> consumer.accept(getListItem(e.getKey()) + ": " +
                            getFeatureCount(e.getValue())));
        } else {
            consumer.accept("Feature types: none");
        }
    }

    private void buildGeometries(ObjectNode geometries, Consumer<String> consumer) {
        consumer.accept("Total geometries: " + geometries.path("geometryCount").asLong());
        consumer.accept("Implicit geometries: " + geometries.path("implicitGeometryCount").asLong());

        ObjectNode byType = getObject(geometries, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Geometry types:");
            byType.properties().forEach(e -> consumer.accept(getListItem(e.getKey() + ": " + e.getValue().asString())));
        } else {
            consumer.accept("Geometry types: none");
        }

        ObjectNode byLod = getObject(geometries, "byLod");
        if (!byLod.isEmpty()) {
            consumer.accept("Levels of detail:");
            byLod.properties().forEach(e -> consumer.accept(getListItem(quote(e.getKey()) + ": " +
                    e.getValue().asString())));
        } else {
            consumer.accept("Levels of detail: none");
        }
    }

    private void buildAddresses(ObjectNode addresses, Consumer<String> consumer) {
        consumer.accept("Total addresses: " + addresses.path("addressCount").asLong());
    }

    private void buildAppearances(ObjectNode appearances, Consumer<String> consumer) {
        consumer.accept("Total appearances: " + appearances.path("appearanceCount").asLong());
        consumer.accept("Global appearances: " + getBoolean(appearances, "hasGlobalAppearances"));
        consumer.accept("Materials: " + getBoolean(appearances, "hasMaterials"));
        consumer.accept("Textures: " + getBoolean(appearances, "hasTextures"));
        consumer.accept("Georeferenced textures: " + getBoolean(appearances, "hasGeoreferencedTextures"));

        ObjectNode byTheme = getObject(appearances, "byTheme");
        if (!byTheme.isEmpty()) {
            consumer.accept("Themes:");
            byTheme.properties().forEach(e -> consumer.accept(getListItem(quote(e.getKey()) + ": " +
                    e.getValue().asString())));
        } else {
            consumer.accept("Themes: none");
        }
    }

    private void buildExtensions(ArrayNode extensions, Consumer<String> consumer) {
        consumer.accept("Total extensions: " + extensions.size());
        for (JsonNode extension : extensions) {
            if (extension.isObject()) {
                consumer.accept(getListItem(quote(getString(extension, "name")) + ": " +
                        quote(getString(extension, "version"))));
            }
        }
    }

    private void buildCodelists(ObjectNode codeLists, Consumer<String> consumer) {
        consumer.accept("Total codelists: " + codeLists.propertyNames().stream()
                .map(type -> getArray(codeLists, type))
                .mapToInt(ArrayNode::size)
                .sum());
        codeLists.propertyNames().forEach(type ->
                consumer.accept(getListItem(type) + ": " + getArray(codeLists, type).size()));
    }

    private void buildGenericAttributes(ObjectNode genericAttributes, Consumer<String> consumer) {
        consumer.accept("Total attributes: " + StreamSupport.stream(genericAttributes.spliterator(), false)
                .filter(JsonNode::isObject)
                .mapToInt(JsonNode::size)
                .sum());
        genericAttributes.properties().forEach(e -> {
            if (e.getValue() instanceof ObjectNode object) {
                consumer.accept(e.getKey() + ":");
                object.properties().forEach(attribute ->
                        consumer.accept(getListItem(quote(attribute.getKey()) + ": " +
                                join(attribute.getValue() instanceof ArrayNode types ? types : emptyArray()))));
            }
        });
    }

    private void buildEndOfReport(Consumer<String> consumer) {
        addSectionSpacing(consumer);
        consumer.accept(getTitle("End of Report"));
    }

    private ObjectNode getObject(JsonNode parent, String key) {
        return parent.get(key) instanceof ObjectNode object ? object : JsonMapping.mapper().createObjectNode();
    }

    private ArrayNode getArray(JsonNode parent, String key) {
        return parent.get(key) instanceof ArrayNode array ? array : emptyArray();
    }

    private ArrayNode emptyArray() {
        return JsonMapping.mapper().createArrayNode();
    }

    private String getString(JsonNode object, String key) {
        JsonNode value = object.get(key);
        return value != null && !value.isNull() ? value.asString() : null;
    }

    private String getBoolean(JsonNode object, String key) {
        return object.path(key).asBoolean(false) ? "yes" : "no";
    }

    private String getFeatureCount(JsonNode object) {
        long active = object.path("active").asLong();
        long terminated = object.path("terminated").asLong();
        return (active + terminated) + " (" + terminated + " terminated)";
    }

    private String getExtent(ArrayNode extent) {
        if (!extent.isEmpty()) {
            List<String> coordinates = new ArrayList<>(extent.size());
            extent.forEach(value -> coordinates.add(String.valueOf(value.asDouble())));
            int index = extent.size() / 2;
            return "[" + String.join(", ", coordinates.subList(0, index)) + "] to [" +
                    String.join(", ", coordinates.subList(index, coordinates.size())) + "]";
        } else {
            return "none";
        }
    }

    private void addSectionSpacing(Consumer<String> consumer) {
        if (withSectionSpacing) {
            consumer.accept("");
        }
    }

    private String getTitle(String title) {
        return titlePrefix + title + titleSuffix;
    }

    private String getListItem(String value) {
        return listMarker + value;
    }

    private String quote(String value) {
        return quote + value + quote;
    }

    private String join(ArrayNode array) {
        return join(array, false);
    }

    private String quoteAndJoin(ArrayNode array) {
        return join(array, true);
    }

    private String join(ArrayNode array, boolean quote) {
        if (array.isEmpty()) {
            return "none";
        }

        List<String> values = new ArrayList<>(array.size());
        array.forEach(value -> {
            String text = value.isNull() ? "null" : value.asString();
            values.add(quote ? quote(text) : text);
        });

        return String.join(", ", values);
    }
}
