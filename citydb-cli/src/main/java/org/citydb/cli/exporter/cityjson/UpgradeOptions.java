/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.cli.exporter.cityjson;

import org.citydb.cli.option.Option;
import picocli.CommandLine;

public class UpgradeOptions implements Option {
    @CommandLine.Option(names = "--use-lod4-as-lod3",
            description = "Use LoD4 as LoD3, replacing an existing LoD3.")
    private boolean useLod4AsLod3;

    public boolean isUseLod4AsLod3() {
        return useLod4AsLod3;
    }
}
