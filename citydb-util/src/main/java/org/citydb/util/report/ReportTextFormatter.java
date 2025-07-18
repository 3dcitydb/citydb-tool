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
import java.util.Map;
import java.util.function.Consumer;

public class ReportTextFormatter {
    private final ReportOptions options;

    ReportTextFormatter(ReportOptions options) {
        this.options = options;
    }

    void format(JSONObject report, Consumer<String> consumer) {
        consumer.accept(getTitle("Overview"));
        formatSummary(getJSONObject(report, "summary"), consumer);

        if (options.isIncludeDatabaseSize()) {
            consumer.accept("");
            consumer.accept(getTitle("Database Size"));
            formatDatabaseSize(getJSONObject(getJSONObject(report, "database"), "size"), consumer);
        }

        consumer.accept("");
        consumer.accept(getTitle("Feature Statistics"));
        formatFeatures(getJSONObject(report, "features"), consumer);
        consumer.accept("");
        consumer.accept(getTitle("Geometry Statistics"));
        formatGeometries(getJSONObject(report, "geometries"), consumer);
        consumer.accept("");
        consumer.accept(getTitle("Appearance Statistics"));
        formatAppearances(getJSONObject(report, "appearances"), consumer);
        consumer.accept("");
        consumer.accept(getTitle("Extensions"));
        formatExtensions(getJSONArray(report, "extensions"), consumer);
        consumer.accept("");
        consumer.accept(getTitle("Codelists"));
        formatCodelists(getJSONArray(report, "codeLists"), consumer);

        if (options.isIncludeGenericAttributes()) {
            consumer.accept("");
            consumer.accept(getTitle("Generic Attributes"));
            formatGenericAttributes(getJSONObject(report, "genericAttributes"), consumer);
        }
    }

    private void formatSummary(JSONObject summary, Consumer<String> consumer) {
        consumer.accept("Top-level features: " + join(getJSONArray(summary, "topLevelFeatures"), String.class));
        consumer.accept("Levels of detail: " + join(getJSONArray(summary, "lods"), "\"", String.class));
        consumer.accept("Themes: " + join(getJSONArray(summary, "themes"), "\"", String.class));
        consumer.accept("Extent: " + join(getJSONArray(summary, "extent"), Double.class));
        consumer.accept("WGS84 extent: " + join(getJSONArray(summary, "wgs84Extent"), Double.class));
    }

    private void formatDatabaseSize(JSONObject databaseSize, Consumer<String> consumer) {
        Map<String, Long> tableSizes = getJSONObject(databaseSize, "byTable").to(new TypeReference<>() {
        }.getType());

        consumer.accept("Total database: " + DatabaseSize.formatSize(databaseSize.getLongValue("databaseSize")));
        consumer.accept("Schema: " + DatabaseSize.formatSize(databaseSize.getLongValue("schemaSize")));
        consumer.accept("Content Tables (by size):");
        tableSizes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> consumer.accept(getListItem(e.getKey()) + ": " + DatabaseSize.formatSize(e.getValue())));
    }

    private void formatFeatures(JSONObject features, Consumer<String> consumer) {
        consumer.accept("Total features: " + features.getIntValue("featureCount"));
        consumer.accept("Top-level features: " + features.getIntValue("topLevelFeatureCount"));
        consumer.accept("Terminated features: " + features.getIntValue("terminatedFeatureCount"));
        consumer.accept("Address records: " + features.getIntValue("addressCount"));

        JSONObject byType = getJSONObject(features, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Feature types:");
            byType.forEach((type, count) -> consumer.accept(getListItem(type) + ": " + count));
        } else {
            consumer.accept("Feature types: none");
        }

        JSONObject byLod = getJSONObject(features, "byLod");
        if (!byLod.isEmpty()) {
            consumer.accept("Level of detail:");
            byLod.forEach((lod, count) -> consumer.accept(getListItem(lod, "\"") + ": " + count));
        } else {
            consumer.accept("Levels of detail: none");
        }
    }

    private void formatGeometries(JSONObject geometries, Consumer<String> consumer) {
        consumer.accept("Total geometries: " + geometries.getIntValue("geometryCount"));
        consumer.accept("Implicit geometries: " + geometries.getIntValue("implicitGeometryCount"));

        JSONObject byType = getJSONObject(geometries, "byType");
        if (!byType.isEmpty()) {
            consumer.accept("Geometry types:");
            byType.forEach((type, count) -> consumer.accept(getListItem(type) + ": " + count));
        } else {
            consumer.accept("Geometry types: none");
        }
    }

    private void formatAppearances(JSONObject appearances, Consumer<String> consumer) {
        consumer.accept("Total appearances: " + appearances.getIntValue("appearanceCount"));
        consumer.accept("Global appearances: " + getBoolean(appearances, "hasGlobalAppearances"));
        consumer.accept("Materials: " + getBoolean(appearances, "hasMaterials"));
        consumer.accept("Textures: " + getBoolean(appearances, "hasTextures"));
        consumer.accept("Georeferenced textures: " + getBoolean(appearances, "hasGeoreferencedTextures"));

        JSONObject byType = getJSONObject(appearances, "byTheme");
        if (!byType.isEmpty()) {
            consumer.accept("Themes:");
            byType.forEach((theme, count) -> consumer.accept(getListItem(theme, "\"") + ": " + count));
        } else {
            consumer.accept("Themes: none");
        }
    }

    private void formatExtensions(JSONArray extensions, Consumer<String> consumer) {
        consumer.accept("Total extensions: " + extensions.size());
        extensions.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .forEach(extension -> consumer.accept(getListItem(extension.getString("name"), "\"") + ": " +
                        extension.getString("version")));
    }

    private void formatCodelists(JSONArray codeLists, Consumer<String> consumer) {
        consumer.accept("Total codelists: " + codeLists.size());
        codeLists.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .forEach(codeList -> consumer.accept(getListItem(codeList.getString("type")) + ": " +
                        codeList.getString("identifier")));
    }

    private void formatGenericAttributes(JSONObject genericAttributes, Consumer<String> consumer) {
        consumer.accept("Total attributes: " + genericAttributes.size());
        genericAttributes.forEach((name, types) ->
                consumer.accept(getListItem(name, "\"") + ": " + join((JSONArray) types, String.class)));
    }

    private JSONObject getJSONObject(JSONObject parent, String key) {
        JSONObject object = parent.getJSONObject(key);
        return object != null ? object : new JSONObject();
    }

    private JSONArray getJSONArray(JSONObject parent, String key) {
        JSONArray array = parent.getJSONArray(key);
        return array != null ? array : new JSONArray();
    }

    private String getTitle(String title) {
        return "=== " + title + " ===";
    }

    private String getListItem(String title) {
        return getListItem(title, "");
    }

    private String getListItem(String title, String quote) {
        return "- " + quote + title + quote;
    }

    private String getBoolean(JSONObject object, String key) {
        return object.getBooleanValue(key) ? "yes" : "no";
    }

    private String join(JSONArray array, Class<?> type) {
        return join(array, "", type);
    }

    private String join(JSONArray array, String quote, Class<?> type) {
        return !array.isEmpty() ?
                String.join(", ", array.toJavaList(type).stream()
                        .map(String::valueOf)
                        .map(v -> quote + v + quote)
                        .toList()) :
                "none";
    }
}
