/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
