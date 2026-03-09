/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Objects;
import java.util.Optional;

public class PrefixedName extends Name {
    private final String prefix;

    protected PrefixedName(String localName, String namespace, String prefix) {
        super(localName, namespace);
        this.prefix = prefix;
    }

    public static PrefixedName of(String name, String namespace) {
        int index = name != null ? name.indexOf(":") : -1;
        if (index != -1) {
            return namespace == null
                    || namespace.equals(Namespaces.EMPTY_NAMESPACE) ?
                    new PrefixedName(name.substring(index + 1), null, name.substring(0, index)) :
                    new PrefixedName(name.substring(index + 1), namespace, null);
        } else {
            return new PrefixedName(name, namespace, null);
        }
    }

    public static PrefixedName of(String name) {
        return of(name, null);
    }

    public static PrefixedName of(Name name) {
        return new PrefixedName(name.getLocalName(), name.getNamespace(), null);
    }

    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLocalName(), getNamespace(), prefix);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof PrefixedName other)) {
            return false;
        } else {
            return super.equals(other) && Objects.equals(prefix, other.prefix);
        }
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
