/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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
