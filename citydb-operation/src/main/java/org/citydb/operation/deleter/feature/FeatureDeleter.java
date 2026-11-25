/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.feature;

import org.citydb.core.json.Json;
import org.citydb.operation.deleter.DeleteException;
import org.citydb.operation.deleter.DeleteHelper;
import org.citydb.operation.deleter.common.DatabaseDeleter;
import org.citydb.operation.deleter.options.DeleteMode;
import tools.jackson.databind.node.ObjectNode;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class FeatureDeleter extends DatabaseDeleter {
    private final org.citydb.database.util.DeleteHelper deleteHelper;
    private final ObjectNode metadata = Json.objectNode();

    public FeatureDeleter(DeleteHelper helper) throws SQLException {
        super(helper);
        deleteHelper = adapter.getSchemaAdapter().getDeleteHelper(helper.getConnection());
        helper.getOptions().getReasonForUpdate().ifPresent(reasonForUpdate ->
                metadata.put("reason_for_update", reasonForUpdate));
        helper.getOptions().getLineage().ifPresent(lineage ->
                metadata.put("lineage", lineage));
        metadata.put("updating_person", helper.getOptions().getUpdatingPerson()
                .orElseGet(adapter.getConnectionDetails()::getUser));
    }

    public void deleteFeature(long id) throws DeleteException, SQLException {
        addBatch(id);
    }

    @Override
    protected void executeBatch(Set<Long> ids) throws SQLException {
        if (helper.getOptions().getMode() == DeleteMode.TERMINATE) {
            metadata.put("termination_date", helper.getOptions().getTerminationDate()
                    .orElseGet(() -> OffsetDateTime.now().withNano(0))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            deleteHelper.terminateFeatures(ids, metadata);
        } else {
            deleteHelper.deleteFeatures(ids);
        }
    }

    @Override
    public void close() throws SQLException {
        deleteHelper.close();
    }
}
