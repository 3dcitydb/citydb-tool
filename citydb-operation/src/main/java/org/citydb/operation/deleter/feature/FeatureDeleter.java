/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
import java.time.OffsetDateTime;
import java.util.Set;

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
    protected void executeBatch(Set<Long> ids) throws SQLException {
        if (helper.getOptions().getMode() == DeleteMode.TERMINATE) {
            metadata.put("termination_date", helper.getOptions().getTerminationDate()
                    .orElseGet(() -> OffsetDateTime.now().withNano(0)));

            setLongArrayOrNull(1, ids);
            stmt.setString(2, helper.getAdapter().getConnectionDetails().getSchema());
            setJsonOrNull(3, metadata.toString());
            stmt.setBoolean(4, helper.getOptions().isTerminateWithSubFeatures());
            stmt.execute();
        } else {
            setLongArrayOrNull(1, ids);
            stmt.setString(2, helper.getAdapter().getConnectionDetails().getSchema());
            stmt.execute();
        }
    }
}
