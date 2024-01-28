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

package org.citydb.operation.deleter.feature;

import org.citydb.database.schema.Table;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.options.DeleteMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class FeatureDeleter extends DatabaseDeleter {

    public FeatureDeleter(DeleteHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected PreparedStatement getDeleteStatement(Connection connection) throws SQLException {
        if (helper.getOptions().getMode() == DeleteMode.TERMINATE) {
            StringBuilder stmt = new StringBuilder("update ")
                    .append(helper.getTableHelper().getPrefixedTableName(Table.FEATURE))
                    .append(" set termination_date = ?, last_modification_date = ?, updating_person = ?");

            helper.getOptions().getReasonForUpdate().ifPresent(reasonForUpdate ->
                    stmt.append(", reason_for_update = '").append(reasonForUpdate).append("'"));

            helper.getOptions().getLineage().ifPresent(lineage ->
                stmt.append(", lineage = '").append(lineage).append("'"));

            stmt.append(" where id = ? and termination_date is null");
            return connection.prepareStatement(stmt.toString());
        } else {
            return connection.prepareCall("{call citydb_pkg.delete_feature(?, ?)}");
        }
    }

    public void deleteFeature(long id) throws DeleteException, SQLException {
        addBatch(id);
    }

    @Override
    protected void executeBatch(Long[] ids) throws SQLException {
        if (helper.getOptions().getMode() == DeleteMode.TERMINATE) {
            String updatingPerson = helper.getOptions().getUpdatingPerson()
                    .orElse(helper.getAdapter().getConnectionDetails().getUser());

            for (long id : ids) {
                OffsetDateTime now = OffsetDateTime.now();
                stmt.setObject(1, helper.getOptions().getTerminationDate().orElse(now));
                stmt.setObject(2, now);
                stmt.setString(3, updatingPerson);
                stmt.setLong(4, id);
                stmt.addBatch();
            }

            stmt.executeBatch();
        } else {
            stmt.setArray(1, helper.getConnection().createArrayOf("bigint", ids));
            stmt.setString(2, helper.getAdapter().getConnectionDetails().getSchema());
            stmt.execute();
        }
    }
}
