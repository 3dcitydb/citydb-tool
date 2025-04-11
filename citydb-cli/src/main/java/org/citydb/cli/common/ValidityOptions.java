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

package org.citydb.cli.common;

import org.citydb.core.time.TimeHelper;
import org.citydb.database.schema.ValidityReference;
import org.citydb.operation.exporter.options.ValidityMode;
import picocli.CommandLine;

import java.time.OffsetDateTime;

public class ValidityOptions implements Option {
    public enum Mode {valid, invalid, all}

    public enum Reference {database, real_world}

    @CommandLine.Option(names = {"-M", "--validity"}, defaultValue = "valid",
            description = "Process features by validity: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    @CommandLine.Option(names = {"-T", "--validity-at"},
            description = "Check validity at a specific point in time. If provided, the time must be in " +
                    "<YYYY-MM-DD> or <YYYY-MM-DDThh:mm:ss[(+|-)hh:mm]> format.")
    private String time;

    @CommandLine.Option(names = "--validity-reference", paramLabel = "<source>", defaultValue = "database",
            description = "Validity time reference: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Reference reference;

    @CommandLine.Option(names = "--lenient-validity",
            description = "Ignore incomplete validity intervals of features.")
    private boolean lenient;

    private OffsetDateTime at;

    public org.citydb.operation.exporter.options.ValidityOptions getExportValidityOptions() {
        return new org.citydb.operation.exporter.options.ValidityOptions()
                .setMode(switch (mode) {
                    case valid -> ValidityMode.VALID;
                    case invalid -> ValidityMode.INVALID;
                    case all -> ValidityMode.ALL;
                })
                .setReference(switch (reference) {
                    case database -> ValidityReference.DATABASE;
                    case real_world -> ValidityReference.REAL_WORLD;
                })
                .setAt(at)
                .setLenient(lenient);
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (time != null) {
            if (mode == Mode.all) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The validity mode '" + mode + "' does not take a time");
            } else {
                try {
                    at = OffsetDateTime.parse(time, TimeHelper.VALIDITY_TIME_FORMATTER);
                } catch (Exception e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "The validity time must be in YYYY-MM-DD or YYYY-MM-DDThh:mm:ss[(+|-)hh:mm] " +
                                    "format but was '" + time + "'");
                }
            }
        }
    }
}
