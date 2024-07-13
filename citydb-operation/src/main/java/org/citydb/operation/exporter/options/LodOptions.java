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

package org.citydb.operation.exporter.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class LodOptions {
    private Set<String> lods;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private LodMode mode;

    public Set<String> getLods() {
        if (lods == null) {
            lods = new LinkedHashSet<>();
        }

        return lods;
    }

    public LodOptions setLods(Set<String> lods) {
        this.lods = lods;
        return this;
    }

    public LodOptions withLod(String lod) {
        if (lod != null) {
            getLods().add(lod);
        }

        return this;
    }

    public LodOptions withLod(int lod) {
        return withLod(String.valueOf(lod));
    }

    public LodMode getMode() {
        return mode != null ? mode : LodMode.KEEP;
    }

    public LodOptions setMode(LodMode mode) {
        this.mode = mode;
        return this;
    }
}
