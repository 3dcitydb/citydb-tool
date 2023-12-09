/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.model.property.Property;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public abstract class Child implements Serializable {
    private Child parent;
    private UserProperties userProperties;

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
}
