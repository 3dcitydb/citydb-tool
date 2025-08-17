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

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;

import java.util.*;

public class SchemaMapping {
    public static final String TARGET_OBJECTCLASS_ID = "@target.objectclass_id@";

    private final Map<Integer, Namespace> namespacesById = new HashMap<>();
    private final Map<String, Namespace> namespacesByURI = new HashMap<>();
    private final Map<String, Namespace> namespacesByAlias = new HashMap<>();
    private final Map<Integer, DataType> dataTypesById = new HashMap<>();
    private final Map<Name, DataType> dataTypesByName = new HashMap<>();
    private final Map<String, DataType> dataTypesByIdentifier = new HashMap<>();
    private final Map<Integer, FeatureType> featureTypesById = new HashMap<>();
    private final Map<Name, FeatureType> featureTypesByName = new HashMap<>();
    private final Map<String, FeatureType> featureTypesByIdentifier = new HashMap<>();

    SchemaMapping() {
    }

    public Collection<Namespace> getNamespaces() {
        return namespacesById.values();
    }

    public Namespace getNamespace(int id) {
        return namespacesById.getOrDefault(id, Namespace.UNDEFINED);
    }

    public Namespace getNamespace(Name name) {
        return getNamespaceByURI(name.getNamespace());
    }

    public Namespace getNamespaceByURI(String namespace) {
        return namespacesByURI.getOrDefault(Namespaces.ensureNonNull(namespace), Namespace.UNDEFINED);
    }

    public Namespace getNamespaceByAlias(String alias) {
        return namespacesByAlias.getOrDefault(alias, Namespace.UNDEFINED);
    }

    public Name resolvePrefixedName(PrefixedName name) {
        return name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE) ?
                Name.of(name.getLocalName(), getNamespaceByAlias(name.getPrefix().orElse(null)).getURI()) :
                Name.of(name.getLocalName(), name.getNamespace());
    }

    void addNamespace(Namespace namespace) {
        namespacesById.put(namespace.getId(), namespace);
        namespacesByURI.put(namespace.getURI(), namespace);
        namespace.getAlias().ifPresent(alias -> namespacesByAlias.put(alias, namespace));
    }

    public Collection<DataType> getDataTypes() {
        return dataTypesById.values();
    }

    public DataType getDataType(int id) {
        return dataTypesById.getOrDefault(id, DataType.UNDEFINED);
    }

    public DataType getDataType(Name name) {
        return dataTypesByName.getOrDefault(name, DataType.UNDEFINED);
    }

    public DataType getDataType(String localName, String namespace) {
        return getDataType(Name.of(localName, namespace));
    }

    DataType getDataTypeByIdentifier(String identifier) {
        return dataTypesByIdentifier.getOrDefault(identifier, DataType.UNDEFINED);
    }

    void addDataType(DataType dataType) {
        dataTypesById.put(dataType.getId(), dataType);
        dataTypesByName.put(dataType.getName(), dataType);
        dataTypesByIdentifier.put(dataType.getIdentifier(), dataType);
    }

    public Collection<FeatureType> getFeatureTypes() {
        return featureTypesById.values();
    }

    public FeatureType getFeatureType(int id) {
        return featureTypesById.getOrDefault(id, FeatureType.UNDEFINED);
    }

    public FeatureType getFeatureType(Name name) {
        return featureTypesByName.getOrDefault(name, FeatureType.UNDEFINED);
    }

    public FeatureType getFeatureType(String localName, String namespace) {
        return getFeatureType(Name.of(localName, namespace));
    }

    FeatureType getFeatureTypeByIdentifier(String identifier) {
        return featureTypesByIdentifier.getOrDefault(identifier, FeatureType.UNDEFINED);
    }

    public FeatureType getFeatureType(PrefixedName name) {
        return name != null ?
                getFeatureType(resolvePrefixedName(name)) :
                FeatureType.UNDEFINED;
    }

    void addFeatureType(FeatureType featureType) {
        featureTypesById.put(featureType.getId(), featureType);
        featureTypesByName.put(featureType.getName(), featureType);
        featureTypesByIdentifier.put(featureType.getIdentifier(), featureType);
    }

    public FeatureType getSuperType(Collection<FeatureType> featureTypes) {
        if (featureTypes != null && !featureTypes.isEmpty()) {
            Iterator<FeatureType> iterator = featureTypes.iterator();
            List<FeatureType> superTypes = new ArrayList<>(iterator.next().getTypeHierarchy());
            while (iterator.hasNext()) {
                superTypes.retainAll(iterator.next().getTypeHierarchy());
            }

            return superTypes.get(0);
        } else {
            return FeatureType.UNDEFINED;
        }
    }

    public Set<Integer> getObjectClassIds(FeatureType featureType) {
        return getObjectClassIds(List.of(featureType));
    }

    public Set<Integer> getObjectClassIds(Collection<FeatureType> featureTypes) {
        Set<Integer> ids = new HashSet<>();
        for (FeatureType featureType : featureTypes) {
            if (featureType.isAbstract()) {
                featureTypesById.values().stream()
                        .filter(candidate -> !candidate.isAbstract() && candidate.isSubTypeOf(featureType))
                        .forEach(subType -> ids.add(subType.getId()));
            } else {
                ids.add(featureType.getId());
            }
        }

        return ids;
    }

    SchemaMapping build() throws SchemaException {
        for (DataType dataType : dataTypesById.values()) {
            dataType.postprocess(this);
        }

        for (FeatureType featureType : featureTypesById.values()) {
            featureType.postprocess(this);
        }

        dataTypesByIdentifier.clear();
        featureTypesByIdentifier.clear();
        return this;
    }
}
