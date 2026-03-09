/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.util;

import org.xmlobjects.gml.util.id.DefaultIdCreator;

import java.util.Objects;
import java.util.UUID;

public class IdCreator implements org.xmlobjects.gml.util.id.IdCreator {
    private final String seed;
    private final String prefix = DefaultIdCreator.getInstance().getDefaultPrefix();
    private long index;

    public IdCreator(String seed) {
        this.seed = Objects.requireNonNull(seed, "The seed must not be null.");
    }

    @Override
    public String createId() {
        String id = seed + index++;
        return prefix + UUID.nameUUIDFromBytes(id.getBytes());
    }
}
