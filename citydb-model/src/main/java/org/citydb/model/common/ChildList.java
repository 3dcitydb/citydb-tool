/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class ChildList<T extends Child> extends ArrayList<T> {
    private Child parent;

    public ChildList(Child parent) {
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
    }

    public ChildList(Collection<? extends T> c, Child parent) {
        super(c);
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
        applyParent(c);
    }

    public ChildList(int initialCapacity, Child parent) {
        super(initialCapacity);
        this.parent = Objects.requireNonNull(parent, "The parent must not be null.");
    }

    public Child getParent() {
        return parent;
    }

    void setParent(Child parent) {
        this.parent = parent;
        applyParent(this);
    }

    @Override
    public void add(int index, T element) {
        applyParent(element);
        super.add(index, element);
    }

    @Override
    public boolean add(T o) {
        applyParent(o);
        return super.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        applyParent(c);
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        applyParent(c);
        return super.addAll(index, c);
    }

    @Override
    public T set(int index, T element) {
        applyParent(element);
        return super.set(index, element);
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        super.replaceAll(operator);
        applyParent(this);
    }

    private void applyParent(T child) {
        if (child != null) {
            child.setParent(parent);
        }
    }

    private void applyParent(Collection<? extends T> c) {
        c.forEach(this::applyParent);
    }
}
