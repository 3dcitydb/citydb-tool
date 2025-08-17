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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GenericAttribute implements ValueObject, Typeable {
    private final Name name;
    private DataType type;
    private Map<Name, GenericAttribute> attributes;

    private GenericAttribute(Name name, DataType type) {
        this.name = Objects.requireNonNull(name, "The attribute name must not be null.");
        this.type = type;
    }

    public static GenericAttribute of(String name, DataType type) {
        return new GenericAttribute(Name.of(name, Namespaces.GENERICS), type);
    }

    public static GenericAttribute of(String name) {
        return of(name, null);
    }

    public static GenericAttribute of(Name name, DataType type) {
        return of(name.getLocalName(), type);
    }

    public static GenericAttribute of(Name name) {
        return of(name.getLocalName());
    }

    @Override
    public Name getName() {
        return name;
    }

    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(type != null ?
                type.getValue().orElse(null) :
                null);
    }

    @Override
    public Optional<DataType> getType() {
        return Optional.ofNullable(type);
    }

    public GenericAttribute setType(DataType value) {
        this.type = value;
        return this;
    }

    public boolean hasAttributes() {
        return attributes != null && !attributes.isEmpty();
    }

    public Map<Name, GenericAttribute> getAttributes() {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }

        return attributes;
    }

    public GenericAttribute addAttribute(GenericAttribute attribute) {
        if (attribute != null) {
            getAttributes().put(attribute.name, attribute);
        }

        return this;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
