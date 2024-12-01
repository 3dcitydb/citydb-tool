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

package org.citydb.cli.importer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.*;
import org.citydb.cli.importer.filter.Filter;
import org.citydb.cli.importer.options.FilterOptions;
import org.citydb.cli.util.CommandHelper;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.core.file.InputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.InputFiles;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadOptions;
import org.citydb.io.reader.filter.FilterException;
import org.citydb.io.reader.options.InputFormatOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.operation.importer.Importer;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImportController implements Command {
    @CommandLine.Mixin
    protected InputFileOptions inputFileOptions;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected Boolean failFast;

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    protected Path tempDirectory;

    @CommandLine.Mixin
    protected ThreadsOptions threadsOptions;

    @CommandLine.Option(names = "--preview",
            description = "Run in preview mode. Features will not be imported.")
    protected boolean preview;

    @CommandLine.Mixin
    protected IndexOptions indexOptions;

    @CommandLine.Option(names = "--compute-extent",
            description = "Compute and overwrite extents of features.")
    protected Boolean computeEnvelopes;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Filter options:%n")
    protected FilterOptions filterOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    protected final Logger logger = LoggerManager.getInstance().getLogger(ImportController.class);
    protected final CommandHelper helper = CommandHelper.newInstance();
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;

    protected abstract InputFormatOptions getFormatOptions(ConfigObject<InputFormatOptions> formatOptions) throws ExecutionException;

    @Override
    public Integer call() throws ExecutionException {
        return doImport() ?
                CommandLine.ExitCode.OK :
                CommandLine.ExitCode.SOFTWARE;
    }

    protected boolean doImport() throws ExecutionException {
        IOAdapterManager ioManager = helper.createIOAdapterManager();
        IOAdapter ioAdapter = getIOAdapter(ioManager);

        List<InputFile> inputFiles = getInputFiles(ioAdapter, ioManager);
        if (inputFiles.isEmpty()) {
            logger.warn("No files found at {}.", inputFileOptions.joinFiles());
            return true;
        } else {
            logger.info("Found {} file(s) at {}.", inputFiles.size(), inputFileOptions.joinFiles());
        }

        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        ReadOptions readOptions = getReadOptions();
        readOptions.getFormatOptions().set(getFormatOptions(readOptions.getFormatOptions()));
        readOptions.setFilter(getFilter(databaseManager.getAdapter()));
        ImportOptions importOptions = getImportOptions();

        ImportLogger importLogger = new ImportLogger(preview, databaseManager.getAdapter());
        IndexOptions.Mode indexMode = indexOptions.getMode();

        if (indexMode != IndexOptions.Mode.keep) {
            logger.info("Dropping database indexes...");
            helper.dropIndexes(databaseManager.getAdapter());
        }

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());

        if (preview) {
            logger.info("Import is running in preview mode. Features will not be imported.");
        }

        try {
            Importer importer = Importer.newInstance()
                    .setAutoCommit(!preview)
                    .setImportLogger(importLogger);

            AtomicLong counter = new AtomicLong();

            for (int i = 0; shouldRun && i < inputFiles.size(); i++) {
                InputFile inputFile = inputFiles.get(i);
                logger.info("[{}|{}] Importing file {}.", i + 1, inputFiles.size(), inputFile.getContentFile());

                try (FeatureReader reader = ioAdapter.createReader(inputFile, readOptions)) {
                    importer.startSession(databaseManager.getAdapter(), importOptions);

                    reader.read(feature -> {
                        importer.importFeature(feature).whenComplete((descriptor, e) -> {
                            if (descriptor != null) {
                                importLogger.add(feature);
                                long count = counter.incrementAndGet();
                                if (count % 1000 == 0) {
                                    logger.info("{} features processed.", count);
                                }
                            } else {
                                reader.cancel();
                                abort(feature, e);
                            }
                        });
                    });
                } catch (Throwable e) {
                    shouldRun = false;
                    throw e;
                } finally {
                    if (shouldRun && !preview && importer.wasSuccessful()) {
                        importer.commitSession();
                    } else {
                        importer.abortSession();
                    }
                }
            }

            if (shouldRun && indexMode == IndexOptions.Mode.drop_create) {
                logger.info("Re-creating database indexes. This operation may take some time...");
                helper.createIndexes(databaseManager.getAdapter());
            }
        } catch (Throwable e) {
            logger.warn("Database import aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during import.", e);
        } finally {
            databaseManager.disconnect();
            if (!importLogger.getStatistics().isEmpty()) {
                logger.info(!preview ? "Import summary:" : "Preview of features to be imported:");
                importLogger.getStatistics().logFeatureSummary(Level.INFO);
            } else {
                logger.info("No features imported.");
            }
        }

        return shouldRun;
    }

    protected List<InputFile> getInputFiles(IOAdapter ioAdapter, IOAdapterManager ioManager) throws ExecutionException {
        try {
            logger.debug("Searching for {} input files...", ioManager.getFileFormat(ioAdapter));
            return InputFiles.of(inputFileOptions.getFiles())
                    .withFileExtensions(ioManager.getFileExtensions(ioAdapter))
                    .withMediaType(ioManager.getMediaType(ioAdapter))
                    .find();
        } catch (IOException e) {
            throw new ExecutionException("Failed to create list of input files.", e);
        }
    }

    protected Filter getFilter(DatabaseAdapter adapter) throws ExecutionException {
        try {
            return filterOptions != null ?
                    Filter.of(filterOptions.getImportFilterOptions(), adapter) :
                    null;
        } catch (FilterException e) {
            throw new ExecutionException("Failed to build import filter.", e);
        }
    }

    protected ReadOptions getReadOptions() throws ExecutionException {
        ReadOptions readOptions;
        try {
            readOptions = config.getOrElse(ReadOptions.class, ReadOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get read options from config.", e);
        }

        if (failFast != null) {
            readOptions.setFailFast(failFast);
        }

        if (tempDirectory != null) {
            readOptions.setTempDirectory(tempDirectory.toString());
        }

        if (threadsOptions.getNumberOfThreads() != null) {
            readOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        if (inputFileOptions.getEncoding() != null) {
            readOptions.setEncoding(inputFileOptions.getEncoding());
        }

        if (computeEnvelopes != null) {
            readOptions.setComputeEnvelopes(computeEnvelopes);
        }

        return readOptions;
    }

    protected ImportOptions getImportOptions() throws ExecutionException {
        ImportOptions importOptions;
        try {
            importOptions = config.getOrElse(ImportOptions.class, ImportOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get import options from config.", e);
        }

        if (tempDirectory != null) {
            importOptions.setTempDirectory(tempDirectory.toString());
        }

        if (threadsOptions.getNumberOfThreads() != null) {
            importOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        return importOptions;
    }

    private void abort(Feature feature, Throwable e) {
        synchronized (lock) {
            if (shouldRun) {
                shouldRun = false;
                logger.warn("Database import aborted due to an error.");
                helper.logException("Failed to import " + feature.getFeatureType().getLocalName() +
                        " '" + feature.getObjectId().orElse("unknown ID") + "'.", e);
            }
        }
    }
}
