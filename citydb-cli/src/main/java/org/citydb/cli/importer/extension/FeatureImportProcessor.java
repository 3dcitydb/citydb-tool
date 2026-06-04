/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.extension;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.importer.ImportOptions;
import org.citydb.cli.util.FeatureStatistics;
import org.citydb.database.DatabaseManager;
import org.citydb.io.reader.ReadOptions;
import org.citydb.model.feature.Feature;
import org.citydb.plugin.Extension;

@FunctionalInterface
public interface FeatureImportProcessor extends Extension {
    Feature process(Feature feature) throws ExecutionException;

    default void beforeImport(ImportOptions importOptions, ReadOptions readOptions, DatabaseManager databaseManager) throws ExecutionException {
    }

    default void afterImport(boolean success, FeatureStatistics statistics) throws ExecutionException {
    }
}
