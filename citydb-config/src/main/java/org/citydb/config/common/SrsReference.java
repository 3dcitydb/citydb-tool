/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.config.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public class SrsReference {
    private Integer srid;
    private String identifier;

    public static SrsReference of(int srid) {
        return new SrsReference().setSRID(srid);
    }

    public Optional<Integer> getSRID() {
        return Optional.ofNullable(srid);
    }

    @JsonProperty("srid")
    public SrsReference setSRID(Integer srid) {
        this.srid = srid;
        return this;
    }

    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    public SrsReference setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }
}
