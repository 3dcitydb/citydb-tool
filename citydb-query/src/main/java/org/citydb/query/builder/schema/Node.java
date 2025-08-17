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

package org.citydb.query.builder.schema;

import org.citydb.database.schema.SchemaObject;
import org.citydb.model.common.Name;
import org.citydb.query.filter.common.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class Node {
    private final SchemaObject object;
    private Predicate predicate;
    private Node parent;
    private Node child;

    private Node(SchemaObject object) {
        this.object = Objects.requireNonNull(object, "The schema object must not be null.");
    }

    public static Node of(SchemaObject object) {
        return new Node(object);
    }

    public SchemaObject getSchemaObject() {
        return object;
    }

    public Name getName() {
        return object.getName();
    }

    public Optional<Predicate> getPredicate() {
        return Optional.ofNullable(predicate);
    }

    Node setPredicate(Predicate predicate) {
        this.predicate = predicate;
        return this;
    }

    public Optional<Node> getParent() {
        return Optional.ofNullable(parent);
    }

    public Optional<Node> getChild() {
        return Optional.ofNullable(child);
    }

    public Node child(Node child) {
        if (child != null) {
            child.parent = this;
            return this.child = child;
        } else {
            return this;
        }
    }

    public Node first() {
        Node first = this;
        Node parent = this;
        while ((parent = parent.parent) != null) {
            first = parent;
        }

        return first;
    }

    public Node last() {
        Node last = this;
        Node child = this;
        while ((child = child.child) != null) {
            last = child;
        }

        return last;
    }

    public Stream<Node> upStream() {
        return stream(true);
    }

    public Stream<Node> downStream() {
        return stream(false);
    }

    private Stream<Node> stream(boolean traverseUp) {
        List<Node> path = new ArrayList<>();
        Node node = this;
        do {
            path.add(node);
        } while ((node = traverseUp ? node.parent : node.child) != null);

        return path.stream();
    }

    @Override
    public String toString() {
        return object.getName().toString();
    }
}
