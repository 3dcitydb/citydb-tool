/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.HashMap;
import java.util.Map;

public class SchemaMapping {
    private final Map<Integer, Namespace> namespacesById = new HashMap<>();
    private final Map<String, Namespace> namespacesByURI = new HashMap<>();
    private final Map<String, Namespace> namespacesByAlias = new HashMap<>();
    private final Map<Integer, DataType> dataTypesById = new HashMap<>();
    private final Map<Name, DataType> dataTypesByName = new HashMap<>();
    private final Map<String, DataType> dataTypesByIdentifier = new HashMap<>();
    private final Map<Integer, FeatureType> featureTypesById = new HashMap<>();
    private final Map<Name, FeatureType> featureTypesByName = new HashMap<>();

    SchemaMapping() {
    }

    public Namespace getNamespace(int id) {
        return namespacesById.getOrDefault(id, Namespace.UNDEFINED);
    }

    public Namespace getNamespaceByURI(String namespace) {
        return namespacesByURI.getOrDefault(Namespaces.ensureNonNull(namespace), Namespace.UNDEFINED);
    }

    public Namespace getNamespaceByAlias(String alias) {
        return namespacesByAlias.getOrDefault(alias, Namespace.UNDEFINED);
    }

    void addNamespace(Namespace namespace) {
        namespacesById.put(namespace.getId(), namespace);
        namespacesByURI.put(namespace.getURI(), namespace);
        namespace.getAlias().ifPresent(alias -> namespacesByAlias.put(alias, namespace));
    }

    public DataType getDataType(int id) {
        return dataTypesById.getOrDefault(id, DataType.UNDEFINED);
    }

    public DataType getDataType(Name name) {
        return dataTypesByName.getOrDefault(name, DataType.UNDEFINED);
    }

    DataType getDataTypeByIdentifier(String identifier) {
        return dataTypesByIdentifier.getOrDefault(identifier, DataType.UNDEFINED);
    }

    void addDataType(DataType dataType) {
        dataTypesById.put(dataType.getId(), dataType);
        dataTypesByName.put(dataType.getName(), dataType);
        dataTypesByIdentifier.put(dataType.getIdentifier(), dataType);
    }

    public FeatureType getFeatureType(int id) {
        return featureTypesById.getOrDefault(id, FeatureType.UNDEFINED);
    }

    public FeatureType getFeatureType(Name name) {
        return featureTypesByName.getOrDefault(name, FeatureType.UNDEFINED);
    }

    void addFeatureType(FeatureType featureType) {
        featureTypesById.put(featureType.getId(), featureType);
        featureTypesByName.put(featureType.getName(), featureType);
    }

    SchemaMapping build() throws SchemaException {
        for (DataType dataType : dataTypesById.values()) {
            dataType.postprocess(this);
        }

        for (FeatureType featureType : featureTypesById.values()) {
            featureType.postprocess(this);
        }

        dataTypesByIdentifier.clear();
        return this;
    }
}
