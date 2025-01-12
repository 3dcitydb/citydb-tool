/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.tiling.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.database.srs.SrsUnit;

import java.util.Optional;

public class Dimension {
    private double value;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private SrsUnit unit;

    public static Dimension of(double value, SrsUnit unit) {
        return new Dimension().setValue(value).setUnit(unit);
    }

    public static Dimension of(double value) {
        return of(value, null);
    }

    public double getValue() {
        return value;
    }

    public Dimension setValue(double value) {
        this.value = value;
        return this;
    }

    public Optional<SrsUnit> getUnit() {
        return Optional.ofNullable(unit);
    }

    public Dimension setUnit(SrsUnit unit) {
        this.unit = unit;
        return this;
    }
}
