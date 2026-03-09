/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.io.Serializable;
import java.util.Objects;

public class Name implements Serializable {
    private final String localName;
    private final String namespace;

    protected Name(String localName, String namespace) {
        this.localName = Objects.requireNonNull(localName, "The local name must not be null.");
        this.namespace = Namespaces.ensureNonNull(namespace);
    }

    public static Name of(String localName, String namespace) {
        return new Name(localName, namespace);
    }

    public static Name of(String localName) {
        return new Name(localName, Namespaces.EMPTY_NAMESPACE);
    }

    public String getLocalName() {
        return localName;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public int hashCode() {
        return Objects.hash(localName, namespace);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Name other)) {
            return false;
        } else {
            return localName.equals(other.localName) && namespace.equals(other.namespace);
        }
    }

    @Override
    public String toString() {
        return namespace.equals(Namespaces.EMPTY_NAMESPACE) ?
                localName :
                "{" + namespace + "}" + localName;
    }
}
