/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.command.Command;
import org.citydb.cli.option.DatabaseOptions;
import org.citydb.cli.option.IndexOption;
import org.citydb.cli.option.InputFileOptions;
import org.citydb.cli.option.ThreadsOption;
import org.citydb.cli.util.CommandHelper;
import org.citydb.core.file.InputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.InputFiles;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.operation.importer.ImportOptions;
import org.citydb.operation.importer.Importer;
import org.citydb.operation.importer.util.StatisticsConsumer;
import org.citydb.operation.util.FeatureStatistics;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImportController implements Command {
    @CommandLine.Mixin
    protected InputFileOptions inputFileOptions;

    @CommandLine.Option(names = "--fail-fast",
            description = "Fail fast on errors.")
    protected boolean failFast;

    @CommandLine.Mixin
    protected ThreadsOption threadsOption;

    @CommandLine.Option(names = "--preview",
            description = "Run in preview mode. Features will not be imported.")
    protected boolean preview;

    @CommandLine.Mixin
    protected IndexOption indexOption;

    @CommandLine.Option(names = "--compute-extent",
            description = "Compute and overwrite extents of features.")
    protected boolean computeEnvelopes;

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1", order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected final DatabaseOptions databaseOptions = new DatabaseOptions();

    protected final Logger logger = LoggerManager.getInstance().getLogger();
    protected final CommandHelper helper = CommandHelper.of(logger);
    private final Object lock = new Object();
    private volatile boolean shouldRun = true;

    protected abstract IOAdapter getIOAdapter(IOAdapterManager ioManager) throws ExecutionException;
    protected abstract Object getFormatOptions() throws ExecutionException;

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
            logger.warn("No files found at " + inputFileOptions.joinFiles() + ".");
            return true;
        } else {
            logger.info("Found " + inputFiles.size() + " file(s) at " + inputFileOptions.joinFiles() + ".");
        }

        DatabaseManager databaseManager = helper.connect(databaseOptions);
        FeatureStatistics statistics = helper.createFeatureStatistics(databaseManager.getAdapter());
        IndexOption.Mode indexMode = indexOption.getMode();

        if (indexMode != IndexOption.Mode.keep) {
            logger.info("Dropping database indexes...");
            helper.dropIndexes(databaseManager.getAdapter());
        }

        helper.printIndexStatus(databaseManager.getAdapter(), logger::info);

        if (preview) {
            logger.info("Import is running in preview mode. Features will not be imported.");
        }

        try {
            Importer importer = Importer.newInstance()
                    .setAutoCommit(!preview)
                    .setFeatureStatisticsConsumer(StatisticsConsumer.of(statistics::merge, preview ?
                            StatisticsConsumer.Mode.COUNT_ALL :
                            StatisticsConsumer.Mode.COUNT_COMMITTED));

            ReadOptions readOptions = getReadOptions().setFormatOptions(getFormatOptions());
            ImportOptions importOptions = getImportOptions();
            AtomicLong counter = new AtomicLong();

            for (int i = 0; shouldRun && i < inputFiles.size(); i++) {
                InputFile inputFile = inputFiles.get(i);
                logger.info("[" + (i + 1) + "|" + inputFiles.size() + "] Importing file " +
                        inputFile.getContentFile() + ".");

                try (FeatureReader reader = ioAdapter.createReader()) {
                    logger.debug("Preprocessing input file...");
                    reader.initialize(inputFile, readOptions);

                    logger.debug("Importing features from input file...");
                    importer.startSession(databaseManager.getAdapter(), importOptions);

                    reader.read(feature -> importer.importFeature(feature)
                            .whenComplete((descriptor, e) -> {
                                if (descriptor != null) {
                                    long count = counter.incrementAndGet();
                                    if (count % 1000 == 0) {
                                        logger.info(count + " features processed.");
                                    }
                                } else {
                                    reader.cancel();
                                    abort(feature, e);
                                }
                            }));
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

            if (shouldRun && indexMode == IndexOption.Mode.drop_create) {
                logger.info("Re-creating database indexes. This operation may take some time...");
                helper.createIndexes(databaseManager.getAdapter());
            }
        } catch (Throwable e) {
            logger.warn("Database import aborted due to an error.");
            throw new ExecutionException("A fatal error has occurred during import.", e);
        } finally {
            databaseManager.disconnect();
            if (!statistics.isEmpty()) {
                logger.info(!preview ? "Import summary:" : "Preview of features to be imported:");
                statistics.printFeatureSummary(logger::info);
            } else {
                logger.info("No features imported.");
            }
        }

        return shouldRun;
    }

    protected List<InputFile> getInputFiles(IOAdapter ioAdapter, IOAdapterManager ioManager) throws ExecutionException {
        try {
            logger.debug("Searching for " + ioManager.getFileFormat(ioAdapter) + " input files...");
            return InputFiles.of(inputFileOptions.getFiles())
                    .withFileExtensions(ioManager.getFileExtensions(ioAdapter))
                    .withMediaType(ioManager.getMediaType(ioAdapter))
                    .find();
        } catch (IOException e) {
            throw new ExecutionException("Failed to create list of input files.", e);
        }
    }

    protected ReadOptions getReadOptions() {
        return ReadOptions.defaults()
                .setFailFast(failFast)
                .setNumberOfThreads(threadsOption.getNumberOfThreads())
                .setEncoding(inputFileOptions.getEncoding())
                .setComputeEnvelopes(computeEnvelopes);
    }

    protected ImportOptions getImportOptions() {
        return ImportOptions.defaults()
                .setNumberOfThreads(threadsOption.getNumberOfThreads());
    }

    private void abort(Feature feature, Throwable e) {
        synchronized (lock) {
            if (shouldRun) {
                logger.warn("Database import aborted due to an error.");
                helper.logException("Failed to import " + feature.getFeatureType().getLocalName() +
                        " '" + feature.getObjectId().orElse("unknown ID") + "'.", e);
            }

            shouldRun = false;
        }
    }
}
