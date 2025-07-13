package org.citydb.util.report;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.core.time.TimeHelper;
import org.citydb.core.tuple.Pair;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.database.schema.DataType;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.util.StatisticsHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.GeometryType;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseReport {
    private final ReportOptions options;
    private final DatabaseAdapter adapter;
    private final SchemaMapping schemaMapping;
    private final Map<String, Long> features = new TreeMap<>();
    private final Map<String, Long> terminatedFeatures = new TreeMap<>();
    private final Map<String, Long> geometries = new TreeMap<>();
    private final Map<String, Long> lods = new TreeMap<>();
    private final Map<String, Long> appearances = new TreeMap<>();
    private final Map<String, Set<String>> genericAttributes = new TreeMap<>();
    private final Map<String, Pair<String, String>> ades = new TreeMap<>();
    private final Map<String, String> codeLists = new TreeMap<>();
    private final Map<String, Envelope> extents = new TreeMap<>();
    private final Map<String, String> modules = new TreeMap<>();

    private long addressCount;
    private long implicitGeometryCount;
    private boolean hasMaterials;
    private boolean hasTextures;
    private boolean hasGeoreferencedTextures;
    private boolean hasGlobalAppearances;
    private DatabaseSize databaseSize;

    DatabaseReport(ReportOptions options, DatabaseAdapter adapter) {
        this.options = options;
        this.adapter = adapter;
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
    }

    public static DatabaseReport build(ReportOptions options, DatabaseAdapter adapter) throws DatabaseReportException {
        return new DatabaseReportBuilder().build(options, adapter);
    }

    public boolean hasFeatures() {
        return !features.isEmpty();
    }

    public Map<String, Long> getFeatures() {
        return features;
    }

    void addFeatures(Map<FeatureType, StatisticsHelper.FeatureInfo> features) {
        if (features != null) {
            features.forEach((type, info) -> {
                String name = getQName(type.getName());
                this.features.merge(name, info.count(), Long::sum);
                extents.merge(name, info.extent(), Envelope::include);
            });
        }
    }

    public boolean hasTerminatedFeatures() {
        return !terminatedFeatures.isEmpty();
    }

    public Map<String, Long> getTerminatedFeatures() {
        return terminatedFeatures;
    }

    void addTerminatedFeatures(Map<FeatureType, Long> features) {
        if (features != null) {
            features.forEach((type, count) -> terminatedFeatures.merge(getQName(type.getName()), count, Long::sum));
        }
    }

    public boolean hasGeometries() {
        return !geometries.isEmpty();
    }

    public Map<String, Long> getGeometries() {
        return geometries;
    }

    void addGeometries(Map<GeometryType, Long> geometries) {
        if (geometries != null) {
            geometries.forEach((type, count) ->
                    this.geometries.merge(getQName(Name.of(type.getTypeName(), Namespaces.CORE)), count, Long::sum));
        }
    }

    public boolean hasAddresses() {
        return addressCount > 0;
    }

    void setAddressCount(long addressCount) {
        this.addressCount = addressCount > 0 ? addressCount : 0;
    }

    public boolean hasImplicitGeometries() {
        return implicitGeometryCount > 0;
    }

    void setImplicitGeometryCount(long implicitGeometryCount) {
        this.implicitGeometryCount = implicitGeometryCount > 0 ? implicitGeometryCount : 0;
    }

    public boolean hasLods() {
        return !lods.isEmpty();
    }

    public Map<String, Long> getLods() {
        return lods;
    }

    void addLods(Map<String, Long> lods) {
        if (lods != null) {
            lods.forEach((lod, count) -> this.lods.merge(lod, count, Long::sum));
        }
    }

    public boolean hasAppearances() {
        return !appearances.isEmpty();
    }

    public Map<String, Long> getAppearances() {
        return appearances;
    }

    void addAppearances(Map<String, Long> appearances) {
        if (appearances != null) {
            appearances.forEach((theme, count) -> this.appearances.merge(theme, count, Long::sum));
        }
    }

    public boolean hasMaterials() {
        return hasMaterials;
    }

    public boolean hasTextures() {
        return hasTextures;
    }

    public boolean hasGeoreferencedTextures() {
        return hasGeoreferencedTextures;
    }

    void setSurfaceData(StatisticsHelper.SurfaceDataInfo info) {
        hasMaterials = info.hasMaterials();
        hasTextures = info.hasTextures();
        hasGeoreferencedTextures = info.hasGeoreferencedTextures();
    }

    public boolean hasGlobalAppearances() {
        return hasGlobalAppearances;
    }

    void setGlobalAppearances(boolean hasGlobalAppearances) {
        this.hasGlobalAppearances = hasGlobalAppearances;
    }

    public boolean hasThemes() {
        return !appearances.isEmpty();
    }

    public boolean hasNullTheme() {
        return appearances.containsKey(StatisticsHelper.NULL_THEME);
    }

    public Set<String> getThemes() {
        return appearances.keySet();
    }

    public boolean hasGenericAttributes() {
        return !genericAttributes.isEmpty();
    }

    public Map<String, Set<String>> getGenericAttributes() {
        return genericAttributes;
    }

    void addGenericAttributes(Map<String, Set<DataType>> genericAttributes) {
        if (genericAttributes != null) {
            genericAttributes.forEach((name, types) -> this.genericAttributes
                    .computeIfAbsent(name, k -> new TreeSet<>())
                    .addAll(types.stream()
                            .map(type -> getQName(type.getName()))
                            .toList()));
        }
    }

    public boolean hasADEs() {
        return !ades.isEmpty();
    }

    public Map<String, Pair<String, String>> getADEs() {
        return ades;
    }

    void addADEs(Map<String, Pair<String, String>> ades) {
        if (ades != null) {
            this.ades.putAll(ades);
        }
    }

    public boolean hasCodeLists() {
        return !codeLists.isEmpty();
    }

    public Map<String, String> getCodeLists() {
        return codeLists;
    }

    void addCodeLists(Map<String, String> codeLists) {
        if (codeLists != null) {
            this.codeLists.putAll(codeLists);
        }
    }

    public Optional<DatabaseSize> getDatabaseSize() {
        return Optional.ofNullable(databaseSize);
    }

    void setDatabaseSize(DatabaseSize databaseSize) {
        this.databaseSize = databaseSize;
    }

    private String getQName(Name name) {
        String alias = schemaMapping.getNamespaceByURI(name.getNamespace()).getAlias().orElse(null);
        modules.put(alias, name.getNamespace());
        return alias != null ?
                alias + ":" + name.getLocalName() :
                name.getLocalName();
    }

    public JSONObject toJSON() {
        JSONObject report = new JSONObject();

        report.fluentPut("metadata", buildMetadata())
                .fluentPut("summary", buildSummary())
                .fluentPut("database", buildDatabase());

        if (!features.isEmpty()) {
            report.put("features", buildFeatures());
        }

        if (!geometries.isEmpty()) {
            report.put("geometries", buildGeometries());
        }

        if (!appearances.isEmpty()) {
            report.put("appearances", buildAppearances());
        }

        if (options.isIncludeGenericAttributes()) {
            report.put("genericAttributes", genericAttributes);
        }

        if (!ades.isEmpty()) {
            report.put("extensions", buildExtensions());
        }

        if (!codeLists.isEmpty()) {
            report.put("codeLists", buildCodeLists());
        }

        if (!modules.isEmpty()) {
            report.put("modules", modules);
        }

        return report;
    }

    private JSONObject buildMetadata() {
        String timestamp = TimeHelper.toDateTime(LocalDateTime.now().withNano(0))
                .format(TimeHelper.DATE_TIME_FORMATTER);
        String featureScope = options.isOnlyPropertiesOfValidFeatures() ? "valid" : "all";

        return new JSONObject()
                .fluentPut("reportGenerated", timestamp)
                .fluentPut("featureScope", featureScope);
    }

    private JSONObject buildSummary() {
        JSONObject summary = new JSONObject();
        List<String> topLevelFeatures = features.keySet().stream()
                .filter(name -> schemaMapping.getFeatureType(PrefixedName.of(name)).isTopLevel())
                .toList();
        List<Double> extent = extents.values().stream()
                .reduce(Envelope::include)
                .map(envelope -> List.of(
                        envelope.getLowerCorner().getX(),
                        envelope.getLowerCorner().getY(),
                        envelope.getLowerCorner().getZ(),
                        envelope.getUpperCorner().getX(),
                        envelope.getUpperCorner().getY(),
                        envelope.getUpperCorner().getZ()))
                .orElse(null);

        summary.fluentPut("topLevelFeatures", topLevelFeatures)
                .fluentPut("lods", new ArrayList<>(lods.keySet()))
                .fluentPut("themes", new ArrayList<>(appearances.keySet()))
                .fluentPut("crs", buildCrs())
                .fluentPut("extent", extent)
                .fluentPut("hasFeatures", !features.isEmpty())
                .fluentPut("hasTerminatedFeatures", !terminatedFeatures.isEmpty())
                .fluentPut("hasAddresses", addressCount > 0)
                .fluentPut("hasGeometries", !geometries.isEmpty())
                .fluentPut("hasImplicitGeometries", implicitGeometryCount > 0)
                .fluentPut("hasAppearances", !appearances.isEmpty())
                .fluentPut("hasGlobalAppearances", hasGlobalAppearances)
                .fluentPut("hasMaterials", hasMaterials)
                .fluentPut("hasTextures", hasTextures)
                .fluentPut("hasGeoreferencedTextures", hasGeoreferencedTextures);

        if (options.isIncludeGenericAttributes()) {
            summary.put("hasGenericAttributes", !genericAttributes.isEmpty());
        }

        return summary.fluentPut("hasExtensions", !ades.isEmpty())
                .fluentPut("hasCodeLists", !codeLists.isEmpty());
    }

    private JSONObject buildDatabase() {
        JSONObject database = new JSONObject();
        String product = adapter.getDatabaseMetadata().getVendorProductName() + " " +
                adapter.getDatabaseMetadata().getVendorProductVersion();

        database.fluentPut("version", adapter.getDatabaseMetadata().getVersion().toString())
                .fluentPut("product", product)
                .fluentPut("hasChangelogEnabled", adapter.getDatabaseMetadata().isChangelogEnabled())
                .fluentPut("connection", buildDatabaseConnection());

        if (options.isIncludeDatabaseSize() && databaseSize != null) {
            database.put("size", buildDatabaseSize());
        }

        return database;
    }

    private JSONObject buildFeatures() {
        JSONObject features = new JSONObject();
        long featureCount = sum(this.features.values());
        long topLevelFeatureCount = this.features.entrySet().stream()
                .filter(e -> schemaMapping.getFeatureType(PrefixedName.of(e.getKey())).isTopLevel())
                .mapToLong(Map.Entry::getValue).sum();
        long terminatedFeatureCount = sum(terminatedFeatures.values());

        return features.fluentPut("featureCount", featureCount)
                .fluentPut("topLevelFeatureCount", topLevelFeatureCount)
                .fluentPut("terminatedFeatureCount", terminatedFeatureCount)
                .fluentPut("addressCount", addressCount)
                .fluentPut("byType", this.features)
                .fluentPut("byLod", lods);
    }

    private JSONObject buildGeometries() {
        JSONObject geometries = new JSONObject();
        long geometryCount = sum(this.geometries.values());

        return geometries.fluentPut("geometryCount", geometryCount)
                .fluentPut("implicitGeometryCount", implicitGeometryCount)
                .fluentPut("byType", this.geometries);
    }

    private JSONObject buildAppearances() {
        JSONObject appearances = new JSONObject();
        long appearanceCount = sum(this.appearances.values());

        return appearances.fluentPut("appearanceCount", appearanceCount)
                .fluentPut("hasGlobalAppearances", hasGlobalAppearances)
                .fluentPut("hasMaterials", hasMaterials)
                .fluentPut("hasTextures", hasTextures)
                .fluentPut("hasGeoreferencedTextures", hasGeoreferencedTextures)
                .fluentPut("byTheme", this.appearances);
    }

    private JSONArray buildExtensions() {
        JSONArray extensions = new JSONArray();
        ades.forEach((k, v) -> extensions.add(new JSONObject()
                .fluentPut("name", k)
                .fluentPut("description", v.first())
                .fluentPut("extent", v.second())));

        return extensions;
    }

    private JSONArray buildCodeLists() {
        JSONArray codeLists = new JSONArray();
        this.codeLists.forEach((k, v) -> codeLists.add(new JSONObject()
                .fluentPut("identifier", k)
                .fluentPut("type", v)));

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

    private JSONObject buildDatabaseSize() {
        JSONObject size = new JSONObject();
        Map<String, Long> byTable = databaseSize.getTableSizes().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue,
                        (k1, k2) -> k1,
                        TreeMap::new));

        return size.fluentPut("databaseSize", databaseSize.getDatabaseSize())
                .fluentPut("schemaSize", databaseSize.getSchemaSize())
                .fluentPut("byTable", byTable);
    }

    private long sum(Collection<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }
}
