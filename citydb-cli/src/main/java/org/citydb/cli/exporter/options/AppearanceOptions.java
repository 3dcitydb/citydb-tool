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

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

import java.util.Set;
import java.util.stream.Collectors;

public class AppearanceOptions implements Option {
    @CommandLine.Option(names = {"-a", "--appearance-theme"}, split = ",", paramLabel = "<theme>",
            description = "Export appearances with a matching theme. Use 'none' for the null theme.")
    private Set<String> themes;

    public Set<String> getThemes() {
        return themes;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (themes != null) {
            themes = themes.stream()
                    .map(theme -> "none".equalsIgnoreCase(theme) ? null : theme)
                    .collect(Collectors.toSet());
        }
    }
}
