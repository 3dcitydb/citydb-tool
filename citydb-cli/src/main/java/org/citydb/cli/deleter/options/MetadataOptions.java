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
