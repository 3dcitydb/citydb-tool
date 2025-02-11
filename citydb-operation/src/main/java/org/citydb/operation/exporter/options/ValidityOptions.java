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
