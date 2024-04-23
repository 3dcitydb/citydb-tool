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

package org.citydb.model.common;

import java.util.Optional;

public class PrefixedName extends Name {
    private final String prefix;

    protected PrefixedName(String localName, String namespace, String prefix) {
        super(localName, namespace);
        this.prefix = prefix;
    }

    public static PrefixedName of(String name, String namespace) {
        int index = name.indexOf(":");
        return index > -1 ?
                new PrefixedName(name.substring(index + 1), namespace, name.substring(0, index)) :
                new PrefixedName(name, namespace, null);
    }

    public static PrefixedName of(String name) {
        return of(name, null);
    }

    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    @Override
    public String toString() {
        return prefix != null
                && !prefix.isEmpty()
                && getNamespace().equals(Namespaces.EMPTY_NAMESPACE) ?
                prefix + ":" + getLocalName() :
                super.toString();
    }
}
