package org.citydb.cli.info;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConfigOption;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.cli.common.ThreadsOptions;
import org.citydb.cli.deleter.DeleteCommand;
import org.citydb.cli.util.CommandHelper;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.util.IndexHelper;
import org.citydb.logging.LoggerManager;
import org.citydb.util.report.DatabaseReport;
import org.citydb.util.report.ReportOptions;
import picocli.CommandLine;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
        name = "info",
        description = "Show database contents and summary information.")
public class InfoCommand implements Command {
    enum FeatureScope {all, active}

    @CommandLine.Option(names = {"-o", "--output"},
            description = "Write output as a JSON file. Use '-' to write to standard output.")
    private Path file;

    @CommandLine.ArgGroup(exclusive = false)
    protected ThreadsOptions threadsOptions;

    @CommandLine.Option(names = {"-s", "--feature-scope"}, paramLabel = "<scope>", defaultValue = "all",
            description = "Feature scope: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). For 'active', " +
                    "only properties of non-terminated features are considered.")
    private FeatureScope scope;

    @CommandLine.Option(names = "--include-generic-attributes",
            description = "Include generic attributes and their data types.")
    private Boolean includeGenericAttributes;

    @CommandLine.Option(names = "--include-size-metrics",
            description = "Include database size metrics.")
    private Boolean includeSizeMetrics;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger(DeleteCommand.class);
    private final CommandHelper helper = CommandHelper.newInstance();

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        ReportOptions reportOptions = getReportOptions();

        IndexHelper.Status status = helper.getIndexStatus(databaseManager.getAdapter());
        helper.logIndexStatus(Level.INFO, status);

        boolean writeToStdout = file != null && file.toString().equals("-");
        file = helper.resolvePath(file);

        try {
            logger.info("Collecting database contents and summary information...");
            if (status != IndexHelper.Status.ON) {
                logger.warn("Generating the database report may be slower because not all indexes are enabled.");
            }

            DatabaseReport report = DatabaseReport.build(reportOptions, databaseManager.getAdapter());

            if (file != null) {
                if (writeToStdout) {
                    logger.info("Writing JSON report to standard output.");
                } else {
                    logger.info("Writing report to JSON file {}.", file);
                }

                try (OutputStream stream = writeToStdout ? System.out : Files.newOutputStream(file)) {
                    JSON.writeTo(stream, report.toJSON(), JSONWriter.Feature.PrettyFormat);
                }
            }

            if (!writeToStdout) {
                report.print(logger::info);
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to create database report.", e);
        }

        return CommandLine.ExitCode.OK;
    }

    private ReportOptions getReportOptions() throws ExecutionException {
        ReportOptions reportOptions;
        try {
            reportOptions = config.getOrElse(ReportOptions.class, ReportOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get report options from config.", e);
        }

        if (threadsOptions != null && threadsOptions.getNumberOfThreads() != null) {
            reportOptions.setNumberOfThreads(threadsOptions.getNumberOfThreads());
        }

        reportOptions.onlyActiveFeatures(scope == FeatureScope.active);

        if (includeGenericAttributes != null) {
            reportOptions.includeGenericAttributes(includeGenericAttributes);
        }

        if (includeSizeMetrics != null) {
            reportOptions.includeDatabaseSize(includeSizeMetrics);
        }

        return reportOptions;
    }
}
