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

package org.citydb.cli.common;

import org.citydb.config.common.SrsReference;
import picocli.CommandLine;

public class CrsOptions implements Option {
    @CommandLine.Option(names = {"--crs"},
            description = "SRID or identifier of the CRS to use for the coordinates of geometries " +
                    "(default: storage CRS).")
    private String crs;

    @CommandLine.Option(names = {"--crs-name"},
            description = "Name of the CRS to use in the output file.")
    private String name;

    public SrsReference getTargetSrs() {
        if (crs != null) {
            SrsReference reference = new SrsReference();
            try {
                return reference.setSRID(Integer.parseInt(crs));
            } catch (NumberFormatException e) {
                return reference.setIdentifier(crs);
            }
        } else {
            return null;
        }
    }

    public String getName() {
        return name;
    }
}
