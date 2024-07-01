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
import org.citydb.model.common.PrefixedName;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.query.filter.common.*;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.operation.NumericExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class PropertyRef implements GeometryExpression, NumericExpression, CharacterExpression {
    private final PrefixedName name;
    private final String typeCast;
    private Predicate filter;
    private PropertyRef parent;
    private PropertyRef child;
    private Sign sign;

    private PropertyRef(PrefixedName name, String typeCast) {
        this.name = Objects.requireNonNull(name, "The name must not be null.");
        this.typeCast = typeCast;
    }

    public static PropertyRef of(String name, String namespace) {
        int index = name != null ? name.indexOf("::") : -1;
        return index != -1 ?
                new PropertyRef(PrefixedName.of(name.substring(0, index), namespace), name.substring(index + 2)) :
                new PropertyRef(PrefixedName.of(name, namespace), null);
    }

    public static PropertyRef of(String name) throws FilterParseException {
        return of(name, null);
    }

    public static PropertyRef of(Name name) {
        return of(PrefixedName.of(name));
    }

    public static PropertyRef of(PrefixedName name) {
        return new PropertyRef(name, null);
    }

    public static PropertyRef of(FeatureTypeProvider provider) {
        return of(provider.getName());
    }

    public static PropertyRef of(FeatureType featureType) {
        return of(featureType.getName());
    }

    public PrefixedName getName() {
        return name;
    }

    public Optional<String> getTypeCast() {
        return Optional.ofNullable(typeCast);
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

    public PropertyRef child(String name) throws FilterParseException {
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

    public PropertyRef first() {
        PropertyRef first = this;
        PropertyRef parent = this;
        while ((parent = parent.parent) != null) {
            first = parent;
        }

        return first;
    }

    public PropertyRef last() {
        PropertyRef last = this;
        PropertyRef child = this;
        while ((child = child.child) != null) {
            last = child;
        }

        return last;
    }

    public Stream<PropertyRef> upStream() {
        return stream(true);
    }

    public Stream<PropertyRef> downStream() {
        return stream(false);
    }

    private Stream<PropertyRef> stream(boolean traverseUp) {
        List<PropertyRef> path = new ArrayList<>();
        PropertyRef propertyRef = this;
        do {
            path.add(propertyRef);
        } while ((propertyRef = traverseUp ? propertyRef.parent : propertyRef.child) != null);

        return path.stream();
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
}
