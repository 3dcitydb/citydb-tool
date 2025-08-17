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

package org.citydb.model.util;

import java.util.UUID;

public class IdCreator {
    private static final IdCreator instance = new IdCreator();
    private String prefix;

    private IdCreator() {
        prefix = getDefaultPrefix();
    }

    public static IdCreator getInstance() {
        return instance;
    }

    public static IdCreator newInstance(String prefix) {
        return new IdCreator().withPrefix(prefix);
    }

    public String getDefaultPrefix() {
        return "ID_";
    }

    public String getPrefix() {
        return prefix;
    }

    public IdCreator withPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : getDefaultPrefix();
        return this;
    }

    public String createId() {
        return prefix + UUID.randomUUID();
    }
}
