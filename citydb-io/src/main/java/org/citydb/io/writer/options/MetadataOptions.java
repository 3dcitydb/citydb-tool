/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.writer.options;

import java.util.Optional;

public class MetadataOptions {
    private String title;
    private String description;
    private boolean computeExtent;

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public MetadataOptions setTitle(String title) {
        this.title = title;
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public MetadataOptions setDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isComputeExtent() {
        return computeExtent;
    }

    public MetadataOptions setComputeExtent(boolean computeExtent) {
        this.computeExtent = computeExtent;
        return this;
    }
}
