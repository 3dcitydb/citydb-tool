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
            logger.info("Writing indexes status as JSON to {}.",
                    outputOptions.isWriteToStdout() ? "standard output" : outputOptions.getFile());

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
