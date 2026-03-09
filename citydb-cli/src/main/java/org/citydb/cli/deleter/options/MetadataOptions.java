/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.deleter.options;

import org.citydb.cli.common.Option;
import org.citydb.core.time.TimeHelper;
import picocli.CommandLine;

import java.time.OffsetDateTime;

public class MetadataOptions implements Option {
    @CommandLine.Option(names = "--termination-date",
            description = "Time in <YYYY-MM-DD> or <YYYY-MM-DDThh:mm:ss[(+|-)hh:mm]> format to use as " +
                    "termination date (default: now)")
    private String time;

    @CommandLine.Option(names = "--lineage",
            description = "Lineage to use for the features.")
    private String lineage;

    @CommandLine.Option(names = "--updating-person", paramLabel = "<name>",
            description = "Name of the user responsible for the delete (default: database user).")
    private String updatingPerson;

    @CommandLine.Option(names = "--reason-for-update", paramLabel = "<reason>",
            description = "Reason for deleting the data.")
    private String reasonForUpdate;

    private OffsetDateTime terminationDate;

    public OffsetDateTime getTerminationDate() {
        return terminationDate;
    }

    public String getLineage() {
        return lineage;
    }

    public String getUpdatingPerson() {
        return updatingPerson;
    }

    public String getReasonForUpdate() {
        return reasonForUpdate;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (time != null) {
            try {
                terminationDate = OffsetDateTime.parse(time, TimeHelper.DATE_TIME_FORMATTER);
            } catch (Exception e) {
                throw new CommandLine.ParameterException(commandLine,
                        "The termination time must be in YYYY-MM-DD or YYYY-MM-DDThh:mm:ss[(+|-)hh:mm] " +
                                "format but was '" + time + "'");
            }
        }
    }
}
