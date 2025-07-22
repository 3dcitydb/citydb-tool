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
import org.citydb.database.schema.Table;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Envelope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ReportJsonBuilder {

    protected ReportJsonBuilder() {
    }

    public static ReportJsonBuilder newInstance() {
        return new ReportJsonBuilder();
    }

    public JSONObject build(DatabaseReport report) {
        ReportOptions options = report.getOptions();
        DatabaseAdapter adapter = report.getAdapter();

        JSONObject jsonReport = new JSONObject();
        jsonReport.fluentPut("metadata", buildMetadata(options))
                .fluentPut("summary", buildSummary(report, adapter))
                .fluentPut("database", buildDatabase(report, options, adapter))
                .fluentPut("features", buildFeatures(report, adapter))
                .fluentPut("geometries", buildGeometries(report))
                .fluentPut("appearances", buildAppearances(report))
                .fluentPut("extensions", buildExtensions(report))
                .fluentPut("codeLists", buildCodeLists(report))
                .fluentPut("modules", new JSONObject(report.getModules()));

        if (options.isIncludeGenericAttributes()) {
            jsonReport.put("genericAttributes", buildGenericAttributes(report));
        }

        return jsonReport;
    }

    private JSONObject buildMetadata(ReportOptions options) {
        String timestamp = TimeHelper.toDateTime(LocalDateTime.now().withNano(0))
                .format(TimeHelper.DATE_TIME_FORMATTER);
        String featureScope = options.isOnlyActiveFeatures() ? "valid" : "all";

        return new JSONObject()
                .fluentPut("reportGenerated", timestamp)
                .fluentPut("featureScope", featureScope)
                .fluentPut("genericAttributesProcessed", options.isIncludeGenericAttributes());
    }

    private JSONObject buildSummary(DatabaseReport report, DatabaseAdapter adapter) {
        List<String> topLevelFeatures = report.getFeatures().keySet().stream()
                .filter(name -> adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(PrefixedName.of(name))
                        .isTopLevel())
                .toList();

        return new JSONObject().fluentPut("topLevelFeatures", topLevelFeatures)
                .fluentPut("lods", new JSONArray(report.getLods().keySet()))
                .fluentPut("themes", new JSONArray(report.getAppearances().keySet()))
                .fluentPut("crs", buildCrs(adapter))
                .fluentPut("extent", convertExtent(report.getExtent(), 4))
                .fluentPut("wgs84Extent", convertExtent(report.getWgs84Extent(), 6));
    }

    private JSONObject buildDatabase(DatabaseReport report, ReportOptions options, DatabaseAdapter adapter) {
        JSONObject database = new JSONObject();
        database.fluentPut("type", DatabaseConstants.CITYDB_NAME)
                .fluentPut("version", adapter.getDatabaseMetadata().getVersion().toString())
                .fluentPut("dbms", buildDbms(adapter))
                .fluentPut("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled())
                .fluentPut("connection", buildDatabaseConnection(adapter));

        DatabaseSize databaseSize = report.getDatabaseSize().orElse(null);
        if (options.isIncludeDatabaseSize() && databaseSize != null) {
            database.put("size", buildDatabaseSize(databaseSize));
        }

        return database;
    }

    private JSONObject buildDbms(DatabaseAdapter adapter) {
        JSONObject dbms = new JSONObject();
        dbms.fluentPut("type", adapter.getDatabaseMetadata().getVendorProductName())
                .fluentPut("version", adapter.getDatabaseMetadata().getVendorProductVersion());

        if (adapter.getDatabaseMetadata().hasProperties()) {
            dbms.put("properties", buildDbmsProperties(adapter));
        }

        return dbms;
    }

    private JSONObject buildDbmsProperties(DatabaseAdapter adapter) {
        JSONObject properties = new JSONObject();
        adapter.getDatabaseMetadata().getProperties().forEach((id, property) -> {
            properties.putObject(id)
                    .fluentPut("name", property.getName())
                    .fluentPut("value", property.getValue().orElse("n/a"));
        });

        return properties;
    }

    private JSONObject buildFeatures(DatabaseReport report, DatabaseAdapter adapter) {
        long featureCount = sum(report.getFeatures().values());
        long topLevelFeatureCount = report.getFeatures().entrySet().stream()
                .filter(e -> adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(PrefixedName.of(e.getKey()))
                        .isTopLevel())
                .mapToLong(Map.Entry::getValue).sum();
        long terminatedFeatureCount = sum(report.getTerminatedFeatures().values());

        return new JSONObject().fluentPut("featureCount", featureCount)
                .fluentPut("topLevelFeatureCount", topLevelFeatureCount)
                .fluentPut("terminatedFeatureCount", terminatedFeatureCount)
                .fluentPut("addressCount", report.getAddressCount())
                .fluentPut("byType", new JSONObject(report.getFeatures()))
                .fluentPut("byLod", new JSONObject(report.getLods()));
    }

    private JSONObject buildGeometries(DatabaseReport report) {
        long geometryCount = sum(report.getGeometries().values());

        return new JSONObject().fluentPut("geometryCount", geometryCount)
                .fluentPut("implicitGeometryCount", report.getImplicitGeometryCount())
                .fluentPut("byType", new JSONObject(report.getGeometries()));
    }

    private JSONObject buildAppearances(DatabaseReport report) {
        long appearanceCount = sum(report.getAppearances().values());

        return new JSONObject().fluentPut("appearanceCount", appearanceCount)
                .fluentPut("hasGlobalAppearances", report.hasGlobalAppearances())
                .fluentPut("hasMaterials", report.hasMaterials())
                .fluentPut("hasTextures", report.hasTextures())
                .fluentPut("hasGeoreferencedTextures", report.hasGeoreferencedTextures())
                .fluentPut("byTheme", new JSONObject(report.getAppearances()));
    }

    private JSONObject buildGenericAttributes(DatabaseReport report) {
        JSONObject genericAttributes = new JSONObject();
        report.getGenericAttributes().forEach((name, types) -> genericAttributes.put(name, new JSONArray(types)));

        return genericAttributes;
    }

    private JSONArray buildExtensions(DatabaseReport report) {
        JSONArray extensions = new JSONArray();
        report.getADEs().forEach((name, value) -> extensions.add(new JSONObject()
                .fluentPut("name", name)
                .fluentPut("description", value.first())
                .fluentPut("version", value.second())));

        return extensions;
    }

    private JSONArray buildCodeLists(DatabaseReport report) {
        JSONArray codeLists = new JSONArray();
        report.getCodeLists().forEach((idenifier, type) -> codeLists.add(new JSONObject()
                .fluentPut("identifier", idenifier)
                .fluentPut("type", type)));

        return codeLists;
    }

    private JSONObject buildCrs(DatabaseAdapter adapter) {
        return new JSONObject()
                .fluentPut("srid", adapter.getDatabaseMetadata().getSpatialReference().getSRID())
                .fluentPut("identifier", adapter.getDatabaseMetadata().getSpatialReference().getIdentifier())
                .fluentPut("name", adapter.getDatabaseMetadata().getSpatialReference().getName());
    }

    private JSONObject buildDatabaseConnection(DatabaseAdapter adapter) {
        return new JSONObject()
                .fluentPut("host", adapter.getConnectionDetails().getHost())
                .fluentPut("port", adapter.getConnectionDetails().getPort())
                .fluentPut("database", adapter.getConnectionDetails().getDatabase())
                .fluentPut("schema", adapter.getConnectionDetails().getSchema())
                .fluentPut("user", adapter.getConnectionDetails().getUser());
    }

    private JSONObject buildDatabaseSize(DatabaseSize databaseSize) {
        Map<String, Long> byTable = databaseSize.getTableSizes().entrySet().stream()
                .filter(e -> Table.CONTENT_TABLES.contains(e.getKey()))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        TreeMap::new));

        return new JSONObject().fluentPut("databaseSize", databaseSize.getDatabaseSize())
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
