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

package org.citydb.query.builder.schema;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.*;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.query.builder.common.TypeCast;
import org.citydb.query.filter.literal.PropertyRef;

import java.util.Map;
import java.util.Objects;

public class SchemaPathBuilder {
    private final SchemaMapping schemaMapping;

    private SchemaPathBuilder(SchemaMapping schemaMapping) {
        this.schemaMapping = Objects.requireNonNull(schemaMapping, "The schema mapping must not be null.");
    }

    public static SchemaPathBuilder of(SchemaMapping schemaMapping) {
        return new SchemaPathBuilder(schemaMapping);
    }

    public static SchemaPathBuilder of(DatabaseAdapter adapter) {
        return new SchemaPathBuilder(adapter.getSchemaAdapter().getSchemaMapping());
    }

    public Node build(PropertyRef propertyRef, FeatureType rootType) throws SchemaPathException {
        return build(propertyRef, rootType, false);
    }

    public Node build(PropertyRef propertyRef, FeatureType rootType, boolean resolveValue) throws SchemaPathException {
        Node root = Node.of(rootType);
        FeatureType featureType = getFeatureType(getName(propertyRef));
        if (featureType != null) {
            if (rootType.isSameOrSubTypeOf(featureType)) {
                propertyRef.getFilter().ifPresent(root::setPredicate);
                propertyRef = propertyRef.getChild().orElse(null);
            } else {
                throw new SchemaPathException("The feature type " + featureType + " does not match the requested " +
                        "feature type " + rootType + ".");
            }
        }

        return buildPath(propertyRef, root, resolveValue);
    }

    public Node build(PropertyRef propertyRef, Node parent) throws SchemaPathException {
        return build(propertyRef, parent, false);
    }

    public Node build(PropertyRef propertyRef, Node parent, boolean resolveValue) throws SchemaPathException {
        if (parent.getSchemaObject() instanceof FeatureType featureType
                && parent.getParent().isEmpty()) {
            return build(propertyRef, featureType, resolveValue);
        } else {
            parent = Node.of(parent.getSchemaObject());
            return buildPath(propertyRef, appendTarget(parent, propertyRef), resolveValue);
        }
    }

    private Node buildPath(PropertyRef propertyRef, Node parent, boolean resolveValue) throws SchemaPathException {
        if (propertyRef != null) {
            do {
                Node child = getNode(propertyRef, parent, true);

                if (child == null) {
                    throw new SchemaPathException("'" + propertyRef.getName() + "' is not a valid " +
                            "child of " + parent + ".");
                } else if (child.getSchemaObject() instanceof FeatureType featureType
                        && !isValidTarget(featureType, parent)) {
                    throw new SchemaPathException(child + " is not a valid child of " + parent + ".");
                } else if (child.getSchemaObject() instanceof DataType type
                        && parent.getSchemaObject() instanceof Typeable typeable
                        && !type.isSameOrSubTypeOf(typeable.getType().orElse(DataType.UNDEFINED))) {
                    throw new SchemaPathException(child + " is not a valid child of " + parent + ".");
                } else if (parent.getSchemaObject() instanceof GeometryObject) {
                    throw new SchemaPathException("'" + propertyRef.getName() + "' is not allowed as " +
                            "child of " + parent.getParent().orElse(null) + ".");
                } else if (propertyRef.getTypeCast().isPresent()
                        && !(child.getSchemaObject() instanceof GenericAttribute)) {
                    throw new SchemaPathException("Type casts are only allowed for generic attributes " +
                            "but not for " + child + ".");
                }

                propertyRef.getFilter().ifPresent(child::setPredicate);
                parent.child(child);
                parent = propertyRef.getChild().isPresent() ?
                        appendTarget(child, propertyRef.getChild().get()) :
                        child;
            } while ((propertyRef = propertyRef.getChild().orElse(null)) != null);
        }

        injectDataTypes(parent);
        if (resolveValue) {
            resolveValue(parent);
        }

        return parent.first();
    }

