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

package org.citydb.cli.importer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.CliConstants;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.*;
import org.citydb.cli.importer.duplicate.DuplicateController;
import org.citydb.cli.importer.filter.Filter;
import org.citydb.cli.importer.options.FilterOptions;
import org.citydb.cli.importer.options.ImportMode;
import org.citydb.cli.importer.options.MetadataOptions;
import org.citydb.cli.logging.LoggerManager;
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
import org.citydb.model.feature.Feature;
import org.citydb.operation.importer.Importer;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImportController implements Command {
    enum Mode {import_all, skip, delete, terminate}

    @CommandLine.Mixin
    protected InputFileOptions inputFileOptions;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected Boolean failFast;

    @CommandLine.Option(names = "--temp-dir", paramLabel = "<dir>",
            description = "Store temporary files in this directory.")
    protected Path tempDirectory;

    @CommandLine.Option(names = {"-m", "--import-mode"}, paramLabel = "<mode>", defaultValue = "import_all",
            description = "Import mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    @CommandLine.ArgGroup(exclusive = false)
    protected ThreadsOptions threadsOptions;

    @CommandLine.Option(names = "--preview",
            description = "Run in preview mode. Features will not be imported.")
    protected boolean preview;

    @CommandLine.Mixin
    protected IndexOptions indexOptions;

    @CommandLine.Option(names = "--compute-extent",
            description = "Compute and overwrite extents of features.")
    protected Boolean computeEnvelopes;

    @CommandLine.ArgGroup(exclusive = false)
    protected TransformOptions transformOptions;

    @CommandLine.ArgGroup(exclusive = false, order = 1,
            heading = "Metadata options:%n")
    private MetadataOptions metadataOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Filter options:%n")
    private FilterOptions filterOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @ConfigOption
    private Config config;

    protected static final int ARG_GROUP_ORDER = 2;
    protected final Logger logger = LoggerManager.getInstance().getLogger(ImportController.class);
    protected final CommandHelper helper = CommandHelper.getInstance();
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
        ImportOptions importOptions = getImportOptions();
        ReadOptions readOptions = getReadOptions();
        readOptions.getFormatOptions().set(getFormatOptions(readOptions.getFormatOptions()));

        Filter filter = getFilter(importOptions, databaseManager.getAdapter());
        readOptions.setFilter(filter);

        ImportLogger importLogger = new ImportLogger(preview, databaseManager.getAdapter());
        ImportMode importMode = importOptions.getMode();
        IndexMode indexMode = importOptions.getIndexMode();

        if (indexMode != IndexMode.KEEP) {
            logger.info("Dropping database indexes...");
            helper.dropIndexes(databaseManager.getAdapter());
        }

        helper.logIndexStatus(Level.INFO, databaseManager.getAdapter());

        if (preview) {
            logger.info("Import is running in preview mode. Features will not be imported.");
        }

        try (DuplicateController duplicateController = DuplicateController.of(importOptions,
                databaseManager.getAdapter(), preview)) {
            Importer importer = Importer.newInstance()
                    .setTransactionMode(preview ?
                            Importer.TransactionMode.AUTO_ROLLBACK :
                            Importer.TransactionMode.AUTO_COMMIT)
                    .setImportLogger(importLogger);

            AtomicLong counter = new AtomicLong();

            for (int i = 0; shouldRun && filter.isCountWithinLimit() && i < inputFiles.size(); i++) {
                InputFile inputFile = inputFiles.get(i);
                logger.info("[{}|{}] Importing file {}.", i + 1, inputFiles.size(), inputFile.getContentFile());

                try (FeatureReader reader = ioAdapter.createReader(inputFile, readOptions)) {
                    if (importMode != ImportMode.IMPORT_ALL) {
                        logger.debug("Checking database for duplicate features...");
                        DuplicateController.Result result = duplicateController.processDuplicates(reader, filter);
                        if (result == DuplicateController.Result.SKIP_FILE) {
                            logger.info("All features to be imported are duplicates. Skipping input file.");
                            continue;
                        }
                    }

                    importer.startSession(databaseManager.getAdapter(), importOptions);

                    reader.read(feature -> {
                        if (importMode != ImportMode.SKIP_EXISTING || !duplicateController.isDuplicate(feature)) {
                            importLogger.add(feature);
                            importer.importFeature(feature).whenComplete((descriptor, e) -> {
                                if (descriptor != null) {
                                    long count = counter.incrementAndGet();
                                    if (count % 1000 == 0) {
                                        logger.info("{} features processed.", count);
                                    }
                                } else {
                                    abort(feature, e);
                                    reader.cancel();
                                }
                            });
                        }
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

            if (shouldRun && indexMode == IndexMode.DROP_CREATE) {
                logger.info("Re-creating database indexes. This operation may take some time...");
                helper.createIndexes(databaseManager.getAdapter());
            }
        } catch (Throwable e) {
            logger.warn("Database import aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during import.", e);
        } finally {
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
                    .withBaseDirectory(CliConstants.WORKING_DIR)
                    .find();
        } catch (IOException e) {
            throw new ExecutionException("Failed to create list of input files.", e);
        }
    }

    protected Filter getFilter(ImportOptions importOptions, DatabaseAdapter adapter) throws ExecutionException {
        try {
            org.citydb.io.reader.options.FilterOptions filterOptions = importOptions.getFilterOptions().orElse(null);
            return filterOptions != null && !filterOptions.isEmpty() ?
                    Filter.of(filterOptions, adapter) :
                    Filter.acceptAll();
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
            readOptions.setTempDirectory(helper.resolveAgainstWorkingDir(tempDirectory));
        }

        if (threadsOptions != null && threadsOptions.getNumberOfThreads() != null) {
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

        if (failFast != null) {
            importOptions.setFailFast(failFast);
        }
        if (tempDirectory != null) {
            importOptions.setTempDirectory(helper.resolveAgainstWorkingDir(tempDirectory));
        }

        if (threadsOptions != null && threadsOptions.getNumberOfThreads() != null) {
            importOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        if (metadataOptions != null) {
            if (metadataOptions.getCreationDate() != null) {
                importOptions.setCreationDate(metadataOptions.getCreationDate());
            }

            if (metadataOptions.getCreationDateMode() != null) {
                importOptions.setCreationDateMode(metadataOptions.getCreationDateMode());
            }

            if (metadataOptions.getLineage() != null) {
                importOptions.setLineage(metadataOptions.getLineage());
            }

            if (metadataOptions.getUpdatingPerson() != null) {
                importOptions.setUpdatingPerson(metadataOptions.getUpdatingPerson());
            }

            if (metadataOptions.getReasonForUpdate() != null) {
                importOptions.setReasonForUpdate(metadataOptions.getReasonForUpdate());
            }
        }

        if (Command.hasMatchedOption("--import-mode", commandSpec)) {
            importOptions.setMode(switch (mode) {
                case import_all -> ImportMode.IMPORT_ALL;
                case skip -> ImportMode.SKIP_EXISTING;
                case delete -> ImportMode.DELETE_EXISTING;
                case terminate -> ImportMode.TERMINATE_EXISTING;
            });
        }

        if (Command.hasMatchedOption("--index-mode", commandSpec)) {
            importOptions.setIndexMode(switch (indexOptions.getMode()) {
                case keep -> IndexMode.KEEP;
                case drop -> IndexMode.DROP;
                case drop_create -> IndexMode.DROP_CREATE;
            });
        }

        if (transformOptions != null) {
            importOptions.setAffineTransform(transformOptions.getTransformationMatrix());
        }

        if (filterOptions != null) {
            importOptions.setFilterOptions(filterOptions.getImportFilterOptions());
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
