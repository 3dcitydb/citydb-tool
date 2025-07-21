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

import java.util.*;
import java.util.function.Consumer;

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

            extents.values().forEach(extent::include);
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
        this.wgs84Extent = wgs84Extent != null ?
                wgs84Extent.force2D() :
                null;
    }

    public Envelope getWgs84Extent() {
        return wgs84Extent != null ?
                wgs84Extent :
                Envelope.empty().force2D();
    }

    private String getQName(Name name) {
        String alias = schemaMapping.getNamespaceByURI(name.getNamespace()).getAlias().orElse(null);
        modules.put(alias, name.getNamespace());
        return alias != null ?
                alias + ":" + name.getLocalName() :
                name.getLocalName();
    }

    public JSONObject toJSON() {
        return new ReportJsonBuilder().build(this);
    }

    public void print(Consumer<String> consumer) {
        new ReportTextBuilder().build(this, consumer);
    }
}
