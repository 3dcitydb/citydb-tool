/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.report;

import org.citydb.core.json.JsonMapping;
import org.citydb.core.time.TimeHelper;
import org.citydb.database.DatabaseConstants;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.database.schema.Table;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.report.options.FeatureScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

    public ObjectNode build(DatabaseReport report) {
        ReportOptions options = report.getOptions();
        DatabaseAdapter adapter = report.getAdapter();

        ObjectNode jsonReport = objectNode();
        jsonReport.set("metadata", buildMetadata(options));
        jsonReport.set("summary", buildSummary(report, options, adapter));
        jsonReport.set("database", buildDatabase(report, options, adapter));

        if (!options.isCompact()) {
            jsonReport.set("features", buildFeatures(report, adapter));
            jsonReport.set("geometries", buildGeometries(report));
            jsonReport.set("addresses", buildAddresses(report));
            jsonReport.set("appearances", buildAppearances(report));
            jsonReport.set("extensions", buildExtensions(report));
            jsonReport.set("codeLists", buildCodeLists(report));
            jsonReport.set("modules", toTree(report.getModules()));
        }

        if (options.isIncludeGenericAttributes()) {
            jsonReport.set("genericAttributes", buildGenericAttributes(report));
        }

        return jsonReport;
    }

    private ObjectNode buildMetadata(ReportOptions options) {
        String timestamp = TimeHelper.toDateTime(LocalDateTime.now().withNano(0))
                .format(TimeHelper.DATE_TIME_FORMATTER);

        return objectNode()
                .put("generatedAt", timestamp)
                .put("featureScope", options.getFeatureScope().toValue())
                .put("genericAttributesProcessed", options.isIncludeGenericAttributes());
    }

    private ObjectNode buildSummary(DatabaseReport report, ReportOptions options, DatabaseAdapter adapter) {
        Set<String> candidates = options.getFeatureScope() == FeatureScope.ACTIVE
                ? report.getActiveFeatures().keySet()
                : report.getFeatures();
        List<String> topLevelFeatures = candidates.stream()
                .filter(name -> adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(PrefixedName.of(name))
                        .isTopLevel())
                .toList();

        ObjectNode summary = objectNode();
        summary.set("topLevelFeatures", toTree(topLevelFeatures));
        summary.set("lods", toTree(report.getLods().keySet()));
        summary.set("themes", toTree(report.getAppearances().keySet()));
        summary.set("crs", buildCrs(adapter));
        summary.set("extent", toTree(convertExtent(report.getExtent(), 4)));
        summary.set("wgs84Extent", toTree(convertExtent(report.getWgs84Extent(), 6)));
        return summary;
    }

    private ObjectNode buildDatabase(DatabaseReport report, ReportOptions options, DatabaseAdapter adapter) {
        ObjectNode database = objectNode()
                .put("name", DatabaseConstants.CITYDB_NAME)
                .put("version", adapter.getDatabaseMetadata().getVersion().toString());
        database.set("dbms", buildDbms(adapter));
        database.put("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled());
        database.set("connection", buildDatabaseConnection(adapter));

        DatabaseSize databaseSize = report.getDatabaseSize().orElse(null);
        if (options.isIncludeDatabaseSize() && databaseSize != null) {
            database.set("size", buildDatabaseSize(databaseSize));
        }

        return database;
    }

    private ObjectNode buildDbms(DatabaseAdapter adapter) {
        ObjectNode dbms = objectNode()
                .put("name", adapter.getDatabaseMetadata().getVendorProductName())
                .put("version", adapter.getDatabaseMetadata().getVendorProductVersion());

        if (adapter.getDatabaseMetadata().hasProperties()) {
            dbms.set("properties", buildDbmsProperties(adapter));
        }

        return dbms;
    }

    private ObjectNode buildDbmsProperties(DatabaseAdapter adapter) {
        ObjectNode properties = objectNode();
        adapter.getDatabaseMetadata().getProperties().forEach((id, property) -> properties.putObject(id)
                .put("name", property.getName())
                .put("value", property.getValue().orElse("n/a")));

        return properties;
    }

    private ObjectNode buildFeatures(DatabaseReport report, DatabaseAdapter adapter) {
        ObjectNode features = objectNode();
        features.set("featureCount", buildFeatureCount(report));
        features.set("topLevelFeatureCount", buildTopLevelFeatureCount(report, adapter));
        features.set("byType", buildFeaturesByType(report));
        return features;
    }

    private ObjectNode buildFeatureCount(DatabaseReport report) {
        return putFeatureCount(objectNode(),
                sum(report.getActiveFeatures().values()),
                sum(report.getTerminatedFeatures().values()));
    }

    private ObjectNode buildTopLevelFeatureCount(DatabaseReport report, DatabaseAdapter adapter) {
        long active = report.getActiveFeatures().entrySet().stream()
                .filter(e -> adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(PrefixedName.of(e.getKey()))
                        .isTopLevel())
                .mapToLong(Map.Entry::getValue).sum();
        long terminated = report.getTerminatedFeatures().entrySet().stream()
                .filter(e -> adapter.getSchemaAdapter().getSchemaMapping().getFeatureType(PrefixedName.of(e.getKey()))
                        .isTopLevel())
                .mapToLong(Map.Entry::getValue).sum();

        return putFeatureCount(objectNode(), active, terminated);
    }

    private ObjectNode buildFeaturesByType(DatabaseReport report) {
        ObjectNode featuresByType = objectNode();
        report.getFeatures().forEach(featureType -> putFeatureCount(featuresByType.putObject(featureType),
                report.getActiveFeatures().getOrDefault(featureType, 0L),
                report.getTerminatedFeatures().getOrDefault(featureType, 0L)));

        return featuresByType;
    }

    private ObjectNode buildGeometries(DatabaseReport report) {
        long geometryCount = sum(report.getGeometries().values());

        ObjectNode geometries = objectNode()
                .put("geometryCount", geometryCount)
                .put("implicitGeometryCount", report.getImplicitGeometryCount());
        geometries.set("byType", toTree(report.getGeometries()));
        geometries.set("byLod", toTree(report.getLods()));
        return geometries;
    }

    private ObjectNode buildAddresses(DatabaseReport report) {
        return objectNode().put("addressCount", report.getAddressCount());
    }

    private ObjectNode buildAppearances(DatabaseReport report) {
        long appearanceCount = sum(report.getAppearances().values());

        ObjectNode appearances = objectNode()
                .put("appearanceCount", appearanceCount)
                .put("hasGlobalAppearances", report.hasGlobalAppearances())
                .put("hasMaterials", report.hasMaterials())
                .put("hasTextures", report.hasTextures())
                .put("hasGeoreferencedTextures", report.hasGeoreferencedTextures());
        appearances.set("byTheme", toTree(report.getAppearances()));
        return appearances;
    }

    private ObjectNode buildGenericAttributes(DatabaseReport report) {
        ObjectNode genericAttributes = objectNode();
        report.getGenericAttributes().forEach((featureType, attributes) -> {
            ObjectNode object = genericAttributes.putObject(featureType);
            attributes.forEach((name, types) -> object.set(name, toTree(types)));
        });

        return genericAttributes;
    }

    private ArrayNode buildExtensions(DatabaseReport report) {
        ArrayNode extensions = JsonMapping.mapper().createArrayNode();
        report.getADEs().forEach((name, value) -> extensions.addObject()
                .put("name", name)
                .put("description", value.first())
                .put("version", value.second()));

        return extensions;
    }

    private ObjectNode buildCodeLists(DatabaseReport report) {
        ObjectNode codeLists = objectNode();
        report.getCodeLists().forEach((type, identifiers) -> codeLists.set(type, toTree(identifiers)));

        return codeLists;
    }

    private ObjectNode buildCrs(DatabaseAdapter adapter) {
        return objectNode()
                .put("srid", adapter.getDatabaseMetadata().getSpatialReference().getSRID())
                .put("identifier", adapter.getDatabaseMetadata().getSpatialReference().getIdentifier())
                .put("name", adapter.getDatabaseMetadata().getSpatialReference().getName());
    }

    private ObjectNode buildDatabaseConnection(DatabaseAdapter adapter) {
        ObjectNode connection = objectNode()
                .put("databaseType", adapter.getConnectionDetails().getDatabaseType())
                .put("host", adapter.getConnectionDetails().getHost())
                .put("port", adapter.getConnectionDetails().getPort())
                .put("database", adapter.getConnectionDetails().getDatabase())
                .put("schema", adapter.getConnectionDetails().getSchema())
                .put("user", adapter.getConnectionDetails().getUser());

        if (adapter.getConnectionDetails().hasProperties()) {
            connection.set("properties", toTree(adapter.getConnectionDetails().getProperties()));
        }

        return connection;
    }

    private ObjectNode buildDatabaseSize(DatabaseSize databaseSize) {
        Map<String, Long> byTable = databaseSize.getTableSizes().entrySet().stream()
                .filter(e -> Table.CONTENT_TABLES.contains(e.getKey()))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        TreeMap::new));

        ObjectNode size = objectNode()
                .put("databaseSize", databaseSize.getDatabaseSize())
                .put("schemaSize", databaseSize.getSchemaSize());
        size.set("byTable", toTree(byTable));
        return size;
    }

    private List<Double> convertExtent(Envelope extent, int decimalPlaces) {
        if (extent != null && !extent.isEmpty()) {
            return extent.getVertexDimension() == 2
                    ? List.of(round(extent.getLowerCorner().getX(), decimalPlaces),
                    round(extent.getLowerCorner().getY(), decimalPlaces),
                    round(extent.getUpperCorner().getX(), decimalPlaces),
                    round(extent.getUpperCorner().getY(), decimalPlaces))
                    : List.of(round(extent.getLowerCorner().getX(), decimalPlaces),
                    round(extent.getLowerCorner().getY(), decimalPlaces),
                    round(extent.getLowerCorner().getZ(), decimalPlaces),
                    round(extent.getUpperCorner().getX(), decimalPlaces),
                    round(extent.getUpperCorner().getY(), decimalPlaces),
                    round(extent.getUpperCorner().getZ(), decimalPlaces));
        } else {
            return Collections.emptyList();
        }
    }

    private ObjectNode putFeatureCount(ObjectNode parent, long active, long terminated) {
        return parent.put("active", active)
                .put("terminated", terminated);
    }

    private ObjectNode objectNode() {
        return JsonMapping.mapper().createObjectNode();
    }

    private JsonNode toTree(Object value) {
        return JsonMapping.mapper().valueToTree(value);
    }

    private long sum(Collection<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }

    private double round(double value, int decimalPlaces) {
        return new BigDecimal(value).setScale(decimalPlaces, RoundingMode.HALF_UP).doubleValue();
    }
}
