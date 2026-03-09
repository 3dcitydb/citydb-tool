/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

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
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.CommandHelper;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.database.DatabaseManager;
import org.citydb.database.util.IndexHelper;
import org.citydb.util.report.DatabaseReport;
import org.citydb.util.report.DatabaseReportException;
import org.citydb.util.report.ReportOptions;
import org.citydb.util.report.options.FeatureScope;
import picocli.CommandLine;

import java.io.OutputStream;

@CommandLine.Command(
        name = "info",
        description = "Show database contents and summary information.")
public class InfoCommand implements Command {
    enum Scope {all, active}

    @CommandLine.Mixin
    private OutputOptions outputOptions;

    @CommandLine.ArgGroup(exclusive = false)
    protected ThreadsOptions threadsOptions;

    @CommandLine.Option(names = {"-s", "--feature-scope"}, paramLabel = "<scope>", defaultValue = "all",
            description = "Feature scope: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). For 'active', " +
                    "only properties of non-terminated features are considered.")
    private Scope scope;

    @CommandLine.Option(names = {"-c", "--compact"},
            description = "Only generate a compact overview.")
    private Boolean compact;

    @CommandLine.Option(names = "--include-generic-attributes",
            description = "Include generic attributes and their data types.")
    private Boolean includeGenericAttributes;

    @CommandLine.Option(names = "--include-size-metrics",
            description = "Include database size metrics.")
    private Boolean includeSizeMetrics;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Database connection options:%n")
    private ConnectionOptions connectionOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @ConfigOption
    private Config config;

    private final Logger logger = LoggerManager.getInstance().getLogger(InfoCommand.class);
    private final CommandHelper helper = CommandHelper.getInstance();

    @Override
    public Integer call() throws ExecutionException {
        DatabaseManager databaseManager = helper.connect(connectionOptions, config);
        ReportOptions reportOptions = getReportOptions();

        IndexHelper.Status status = helper.getIndexStatus(databaseManager.getAdapter());
        helper.logIndexStatus(Level.INFO, status);

        logger.info("Collecting database contents and summary information...");
        if (status != IndexHelper.Status.ON) {
            logger.warn("Generating the database report may be slower because not all indexes are enabled.");
        }

        DatabaseReport report;
        try {
            report = DatabaseReport.build(reportOptions, databaseManager.getAdapter());
        } catch (DatabaseReportException e) {
            throw new ExecutionException("Failed to create database report.", e);
        }

        if (outputOptions.isOutputSpecified()) {
            logger.info("Writing database report as JSON to {}.", outputOptions);
            try (OutputStream stream = outputOptions.openStream()) {
                JSON.writeTo(stream, report.toJSON(), JSONWriter.Feature.PrettyFormatWith2Space);
            } catch (Exception e) {
                throw new ExecutionException("Failed to write database report as JSON.", e);
            }
        }

        if (!outputOptions.isWriteToStdout()) {
            report.print(logger::info);
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

        if (Command.hasMatchedOption("--feature-scope", commandSpec)) {
            reportOptions.setFeatureScope(scope == Scope.active ? FeatureScope.ACTIVE : FeatureScope.ALL);
        }

        if (compact != null) {
            reportOptions.setCompact(compact);
        }

        if (includeGenericAttributes != null) {
            reportOptions.setIncludeGenericAttributes(includeGenericAttributes);
        }

        if (includeSizeMetrics != null) {
            reportOptions.setIncludeDatabaseSize(includeSizeMetrics);
        }

        return reportOptions;
    }
}
