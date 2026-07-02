/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.writer.metadata;

import org.citydb.model.geometry.Envelope;

import java.util.Optional;

public class Metadata {
    private String title;
    private String description;
    private Envelope extent;

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Metadata setTitle(String title) {
        this.title = title;
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Metadata setDescription(String description) {
        this.description = description;
        return this;
    }

    public Optional<Envelope> getExtent() {
        return Optional.ofNullable(extent);
    }

    public Metadata setExtent(Envelope extent) {
        this.extent = extent;
        return this;
    }
}
