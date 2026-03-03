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

package org.citydb.cli.importer.ifc;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.importer.ImportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.ifc.IfcAdapter;
import org.citydb.io.ifc.IfcFormatOptions;
import org.citydb.io.reader.options.InputFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "ifc",
        description = "Import data in IFC format.")
public class IfcImportCommand extends ImportController {
    @CommandLine.Option(names = "--reorient-shells",
            description = "Reorient polygon shells during geometry extraction.")
    private Boolean reorientShells;

    @CommandLine.Option(names = "--no-properties",
            description = "Do not import IFC property sets and element quantities.")
    private Boolean noProperties;

    @CommandLine.Option(names = "--no-appearances",
            description = "Do not import material appearances.")
    private Boolean noAppearances;

    @CommandLine.Option(names = "--no-storeys",
            description = "Do not import building storeys.")
    private Boolean noStoreys;

    @CommandLine.Option(names = "--no-references",
            description = "Do not import external references.")
    private Boolean noReferences;

    @CommandLine.Option(names = "--no-generic-attribute-sets",
            description = "Output IFC properties as direct generic attributes instead of wrapped in GenericAttributeSets.")
    private Boolean noGenericAttributeSets;

    @CommandLine.Option(names = "--pset-names-as-prefixes",
            description = "Prefix property names with their property set name (e.g., [PsetName]PropertyName).")
    private Boolean psetNamesAsPrefixes;

    @CommandLine.Option(names = "--list-unmapped-doors-and-windows",
            description = "List all doors and windows that could not be assigned to a BuildingConstructiveElement.")
    private Boolean listUnmappedDoorsAndWindows;

    @CommandLine.Option(names = "--unrelated-doors-and-windows-in-dummy-bce",
            description = "Put unrelated doors and windows in a dummy BuildingConstructiveElement.")
    private Boolean unrelatedDoorsAndWindowsInDummyBce;

    @CommandLine.Option(names = "--xoffset",
            description = "Offset to shift the model in X direction (applied after georeferencing).")
    private Double xOffset;

    @CommandLine.Option(names = "--yoffset",
            description = "Offset to shift the model in Y direction (applied after georeferencing).")
    private Double yOffset;

    @CommandLine.Option(names = "--zoffset",
            description = "Offset to shift the model in Z direction (applied after georeferencing).")
    private Double zOffset;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(IfcAdapter.class);
    }

    @Override
    protected InputFormatOptions getFormatOptions(ConfigObject<InputFormatOptions> formatOptions) throws ExecutionException {
        IfcFormatOptions options;
        try {
            options = formatOptions.getOrElse(IfcFormatOptions.class, IfcFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get IFC format options from config.", e);
        }

        if (reorientShells != null) {
            options.setReorientShells(reorientShells);
        }

        if (noProperties != null) {
            options.setNoProperties(noProperties);
        }

        if (noAppearances != null) {
            options.setNoAppearances(noAppearances);
        }

        if (noStoreys != null) {
            options.setNoStoreys(noStoreys);
        }

        if (noReferences != null) {
            options.setNoReferences(noReferences);
        }

        if (noGenericAttributeSets != null) {
            options.setNoGenericAttributeSets(noGenericAttributeSets);
        }

        if (psetNamesAsPrefixes != null) {
            options.setPsetNamesAsPrefixes(psetNamesAsPrefixes);
        }

        if (listUnmappedDoorsAndWindows != null) {
            options.setListUnmappedDoorsAndWindows(listUnmappedDoorsAndWindows);
        }

        if (unrelatedDoorsAndWindowsInDummyBce != null) {
            options.setUnrelatedDoorsAndWindowsInDummyBce(unrelatedDoorsAndWindowsInDummyBce);
        }

        if (xOffset != null) {
            options.setXOffset(xOffset);
        }

        if (yOffset != null) {
            options.setYOffset(yOffset);
        }

        if (zOffset != null) {
            options.setZOffset(zOffset);
        }

        return options;
    }

    @Override
    protected void configureFormatOptions(ConfigObject<InputFormatOptions> formatOptions,
                                          DatabaseAdapter adapter) throws ExecutionException {
        try {
            IfcFormatOptions options = formatOptions.getOrElse(IfcFormatOptions.class, IfcFormatOptions::new);
            options.setTargetSrid(adapter.getDatabaseMetadata().getSpatialReference().getSRID());
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to configure IFC format options.", e);
        }
    }
}
