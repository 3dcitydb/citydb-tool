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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class GenericAttribute implements SchemaObject {
    private final Name name;
    private Map<Name, GenericAttribute> genericAttributes;

    private GenericAttribute(Name name) {
        this.name = Objects.requireNonNull(name, "The name must not be null.");
    }

    public static GenericAttribute of(String name) {
        return new GenericAttribute(Name.of(name, Namespaces.GENERICS));
    }

    public static GenericAttribute of(Name name) {
        return of(name.getLocalName());
    }

    @Override
    public Name getName() {
        return name;
    }

    public boolean hasGenericAttributes() {
        return genericAttributes != null && !genericAttributes.isEmpty();
    }

    public Map<Name, GenericAttribute> getGenericAttributes() {
        if (genericAttributes == null) {
            genericAttributes = new LinkedHashMap<>();
        }

        return genericAttributes;
    }

    public GenericAttribute addGenericAttribute(GenericAttribute genericAttribute) {
        if (genericAttribute != null) {
            getGenericAttributes().put(genericAttribute.name, genericAttribute);
        }

        return this;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