    private Node getNode(PropertyRef propertyRef, Node parent, boolean useGenericsAsFallback) throws SchemaPathException {
        Name name = getName(propertyRef);
        Node node = null;
        if (parent.getSchemaObject() instanceof FeatureType type) {
            node = getNode(name, type);
        } else if (parent.getSchemaObject() instanceof DataType type) {
            node = getNode(name, type);
        } else if (parent.getSchemaObject() instanceof Property property) {
            node = getNode(name, property);
        } else if (parent.getSchemaObject() instanceof GenericAttribute attribute) {
            node = getNode(name, attribute);
        }

        return node != null ? node : getNode(propertyRef, name, parent, useGenericsAsFallback);
    }

    private Node getNode(Name name, FeatureType type) {
        Property property = getProperty(name, type);
        return property != null ? Node.of(property) : null;
    }

    private Node getNode(Name name, DataType type) {
        SchemaObject object = getProperty(name, type);
        if (object == null) {
            object = getFeatureType(name);
        }

        return object != null ? Node.of(object) : null;
    }

    private Node getNode(Name name, Property property) {
        SchemaObject object = getProperty(name, property.getProperties());
        return object != null ? Node.of(object) : getNode(name, (Typeable) property);
    }

    private Node getNode(Name name, Typeable typeable) {
        SchemaObject object = getProperty(name, typeable.getType().orElse(DataType.UNDEFINED));
        if (object == null) {
            object = getFeatureType(name);
            if (object == null) {
                object = getDataType(name);
            }
        }

        return object != null ? Node.of(object) : null;
    }

