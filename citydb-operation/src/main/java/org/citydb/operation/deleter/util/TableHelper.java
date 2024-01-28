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

package org.citydb.operation.deleter.util;

import org.citydb.database.schema.Table;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.feature.FeatureDeleter;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TableHelper {
    private final DeleteHelper helper;
    private final Map<String, DatabaseDeleter> deleters = new HashMap<>();

    public TableHelper(DeleteHelper helper) {
        this.helper = helper;
    }

    public String getPrefixedTableName(Table table) {
        return helper.getAdapter().getConnectionDetails().getSchema() + "." + table;
    }

    public <T extends DatabaseDeleter> T getOrCreateDeleter(Class<T> type) throws DeleteException {
        DatabaseDeleter deleter = deleters.get(type.getName());
        if (deleter == null) {
            try {
                if (type == FeatureDeleter.class) {
                    deleter = new FeatureDeleter(helper);
                }

                if (deleter != null) {
                    deleters.put(type.getName(), deleter);
                }
            } catch (SQLException e) {
                throw new DeleteException("Failed to build deleter of type " + type.getName() + ".", e);
            }
        }

        if (type.isInstance(deleter)) {
            return type.cast(deleter);
        } else {
            throw new DeleteException("Failed to build deleter of type " + type.getName() + ".");
        }
    }

    public Collection<DatabaseDeleter> getDeleters() {
        return deleters.values();
    }
}
