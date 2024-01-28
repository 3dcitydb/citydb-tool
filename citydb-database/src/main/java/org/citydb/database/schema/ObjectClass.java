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

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

public class ObjectClass {
    public static final ObjectClass UNDEFINED = new ObjectClass(
            1, Name.of("Undefined", Namespaces.CORE), 0, true, false);

    private final int id;
    private final Name name;
    private final int superClassId;
    private final boolean isAbstract;
    private final boolean isTopLevel;

    ObjectClass(int id, Name name, int superClassId, boolean isAbstract, boolean isTopLevel) {
        this.id = id;
        this.name = name;
        this.superClassId = superClassId;
        this.isAbstract = isAbstract;
        this.isTopLevel = isTopLevel;
    }

    public int getId() {
        return id;
    }

    public Name getName() {
        return name;
    }

    public int getSuperClassId() {
        return superClassId;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isTopLevel() {
        return isTopLevel;
    }
}
