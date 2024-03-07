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

package org.citydb.query.filter.literal;

import org.citydb.database.schema.FeatureType;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.query.filter.common.*;
import org.citydb.query.filter.operation.BooleanExpression;
import org.citydb.query.filter.operation.NumericExpression;

import java.util.Objects;
import java.util.Optional;

public class PropertyRef implements BooleanExpression, GeometryExpression, NumericExpression, CharacterExpression {
    private final String localName;
    private final String namespace;
    private final String prefix;
    private Predicate filter;
    private PropertyRef parent;
    private PropertyRef child;
    private Sign sign;

    private PropertyRef(String localName, String namespace, String prefix) {
        this.localName = Objects.requireNonNull(localName, "The local name must not be null.");
        this.namespace = namespace != null ? namespace : Namespaces.EMPTY_NAMESPACE;
        this.prefix = prefix != null ? prefix : "";
    }

    public static PropertyRef of(String name) {
        int index = name.indexOf(":");
        return index > -1 ?
                new PropertyRef(name.substring(index + 1), null, name.substring(0, index)) :
                new PropertyRef(name, null, null);
    }

    public static PropertyRef of(String localName, String namespace) {
        return new PropertyRef(localName, namespace, null);
    }

    public static PropertyRef of(Name name) {
        return new PropertyRef(name.getLocalName(), name.getNamespace(), null);
    }

    public static PropertyRef of(FeatureTypeProvider provider) {
        return of(provider.getName());
    }

    public static PropertyRef of(FeatureType featureType) {
        return of(featureType.getName());
    }

    public String getLocalName() {
        return localName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public Optional<Predicate> getFilter() {
        return Optional.ofNullable(filter);
    }

    public PropertyRef filter(Predicate filter) {
        this.filter = filter;
        return this;
    }

    public Optional<PropertyRef> getParent() {
        return Optional.ofNullable(parent);
    }

    public Optional<PropertyRef> getChild() {
        return Optional.ofNullable(child);
    }

    public PropertyRef child(String name) {
        return child(of(name));
    }

    public PropertyRef child(String localName, String namespace) {
        return child(of(localName, namespace));
    }

    public PropertyRef child(Name name) {
        return child(of(name));
    }

    public PropertyRef child(FeatureTypeProvider provider) {
        return child(of(provider));
    }

    public PropertyRef child(FeatureType featureType) {
        return child(of(featureType));
    }

    public PropertyRef child(PropertyRef propertyRef) {
        if (propertyRef != null) {
            propertyRef.parent = this;
            return child = propertyRef;
        } else {
            return this;
        }
    }

    @Override
    public Sign getSign() {
        return sign != null ? sign : Sign.PLUS;
    }

    @Override
    public NumericExpression negate() {
        sign = sign == null ? Sign.MINUS : null;
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return prefix.isEmpty() ?
                localName :
                prefix + ":" + localName;
    }
}
