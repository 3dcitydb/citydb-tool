/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.cli.importer.cityjson;

import org.citydb.cli.importer.ImportController;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.citygml.reader.CityJSONFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "cityjson",
        description = "Import data in CityJSON format.")
public class CityJSONImportCommand extends ImportController {
    @CommandLine.Option(names = "--no-map-unknown-objects", negatable = true, defaultValue = "true",
            description = "Map city objects from unsupported extensions onto generic city objects " +
                    "(default: ${DEFAULT-VALUE}).")
    private boolean mapUnknownObjects;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityJSONAdapter.class);
    }

    @Override
    protected Object getFormatOptions() {
        return new CityJSONFormatOptions()
                .setMapUnsupportedTypesToGenerics(mapUnknownObjects);
    }
}
