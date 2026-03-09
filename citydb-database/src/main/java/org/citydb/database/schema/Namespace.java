/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
