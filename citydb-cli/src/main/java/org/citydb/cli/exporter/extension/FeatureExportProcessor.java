/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.extension;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.cli.util.FeatureStatistics;
import org.citydb.database.DatabaseManager;
import org.citydb.io.writer.WriteOptions;
import org.citydb.model.feature.Feature;
import org.citydb.plugin.Extension;

@FunctionalInterface
public interface FeatureExportProcessor extends Extension {
    Feature process(Feature feature) throws ExecutionException;

    default void beforeExport(ExportOptions exportOptions, WriteOptions writeOptions, DatabaseManager databaseManager) throws ExecutionException {
    }

    default void afterExport(boolean success, FeatureStatistics statistics) throws ExecutionException {
    }
}
