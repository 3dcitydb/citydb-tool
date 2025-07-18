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
import org.citydb.core.time.TimeHelper;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.schema.Table;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Envelope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ReportJsonBuilder {
    private final DatabaseReport report;
    private final ReportOptions options;
    private final DatabaseAdapter adapter;
    private final SchemaMapping schemaMapping;

    ReportJsonBuilder(DatabaseReport report, ReportOptions options, DatabaseAdapter adapter) {
        this.report = report;
        this.options = options;
        this.adapter = adapter;
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
    }

    JSONObject build() {
        JSONObject jsonReport = new JSONObject();
        jsonReport.fluentPut("metadata", buildMetadata())
                .fluentPut("summary", buildSummary())
                .fluentPut("database", buildDatabase())
                .fluentPut("features", buildFeatures())
                .fluentPut("geometries", buildGeometries())
                .fluentPut("appearances", buildAppearances())
                .fluentPut("extensions", buildExtensions())
                .fluentPut("codeLists", buildCodeLists())
                .fluentPut("modules", new JSONObject(report.getModules()));

        if (options.isIncludeGenericAttributes()) {
            jsonReport.put("genericAttributes", buildGenericAttributes());
        }

        return jsonReport;
    }

    private JSONObject buildMetadata() {
        String timestamp = TimeHelper.toDateTime(LocalDateTime.now().withNano(0))
                .format(TimeHelper.DATE_TIME_FORMATTER);
        String featureScope = options.isOnlyPropertiesOfValidFeatures() ? "valid" : "all";

        return new JSONObject()
                .fluentPut("reportGenerated", timestamp)
                .fluentPut("featureScope", featureScope)
                .fluentPut("genericAttributesProcessed", options.isIncludeGenericAttributes());
    }

    private JSONObject buildSummary() {
        JSONObject summary = new JSONObject();
        List<String> topLevelFeatures = report.getFeatures().keySet().stream()
                .filter(name -> schemaMapping.getFeatureType(PrefixedName.of(name)).isTopLevel())
                .toList();

        return summary.fluentPut("topLevelFeatures", topLevelFeatures)
                .fluentPut("lods", new JSONArray(report.getLods().keySet()))
                .fluentPut("themes", new JSONArray(report.getAppearances().keySet()))
                .fluentPut("crs", buildCrs())
                .fluentPut("extent", convertExtent(report.getExtent(), 4))
                .fluentPut("wgs84Extent", convertExtent(report.getWgs84Extent(), 6));
    }

    private JSONObject buildDatabase() {
        JSONObject database = new JSONObject();
        database.fluentPut("type", DatabaseConstants.CITYDB_NAME)
                .fluentPut("version", adapter.getDatabaseMetadata().getVersion().toString())
                .fluentPut("dbms", buildDbms())
                .fluentPut("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled())
                .fluentPut("connection", buildDatabaseConnection());

        DatabaseSize databaseSize = report.getDatabaseSize().orElse(null);
        if (options.isIncludeDatabaseSize() && databaseSize != null) {
            database.put("size", buildDatabaseSize(databaseSize));
        }

        return database;
    }

    private JSONObject buildDbms() {
        JSONObject dbms = new JSONObject();
        dbms.fluentPut("type", adapter.getDatabaseMetadata().getVendorProductName())
                .fluentPut("version", adapter.getDatabaseMetadata().getVendorProductVersion());

        if (adapter.getDatabaseMetadata().hasProperties()) {
            dbms.put("properties", buildDbmsProperties());
        }

        return dbms;
    }

    private JSONObject buildDbmsProperties() {
        JSONObject properties = new JSONObject();
        adapter.getDatabaseMetadata().getProperties().forEach((id, property) -> {
            properties.putObject(id)
                    .fluentPut("name", property.getName())
                    .fluentPut("value", property.getValue().orElse("n/a"));
        });

        return properties;
    }

    private JSONObject buildFeatures() {
        JSONObject features = new JSONObject();
        long featureCount = sum(report.getFeatures().values());
        long topLevelFeatureCount = report.getFeatures().entrySet().stream()
                .filter(e -> schemaMapping.getFeatureType(PrefixedName.of(e.getKey())).isTopLevel())
                .mapToLong(Map.Entry::getValue).sum();
        long terminatedFeatureCount = sum(report.getTerminatedFeatures().values());

        return features.fluentPut("featureCount", featureCount)
                .fluentPut("topLevelFeatureCount", topLevelFeatureCount)
                .fluentPut("terminatedFeatureCount", terminatedFeatureCount)
                .fluentPut("addressCount", report.getAddressCount())
                .fluentPut("byType", new JSONObject(report.getFeatures()))
                .fluentPut("byLod", new JSONObject(report.getLods()));
    }

    private JSONObject buildGeometries() {
        JSONObject geometries = new JSONObject();
        long geometryCount = sum(report.getGeometries().values());

        return geometries.fluentPut("geometryCount", geometryCount)
                .fluentPut("implicitGeometryCount", report.getImplicitGeometryCount())
                .fluentPut("byType", new JSONObject(report.getGeometries()));
    }

    private JSONObject buildAppearances() {
        JSONObject appearances = new JSONObject();
        long appearanceCount = sum(report.getAppearances().values());

        return appearances.fluentPut("appearanceCount", appearanceCount)
                .fluentPut("hasGlobalAppearances", report.hasGlobalAppearances())
                .fluentPut("hasMaterials", report.hasMaterials())
                .fluentPut("hasTextures", report.hasTextures())
                .fluentPut("hasGeoreferencedTextures", report.hasGeoreferencedTextures())
                .fluentPut("byTheme", new JSONObject(report.getAppearances()));
    }

    private JSONObject buildGenericAttributes() {
        JSONObject genericAttributes = new JSONObject();
        report.getGenericAttributes().forEach((name, types) -> genericAttributes.put(name, new JSONArray(types)));
        return genericAttributes;
    }

    private JSONArray buildExtensions() {
        JSONArray extensions = new JSONArray();
        report.getADEs().forEach((name, value) -> extensions.add(new JSONObject()
                .fluentPut("name", name)
                .fluentPut("description", value.first())
                .fluentPut("version", value.second())));

        return extensions;
    }

    private JSONArray buildCodeLists() {
        JSONArray codeLists = new JSONArray();
        report.getCodeLists().forEach((idenifier, type) -> codeLists.add(new JSONObject()
                .fluentPut("identifier", idenifier)
                .fluentPut("type", type)));

        return codeLists;
    }

    private JSONObject buildCrs() {
        return new JSONObject()
                .fluentPut("srid", adapter.getDatabaseMetadata().getSpatialReference().getSRID())
                .fluentPut("identifier", adapter.getDatabaseMetadata().getSpatialReference().getIdentifier())
                .fluentPut("name", adapter.getDatabaseMetadata().getSpatialReference().getName());
    }

    private JSONObject buildDatabaseConnection() {
        return new JSONObject()
                .fluentPut("host", adapter.getConnectionDetails().getHost())
                .fluentPut("port", adapter.getConnectionDetails().getPort())
                .fluentPut("database", adapter.getConnectionDetails().getDatabase())
                .fluentPut("schema", adapter.getConnectionDetails().getSchema())
                .fluentPut("user", adapter.getConnectionDetails().getUser());
    }

    private JSONObject buildDatabaseSize(DatabaseSize databaseSize) {
        JSONObject size = new JSONObject();
        Map<String, Long> byTable = databaseSize.getTableSizes().entrySet().stream()
                .filter(e -> Table.CONTENT_TABLES.contains(e.getKey()))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        TreeMap::new));

        return size.fluentPut("databaseSize", databaseSize.getDatabaseSize())
                .fluentPut("schemaSize", databaseSize.getSchemaSize())
                .fluentPut("byTable", byTable);
    }

    private List<Double> convertExtent(Envelope extent, int decimalPlaces) {
        if (extent != null && !extent.isEmpty()) {
            return extent.getVertexDimension() == 2 ?
                    List.of(round(extent.getLowerCorner().getX(), decimalPlaces),
                            round(extent.getLowerCorner().getY(), decimalPlaces),
                            round(extent.getUpperCorner().getX(), decimalPlaces),
                            round(extent.getUpperCorner().getY(), decimalPlaces)) :
                    List.of(round(extent.getLowerCorner().getX(), decimalPlaces),
                            round(extent.getLowerCorner().getY(), decimalPlaces),
                            round(extent.getLowerCorner().getZ(), decimalPlaces),
                            round(extent.getUpperCorner().getX(), decimalPlaces),
                            round(extent.getUpperCorner().getY(), decimalPlaces),
                            round(extent.getUpperCorner().getZ(), decimalPlaces));
        } else {
            return Collections.emptyList();
        }
    }

    private long sum(Collection<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }

    private double round(double value, int decimalPlaces) {
        return new BigDecimal(value).setScale(decimalPlaces, RoundingMode.HALF_UP).doubleValue();
    }
}
