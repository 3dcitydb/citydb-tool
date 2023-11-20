/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.model.common;

import java.util.Objects;

public class Reference extends Child {
    private final String target;
    private final ReferenceType type;

    private Reference(String target, ReferenceType type) {
        this.target = Objects.requireNonNull(target, "The reference target must not be null.");
        this.type = Objects.requireNonNull(type, "The reference type must not be null.");
    }

    public static Reference of(String target, ReferenceType type) {
        return new Reference(target, type);
    }

    public String getTarget() {
        return target;
    }

    public ReferenceType getType() {
        return type;
    }
}
