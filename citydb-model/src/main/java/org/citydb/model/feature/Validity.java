/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.feature;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.encoding.ValidityTimeReader;

import java.time.OffsetDateTime;
import java.util.Optional;

public class Validity {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private ValidityMode mode;
    @JSONField(deserializeUsing = ValidityTimeReader.class)
    private OffsetDateTime at;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private ValidityReference reference;
    private boolean lenient;

    public static Validity newInstance() {
        return new Validity();
    }

    public static Validity valid() {
        return newInstance().setMode(ValidityMode.VALID);
    }

    public static Validity validAt(OffsetDateTime at) {
        return valid().setAt(at);
    }

    public static Validity invalid() {
        return newInstance().setMode(ValidityMode.INVALID);
    }

    public static Validity invalidAt(OffsetDateTime at) {
        return invalid().setAt(at);
    }

    public static Validity all() {
        return newInstance().setMode(ValidityMode.ALL);
    }

    public ValidityMode getMode() {
        return mode != null ? mode : ValidityMode.VALID;
    }

    public Validity setMode(ValidityMode mode) {
        this.mode = mode;
        return this;
    }

    public Optional<OffsetDateTime> getAt() {
        return Optional.ofNullable(at);
    }

    public Validity setAt(OffsetDateTime at) {
        this.at = at;
        return this;
    }

    public ValidityReference getReference() {
        return reference != null ? reference : ValidityReference.DATABASE;
    }

    public Validity setReference(ValidityReference reference) {
        this.reference = reference;
        return this;
    }

    public boolean isLenient() {
        return lenient;
    }

    public Validity setLenient(boolean lenient) {
        this.lenient = lenient;
        return this;
    }
}
