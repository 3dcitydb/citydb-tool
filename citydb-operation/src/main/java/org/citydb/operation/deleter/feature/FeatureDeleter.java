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

import com.alibaba.fastjson2.JSONObject;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.options.DeleteMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;

public class FeatureDeleter extends DatabaseDeleter {
    private final JSONObject metadata = new JSONObject();

    public FeatureDeleter(DeleteHelper helper) throws SQLException {
        super(helper);
        helper.getOptions().getReasonForUpdate().ifPresent(reasonForUpdate ->
                metadata.put("reason_for_update", reasonForUpdate));
        helper.getOptions().getLineage().ifPresent(lineage ->
                metadata.put("lineage", lineage));
        metadata.put("updating_person", helper.getOptions().getUpdatingPerson()
                .orElseGet(helper.getAdapter().getConnectionDetails()::getUser));
    }

    @Override
    protected PreparedStatement getDeleteStatement(Connection connection) throws SQLException {
        return helper.getOptions().getMode() == DeleteMode.TERMINATE ?
                connection.prepareCall("{call citydb_pkg.terminate_feature(?, ?, ?, ?)}") :
                connection.prepareCall("{call citydb_pkg.delete_feature(?, ?)}");
    }

    public void deleteFeature(long id) throws DeleteException, SQLException {
        addBatch(id);
    }

    @Override
    protected void executeBatch(Long[] ids) throws SQLException {
        if (helper.getOptions().getMode() == DeleteMode.TERMINATE) {
            metadata.put("termination_date", helper.getOptions().getTerminationDate()
                    .orElseGet(() -> OffsetDateTime.now().withNano(0)));

            stmt.setArray(1, helper.getConnection().createArrayOf("bigint", ids));
            stmt.setString(2, helper.getAdapter().getConnectionDetails().getSchema());
            stmt.setObject(3, metadata.toString(), Types.OTHER);
            stmt.setBoolean(4, helper.getOptions().isTerminateWithSubFeatures());
            stmt.execute();
        } else {
            stmt.setArray(1, helper.getConnection().createArrayOf("bigint", ids));
            stmt.setString(2, helper.getAdapter().getConnectionDetails().getSchema());
            stmt.execute();
        }
    }
}
