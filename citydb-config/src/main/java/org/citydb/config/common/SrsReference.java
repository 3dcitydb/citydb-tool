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

package org.citydb.config.common;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Optional;

public class SrsReference {
    private Integer srid;
    private String identifier;

    public Optional<Integer> getSRID() {
        return Optional.ofNullable(srid);
    }

    @JSONField(name = "srid")
    public SrsReference setSRID(int srid) {
        this.srid = srid;
        return this;
    }

    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    public SrsReference setIdentifier(String uri) {
        this.identifier = uri;
        return this;
    }
}