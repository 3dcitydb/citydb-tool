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

package org.citydb.cli.importer.options;

import picocli.CommandLine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IdOptions {
    @CommandLine.Option(names = {"-i", "--id"}, split = ",", paramLabel = "<id>",
            description = "Identifiers of the features to process.")
    private String[] ids;

    public Set<String> getIds() {
        return new HashSet<>(Arrays.asList(ids));
    }
}
