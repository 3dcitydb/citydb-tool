/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index.status;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.index.IndexController;
import org.citydb.cli.index.IndexStatusBuilder;
import org.citydb.cli.index.options.OutputOptions;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.database.DatabaseManager;
import org.citydb.database.schema.Index;
import org.citydb.database.util.IndexHelper;
import picocli.CommandLine;

import java.io.OutputStream;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@CommandLine.Command(
        name = "status",
        description = "Show indexes with their status in the database.")
public class IndexStatusCommand extends IndexController {
    @CommandLine.Mixin
    private OutputOptions outputOptions;

    private final Logger logger = LoggerManager.getInstance().getLogger(IndexStatusCommand.class);

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        IndexHelper indexHelper = databaseManager.getAdapter().getSchemaAdapter().getIndexHelper();

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());

        Map<Index, Boolean> indexes = new LinkedHashMap<>();
        for (Index index : IndexHelper.DEFAULT_INDEXES) {
            try {
                indexes.put(index, indexHelper.exists(index));
            } catch (SQLException e) {
                throw new ExecutionException("Failed to query status of database indexes.", e);
            }
        }

        if (outputOptions.isOutputSpecified()) {
            logger.info("Writing indexes status as JSON to {}.", outputOptions);
            try (OutputStream stream = outputOptions.openStream()) {
                JSON.writeTo(stream, IndexStatusBuilder.build(indexes), JSONWriter.Feature.PrettyFormatWith2Space);
            } catch (Exception e) {
                throw new ExecutionException("Failed to write indexes status as JSON.", e);
            }
        }

        if (!outputOptions.isWriteToStdout()) {
            logger.info("Indexes list:");
            int i = 1, size = indexes.size();
            for (Map.Entry<Index, Boolean> entry : indexes.entrySet()) {
                logger.info("[{}|{}] Database index on {}: {}", i++, size, entry.getKey(),
                        entry.getValue() ? "on" : "off");
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
