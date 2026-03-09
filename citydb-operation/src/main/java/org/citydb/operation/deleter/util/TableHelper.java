/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
