/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.encoding.ValidityTimeReader;
import org.citydb.database.schema.ValidityReference;

import java.time.OffsetDateTime;
import java.util.Optional;

public class ValidityOptions {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private ValidityMode mode;
    @JSONField(deserializeUsing = ValidityTimeReader.class)
    private OffsetDateTime at;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private ValidityReference reference;
    private boolean lenient;

    public ValidityMode getMode() {
        return mode != null ? mode : ValidityMode.VALID;
    }

    public ValidityOptions setMode(ValidityMode mode) {
        this.mode = mode;
        return this;
    }

    public Optional<OffsetDateTime> getAt() {
        return Optional.ofNullable(at);
    }

    public ValidityOptions setAt(OffsetDateTime at) {
        this.at = at;
        return this;
    }

    public ValidityReference getReference() {
        return reference != null ? reference : ValidityReference.DATABASE;
    }

    public ValidityOptions setReference(ValidityReference reference) {
        this.reference = reference;
        return this;
    }

    public boolean isLenient() {
        return lenient;
    }

    public ValidityOptions setLenient(boolean lenient) {
        this.lenient = lenient;
        return this;
    }
}
