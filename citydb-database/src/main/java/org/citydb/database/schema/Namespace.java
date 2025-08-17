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

import org.citydb.model.common.Namespaces;

import java.util.Optional;

public class Namespace {
    public static final Namespace UNDEFINED = new Namespace(null, Namespaces.EMPTY_NAMESPACE, null);

    private final Integer id;
    private final String uri;
    private final String alias;

    private Namespace(Integer id, String uri, String alias) {
        this.id = id;
        this.uri = uri;
        this.alias = alias;
    }

    static Namespace of(int id, String uri, String alias) throws SchemaException {
        if (uri == null) {
            throw new SchemaException("No namespace URI defined for the namespace (ID " + id + ").");
        }

        return new Namespace(id, uri, alias);
    }

    public Integer getId() {
        return id;
    }

    public String getURI() {
        return uri;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }
}