    private Node getNode(PropertyRef propertyRef, Name name, Node parent, boolean useGenericsAsFallback) throws SchemaPathException {
        if (useGenericsAsFallback
                && (name.getNamespace().equals(Namespaces.GENERICS)
                || name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE))) {
            Table table = getTable(parent);
            if (table == Table.FEATURE || table == Table.PROPERTY) {
                GenericAttribute attribute = GenericAttribute.of(name);
                if (propertyRef.getTypeCast().isPresent()) {
                    attribute.setType(propertyRef.getTypeCast()
                            .map(TypeCast::of)
                            .map(typeCast -> typeCast.getType().getName())
                            .map(schemaMapping::getDataType)
                            .orElseThrow(() -> new SchemaPathException("Type cast '::" +
                                    propertyRef.getTypeCast().get() + "' is not supported.")));
                }

                return Node.of(attribute);
            } else {
                throw new SchemaPathException("'" + name + "' is not a valid child of " + parent + ".");
            }
        } else {
            return null;
        }
    }

    private Property getProperty(Name name, Type<?> type) {
        do {
            Property property = getProperty(name, type.getProperties());
            if (property != null) {
                return property;
            }
        } while ((type = type.getSuperType().orElse(null)) != null);

        return null;
    }

    private Property getProperty(Name name, Map<Name, Property> properties) {
        Property property = properties.get(name);
        if (property == null && name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
            property = properties.values().stream()
                    .filter(candidate -> candidate.getName().getLocalName().equalsIgnoreCase(name.getLocalName()))
                    .findFirst().orElse(null);
        }

        return property;
    }

    private FeatureType getFeatureType(Name name) {
        FeatureType featureType = schemaMapping.getFeatureType(name);
        if (featureType == FeatureType.UNDEFINED && name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
            featureType = schemaMapping.getFeatureTypes().stream()
                    .filter(candidate -> candidate.getName().getLocalName().equals(name.getLocalName()))
                    .findFirst().orElse(null);
        }

        return featureType != FeatureType.UNDEFINED ? featureType : null;
    }

    private DataType getDataType(Name name) {
        DataType type = schemaMapping.getDataType(name);
        if (type == DataType.UNDEFINED && name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
            type = schemaMapping.getDataTypes().stream()
                    .filter(candidate -> candidate.getName().getLocalName().equals(name.getLocalName()))
                    .findFirst().orElse(null);
        }

        return type != DataType.UNDEFINED ? type : null;
    }

    private Name getName(PropertyRef propertyRef) {
        return schemaMapping.resolvePrefixedName(propertyRef.getName());
    }

    private Table getTable(Node node) {
        do {
            if (node.getSchemaObject() instanceof Type<?> type) {
                return type.getTable();
            } else if (node.getSchemaObject() instanceof Typeable typeable
                    && typeable.getType().isPresent()) {
                return typeable.getType().get().getTable();
            } else if (node.getSchemaObject() instanceof GenericAttribute) {
                return Table.PROPERTY;
            }
        } while ((node = node.getParent().orElse(null)) != null);

        return null;
    }

    private Node appendTarget(Node parent, PropertyRef childRef) throws SchemaPathException {
        SchemaObject target = null;
        if (parent.getSchemaObject() instanceof Property property) {
            if (property.getTargetFeature().isPresent()) {
                target = property.getTargetFeature().get();
            } else if (property.getTargetGeometry().isPresent()) {
                target = GeometryObject.of(property.getTargetGeometry().get());
            }
        } else if (parent.getSchemaObject() instanceof DataType
                && parent.getParent().map(Node::getSchemaObject).orElse(null) instanceof Property property) {
            if (property.getTargetFeature().isPresent()) {
                target = property.getTargetFeature().get();
            } else if (property.getTargetGeometry().isPresent()) {
                target = GeometryObject.of(property.getTargetGeometry().get());
            }
        } else if (parent.getSchemaObject() instanceof GenericAttribute attribute) {
            DataType type = attribute.getType().orElse(DataType.UNDEFINED);
            if (type.getName().equals(org.citydb.model.property.DataType.FEATURE_PROPERTY.getName())) {
                target = schemaMapping.getFeatureType(Name.of("AbstractCityObject", Namespaces.CORE));
            } else if (type.getName().equals(org.citydb.model.property.DataType.GEOMETRY_PROPERTY.getName())) {
                target = GeometryObject.of(GeometryType.ABSTRACT_GEOMETRY);
            } else if (type.getName()
                    .equals(org.citydb.model.property.DataType.IMPLICIT_GEOMETRY_PROPERTY.getName())) {
                target = schemaMapping.getFeatureType(Name.of("ImplicitGeometry", Namespaces.CORE));
            }
        }

        return target != null && getNode(childRef, parent, false) == null ?
                parent.child(Node.of(target)) :
                parent;
    }

    private boolean isValidTarget(FeatureType featureType, Node node) {
        Node parent = node;
        do {
            if (parent.getSchemaObject() instanceof Property property) {
                return featureType.isSameOrSubTypeOf(property.getTargetFeature().orElse(FeatureType.UNDEFINED));
            } else if (parent.getSchemaObject() instanceof GenericAttribute attribute) {
                return attribute.getType().orElse(DataType.UNDEFINED).getName()
                        .equals(org.citydb.model.property.DataType.FEATURE_PROPERTY.getName());
            }
        } while ((parent = parent.getParent().orElse(null)) != null);

        return false;
    }

    private void injectDataTypes(Node path) {
        Node parent = path.first();
        do {
            if (parent.getSchemaObject() instanceof Typeable typeable
                    && typeable.getType().isPresent()) {
                Node child = parent.getChild().orElse(null);
                if (child == null || !(child.getSchemaObject() instanceof DataType)) {
                    Node tmp = Node.of(typeable.getType().get());
                    tmp.child(child);
                    parent = parent.child(tmp);
                }
            }
        } while ((parent = parent.getChild().orElse(null)) != null);
    }

    private void resolveValue(Node path) {
        Node parent = path.last();
        do {
            Node last = parent;
            if (parent.getSchemaObject() instanceof ValueObject valueObject) {
                if (valueObject.getValue().isPresent()) {
                    Value value = valueObject.getValue().get();
                    value.getProperty().ifPresent(property -> last.child(Node.of(property)));
                } else if (valueObject instanceof Property property) {
                    if (property.getType().isPresent()) {
                        last.child(Node.of(property.getType().get()));
                    } else if (property.getTargetGeometry().isPresent()) {
                        last.child(Node.of(GeometryObject.of(property.getTargetGeometry().get())));
                    }
                } else if (valueObject instanceof DataType type
                        && type.getName().equals(org.citydb.model.property.DataType.GEOMETRY_PROPERTY.getName())) {
                    last.child(Node.of(GeometryObject.of(GeometryType.ABSTRACT_GEOMETRY)));
                }
            }
        } while ((parent = parent.getChild().orElse(null)) != null);
    }
}
