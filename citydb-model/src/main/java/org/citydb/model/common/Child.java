/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.property.Property;
import org.citydb.model.util.CopySession;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public abstract class Child implements Serializable {
    private Child parent;
    private UserProperties userProperties;

    protected abstract Child createClone(CopySession session);

    protected abstract void copyPropertiesTo(Child clone, CopySession session);

    public Optional<Child> getParent() {
        return Optional.ofNullable(parent);
    }

    public <S extends Child> S getParent(Class<S> type) {
        Child parent = this;
        while ((parent = parent.getParent().orElse(null)) != null) {
            if (type.isInstance(parent)) {
                return type.cast(parent);
            }
        }

        return null;
    }

    void setParent(Child parent) {
        this.parent = parent;
    }

    protected <T extends Child> T asChild(T child) {
        if (child != null) {
            child.setParent(this);
        }

        return child;
    }

    protected final <T extends Child> List<T> asChild(List<T> child) {
        if (child instanceof ChildList<T> childList) {
            childList.setParent(this);
            return childList;
        } else {
            return child != null ? new ChildList<>(child, this) : null;
        }
    }

    protected final <T extends Property<?>> PropertyMap<T> asChild(PropertyMap<T> child) {
        if (child != null) {
            child.setParent(this);
        }

        return child;
    }

    public boolean hasUserProperties() {
        return userProperties != null && !userProperties.isEmpty();
    }

    public UserProperties getUserProperties() {
        if (userProperties == null) {
            userProperties = new UserProperties();
        }

        return userProperties;
    }

    @SuppressWarnings("unchecked")
    public <T extends Child> T copy() {
        try (CopySession session = new CopySession()) {
            return (T) copy(session, getClass());
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Child> T copy(T src, CopySession session) {
        return src != null ? (T) src.copy(session, src.getClass()) : null;
    }

    protected final <T extends Child> List<T> copy(List<T> src, Child parent, CopySession session) {
        if (src == null) {
            return null;
        }

        ChildList<T> clone = new ChildList<>(src.size(), parent);
        for (T child : src) {
            clone.add(copy(child, session));
        }

        return clone;
    }

    protected final <T extends Property<?>> PropertyMap<T> copy(PropertyMap<T> src, Child parent, CopySession session) {
        if (src == null) {
            return null;
        }

        PropertyMap<T> clone = new PropertyMap<>(parent);
        src.forEach(property -> clone.add(copy(property, session)));
        return clone;
    }

    final <T extends Child> T copy(CopySession session, Class<T> type) {
        T clone = session.lookupClone(this, type);
        if (clone != null) {
            return clone;
        }

        clone = type.cast(createClone(session));
        session.registerClone(this, clone);

        if (userProperties != null) {
            ((Child) clone).userProperties = userProperties.copy();
        }

        copyPropertiesTo(clone, session);
        return clone;
    }
}
