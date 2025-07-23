/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.util.report;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import org.citydb.database.metadata.DatabaseSize;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
        JSONObject jsonReport = report.toJSON();
        ReportOptions options = report.getOptions();

        consumer.accept(getTitle("Overview"));
        buildSummary(getJSONObject(jsonReport, "summary"), consumer);

        if (options.isIncludeDatabaseSize()) {
            addSectionSpacing(consumer);
            consumer.accept(getTitle("Database Size"));
            buildDatabaseSize(getJSONObject(getJSONObject(jsonReport, "database"), "size"), consumer);
        }

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Feature Statistics"));
        buildFeatures(getJSONObject(jsonReport, "features"), consumer);

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Geometry Statistics"));
        buildGeometries(getJSONObject(jsonReport, "geometries"), consumer);

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Address Statistics"));
        buildAddresses(getJSONObject(jsonReport, "addresses"), consumer);

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Appearance Statistics"));
        buildAppearances(getJSONObject(jsonReport, "appearances"), consumer);

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Extensions"));
        buildExtensions(getJSONArray(jsonReport, "extensions"), consumer);

        addSectionSpacing(consumer);
        consumer.accept(getTitle("Codelists"));
        buildCodelists(getJSONObject(jsonReport, "codeLists"), consumer);

        if (options.isIncludeGenericAttributes()) {
            addSectionSpacing(consumer);
            consumer.accept(getTitle("Generic Attributes"));
            buildGenericAttributes(getJSONObject(jsonReport, "genericAttributes"), consumer);
        }

        if (withEndOfReport) {
            buildEndOfReport(consumer);
        }
    }

    private void buildSummary(JSONObject summary, Consumer<String> consumer) {
        consumer.accept("Top-level features: " + join(getJSONArray(summary, "topLevelFeatures"), String.class));
        consumer.accept("Levels of detail: " + quoteAndJoin(getJSONArray(summary, "lods"), String.class));
        consumer.accept("Themes: " + quoteAndJoin(getJSONArray(summary, "themes"), String.class));
        consumer.accept("Extent: " + getExtent(getJSONArray(summary, "extent")));
        consumer.accept("WGS84 extent: " + getExtent(getJSONArray(summary, "wgs84Extent")));
    }

    private void buildDatabaseSize(JSONObject databaseSize, Consumer<String> consumer) {
        Map<String, Long> tableSizes = getJSONObject(databaseSize, "byTable").to(new TypeReference<>() {
        }.getType());

        consumer.accept("Total database: " + DatabaseSize.formatSize(databaseSize.getLongValue("databaseSize")));
        consumer.accept("Schema: " + DatabaseSize.formatSize(databaseSize.getLongValue("schemaSize")));
        consumer.accept("Content tables by size:");
        tableSizes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> consumer.accept(getListItem(e.getKey() + ": " + DatabaseSize.formatSize(e.getValue()))));
    }

    private void buildFeatures(JSONObject features, Consumer<String> consumer) {
        consumer.accept("Total features: " + getFeatureCount(getJSONObject(features, "featureCount")));
        consumer.accept("Top-level features: " + getFeatureCount(getJSONObject(features, "topLevelFeatureCount")));

        JSONObject byType = getJSONObject(features, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Feature types:");
            byType.entrySet().stream()
                    .filter(e -> e.getValue() instanceof JSONObject)
                    .forEach(e -> consumer.accept(getListItem(e.getKey()) + ": " +
                            getFeatureCount((JSONObject) e.getValue())));
        } else {
            consumer.accept("Feature types: none");
        }
    }

    private void buildGeometries(JSONObject geometries, Consumer<String> consumer) {
        consumer.accept("Total geometries: " + geometries.getLongValue("geometryCount"));
        consumer.accept("Implicit geometries: " + geometries.getLongValue("implicitGeometryCount"));

        JSONObject byType = getJSONObject(geometries, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Geometry types:");
            byType.forEach((type, count) -> consumer.accept(getListItem(type + ": " + count)));
        } else {
            consumer.accept("Geometry types: none");
        }

        JSONObject byLod = getJSONObject(geometries, "byLod");
        if (!byLod.isEmpty()) {
            consumer.accept("Levels of detail:");
            byLod.forEach((lod, count) -> consumer.accept(getListItem(quote(lod) + ": " + count)));
        } else {
            consumer.accept("Levels of detail: none");
        }
    }

    private void buildAddresses(JSONObject addresses, Consumer<String> consumer) {
        consumer.accept("Total addresses: " + addresses.getLongValue("addressCount"));
    }

    private void buildAppearances(JSONObject appearances, Consumer<String> consumer) {
        consumer.accept("Total appearances: " + appearances.getLongValue("appearanceCount"));
        consumer.accept("Global appearances: " + getBoolean(appearances, "hasGlobalAppearances"));
        consumer.accept("Materials: " + getBoolean(appearances, "hasMaterials"));
        consumer.accept("Textures: " + getBoolean(appearances, "hasTextures"));
        consumer.accept("Georeferenced textures: " + getBoolean(appearances, "hasGeoreferencedTextures"));

        JSONObject byType = getJSONObject(appearances, "byTheme");
        if (!byType.isEmpty()) {
            consumer.accept("Themes:");
            byType.forEach((theme, count) -> consumer.accept(getListItem(quote(theme) + ": " + count)));
        } else {
            consumer.accept("Themes: none");
        }
    }

    private void buildExtensions(JSONArray extensions, Consumer<String> consumer) {
        consumer.accept("Total extensions: " + extensions.size());
        extensions.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .forEach(extension -> consumer.accept(getListItem(quote(extension.getString("name") + ": " +
                        extension.getString("version")))));
    }

    private void buildCodelists(JSONObject codeLists, Consumer<String> consumer) {
        consumer.accept("Total codelists: " + codeLists.keySet().stream()
                .map(type -> getJSONArray(codeLists, type))
                .mapToInt(JSONArray::size)
                .sum());
        codeLists.keySet().forEach(type ->
                consumer.accept(getListItem(type) + ": " + getJSONArray(codeLists, type).size()));
    }

    private void buildGenericAttributes(JSONObject genericAttributes, Consumer<String> consumer) {
        consumer.accept("Total attributes: " + genericAttributes.size());
        genericAttributes.forEach((name, types) ->
                consumer.accept(getListItem(quote(name) + ": " + join((JSONArray) types, String.class))));
    }

    private void buildEndOfReport(Consumer<String> consumer) {
        addSectionSpacing(consumer);
        consumer.accept(getTitle("End of Report"));
    }

    private JSONObject getJSONObject(JSONObject parent, String key) {
        JSONObject object = parent.getJSONObject(key);
        return object != null ? object : new JSONObject();
    }

    private JSONArray getJSONArray(JSONObject parent, String key) {
        JSONArray array = parent.getJSONArray(key);
        return array != null ? array : new JSONArray();
    }

    private String getBoolean(JSONObject object, String key) {
        return object.getBooleanValue(key) ? "yes" : "no";
    }

    private String getFeatureCount(JSONObject object) {
        long active = object.getLongValue("active");
        long terminated = object.getLongValue("terminated");
        return (active + terminated) + " (" + terminated + " terminated)";
    }

    private String getExtent(JSONArray extent) {
        if (!extent.isEmpty()) {
            List<String> hallo = extent.toList(Double.class).stream()
                    .map(String::valueOf)
                    .toList();
            int index = extent.size() / 2;
            return "[" + String.join(", ", hallo.subList(0, index)) + "] to [" +
                    String.join(", ", hallo.subList(index, hallo.size())) + "]";
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

    private String join(JSONArray array, Class<?> type) {
        return join(array, type, false);
    }

    private String quoteAndJoin(JSONArray array, Class<?> type) {
        return join(array, type, true);
    }

    private String join(JSONArray array, Class<?> type, boolean quote) {
        return !array.isEmpty() ?
                String.join(", ", array.toList(type).stream()
                        .map(String::valueOf)
                        .map(v -> quote ? quote(v) : v)
                        .toList()) :
                "none";
    }
}
