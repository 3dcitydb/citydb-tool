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

import com.alibaba.fastjson2.JSONObject;
import org.citydb.core.tuple.Pair;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.metadata.DatabaseSize;
import org.citydb.database.schema.DataType;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.util.StatisticsHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.GeometryType;
import org.citydb.util.report.options.FeatureScope;

import java.util.*;
import java.util.function.Consumer;

public class DatabaseReport {
    private final ReportOptions options;
    private final DatabaseAdapter adapter;
    private final SchemaMapping schemaMapping;
    private final Map<String, Long> activeFeatures = new TreeMap<>();
    private final Map<String, Long> terminatedFeatures = new TreeMap<>();
    private final Map<String, Long> geometries = new TreeMap<>();
    private final Map<String, Long> lods = new TreeMap<>();
    private final Map<String, Long> appearances = new TreeMap<>();
    private final Map<String, Map<String, Set<String>>> genericAttributes = new TreeMap<>();
    private final Map<String, Pair<String, String>> ades = new TreeMap<>();
    private final Map<String, Set<String>> codeLists = new TreeMap<>();
    private final Map<String, String> modules = new TreeMap<>();
    private final Envelope extent;

    private long addressCount;
    private long implicitGeometryCount;
    private boolean hasMaterials;
    private boolean hasTextures;
    private boolean hasGeoreferencedTextures;
    private boolean hasGlobalAppearances;
    private DatabaseSize databaseSize;
    private Envelope wgs84Extent;

    DatabaseReport(ReportOptions options, DatabaseAdapter adapter) {
        this.options = options;
        this.adapter = adapter;
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
        extent = Envelope.empty().setSRID(adapter.getDatabaseMetadata().getSpatialReference().getSRID());
    }

    public static DatabaseReport build(ReportOptions options, DatabaseAdapter adapter) throws DatabaseReportException {
        return new DatabaseReportBuilder().build(options, adapter);
    }

    ReportOptions getOptions() {
        return options;
    }

    DatabaseAdapter getAdapter() {
        return adapter;
    }

    public boolean hasFeatures() {
        return hasActiveFeatures() || hasTerminatedFeatures();
    }

    public Set<String> getFeatures() {
        Set<String> features = new TreeSet<>(activeFeatures.keySet());
        features.addAll(terminatedFeatures.keySet());
        return features;
    }

    public boolean hasActiveFeatures() {
        return !activeFeatures.isEmpty();
    }

    public Map<String, Long> getActiveFeatures() {
        return activeFeatures;
    }

    void setActiveFeatures(Map<FeatureType, StatisticsHelper.FeatureInfo> features) {
        if (features != null) {
            features.forEach((type, info) -> {
                activeFeatures.merge(getQName(type.getName()), info.count(), Long::sum);
                extent.include(info.extent());
            });
        }
    }

    public boolean hasTerminatedFeatures() {
        return !terminatedFeatures.isEmpty();
    }

    public Map<String, Long> getTerminatedFeatures() {
        return terminatedFeatures;
    }

    void setTerminatedFeatures(Map<FeatureType, StatisticsHelper.FeatureInfo> features) {
        if (features != null) {
            features.forEach((type, info) -> {
                boolean allFeatures = options.getFeatureScope() == FeatureScope.ALL;
                terminatedFeatures.merge(getQName(type.getName(), allFeatures), info.count(), Long::sum);
                if (allFeatures) {
                    extent.include(info.extent());
                }
            });
        }
    }

    public boolean hasGeometries() {
        return !geometries.isEmpty();
    }

    public Map<String, Long> getGeometries() {
        return geometries;
    }

    void setGeometries(Map<GeometryType, Long> geometries) {
        if (geometries != null) {
            geometries.forEach((type, count) ->
                    this.geometries.merge(getQName(Name.of(type.getTypeName(), Namespaces.CORE)), count, Long::sum));
        }
    }

    public boolean hasAddresses() {
        return addressCount > 0;
    }

    public long getAddressCount() {
        return addressCount;
    }

    void setAddressCount(long addressCount) {
        this.addressCount = addressCount > 0 ? addressCount : 0;
    }

    public boolean hasImplicitGeometries() {
        return implicitGeometryCount > 0;
    }

    public long getImplicitGeometryCount() {
        return implicitGeometryCount;
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

    void setLods(Map<String, Long> lods) {
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

    void setAppearances(Map<String, Long> appearances) {
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

    public Map<String, Map<String, Set<String>>> getGenericAttributes() {
        return genericAttributes;
    }

    void setGenericAttributes(Map<FeatureType, Map<String, Set<DataType>>> genericAttributes) {
        if (genericAttributes != null) {
            genericAttributes.forEach((featureType, attributes) -> {
                String key = getQName(featureType.getName());
                attributes.forEach((name, types) -> this.genericAttributes.computeIfAbsent(key, k -> new TreeMap<>())
                        .computeIfAbsent(name, k -> new TreeSet<>())
                        .addAll(types.stream()
                                .map(type -> getQName(type.getName()))
                                .toList()));
            });
        }
    }

    public boolean hasADEs() {
        return !ades.isEmpty();
    }

    public Map<String, Pair<String, String>> getADEs() {
        return ades;
    }

    void setADEs(Map<String, Pair<String, String>> ades) {
        if (ades != null) {
            this.ades.putAll(ades);
        }
    }

    public boolean hasCodeLists() {
        return !codeLists.isEmpty();
    }

    public Map<String, Set<String>> getCodeLists() {
        return codeLists;
    }

    void setCodeLists(Map<String, Set<String>> codeLists) {
        if (codeLists != null) {
            codeLists.forEach((type, identifiers) -> {
                this.codeLists.computeIfAbsent(type, k -> new TreeSet<>()).addAll(identifiers);
            });
        }
    }

    public Map<String, String> getModules() {
        return modules;
    }

    public Optional<DatabaseSize> getDatabaseSize() {
        return Optional.ofNullable(databaseSize);
    }

    void setDatabaseSize(DatabaseSize databaseSize) {
        this.databaseSize = databaseSize;
    }

    public Envelope getExtent() {
        return extent;
    }

    void setWgs84Extent(Envelope wgs84Extent) {
        this.wgs84Extent = wgs84Extent != null ? wgs84Extent.force2D() : null;
    }

    public Envelope getWgs84Extent() {
        return wgs84Extent != null ? wgs84Extent : Envelope.empty().force2D();
    }

    private String getQName(Name name) {
        return getQName(name, true);
    }

    private String getQName(Name name, boolean registerModule) {
        String alias = schemaMapping.getNamespaceByURI(name.getNamespace()).getAlias().orElse("");
        if (registerModule) {
            modules.put(alias, name.getNamespace());
        }

        return !alias.isEmpty() ? alias + ":" + name.getLocalName() : name.getLocalName();
    }

    public JSONObject toJSON() {
        return new ReportJsonBuilder().build(this);
    }

    public void print(Consumer<String> consumer) {
        new ReportTextBuilder().build(this, consumer);
    }
}
