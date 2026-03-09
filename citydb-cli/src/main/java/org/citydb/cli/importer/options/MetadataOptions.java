/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

import org.citydb.cli.common.Option;
import org.citydb.core.time.TimeHelper;
import org.citydb.operation.importer.options.CreationDateMode;
import picocli.CommandLine;

import java.time.OffsetDateTime;

public class MetadataOptions implements Option {
    @CommandLine.Option(names = "--creation-date",
            description = "Time in <YYYY-MM-DD> or <YYYY-MM-DDThh:mm:ss[(+|-)hh:mm]> format or 'now' " +
                    "to use as creation date (default: attribute value or now).")
    private String time;

    @CommandLine.Option(names = "--lineage",
            description = "Lineage to use for the features.")
    private String lineage;

    @CommandLine.Option(names = "--updating-person", paramLabel = "<name>",
            description = "Name of the user responsible for the import (default: database user).")
    private String updatingPerson;

    @CommandLine.Option(names = "--reason-for-update", paramLabel = "<reason>",
            description = "Reason for importing the data.")
    private String reasonForUpdate;

    private CreationDateMode creationDateMode;
    private OffsetDateTime creationDate;

    public String getLineage() {
        return lineage;
    }

    public String getUpdatingPerson() {
        return updatingPerson;
    }

    public String getReasonForUpdate() {
        return reasonForUpdate;
    }

    public CreationDateMode getCreationDateMode() {
        return creationDateMode;
    }

    public OffsetDateTime getCreationDate() {
        return creationDate;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (time != null) {
            if (time.equalsIgnoreCase("now")) {
                creationDateMode = CreationDateMode.OVERWRITE_WITH_NOW;
            } else {
                creationDateMode = CreationDateMode.OVERWRITE_WITH_FIXED;
                try {
                    creationDate = OffsetDateTime.parse(time, TimeHelper.DATE_TIME_FORMATTER);
                } catch (Exception e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "The creation time must be in YYYY-MM-DD or YYYY-MM-DDThh:mm:ss[(+|-)hh:mm] " +
                                    "format but was '" + time + "'");
                }
            }
        }
    }
}
