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

package org.citydb.cli.exporter.cityjson;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.exporter.ExportController;
import org.citydb.cli.exporter.ExportOptions;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.database.DatabaseManager;
import org.citydb.database.util.SqlHelper;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.citygml.writer.CityJSONFormatOptions;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.operation.exporter.Exporter;
import org.citydb.query.builder.sql.SqlBuildOptions;
import org.citydb.query.builder.sql.SqlQueryBuilder;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.query.Select;
import org.citygml4j.cityjson.adapter.appearance.serializer.AppearanceSerializer;
import org.citygml4j.cityjson.adapter.geometry.serializer.GeometrySerializer;
import org.citygml4j.cityjson.model.CityJSONVersion;
import picocli.CommandLine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.IdentityHashMap;
import java.util.Map;

@CommandLine.Command(
        name = "cityjson",
        description = "Export data in CityJSON format.")
public class CityJSONExportCommand extends ExportController {
    @CommandLine.Option(names = {"-v", "--cityjson-version"}, required = true, defaultValue = "2.0",
            description = "CityJSON version: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private CityJSONVersion version;

    @CommandLine.Option(names = "--no-json-lines", negatable = true, defaultValue = "true",
            description = "Write output as CityJSON Text Sequence in JSON lines format (default: ${DEFAULT-VALUE}). " +
                    "This option requires CityJSON 1.1 or later.")
    private boolean jsonLines;

    @CommandLine.Option(names = "--pretty-print",
            description = "Format and indent output file.")
    private Boolean prettyPrint;

    @CommandLine.Option(names = "--html-safe",
            description = "Write JSON that is safe to embed into HTML.")
    private Boolean htmlSafe;

    @CommandLine.Option(names = "--vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for geometry vertices (default: ${DEFAULT-VALUE}).")
    private int vertexPrecision = GeometrySerializer.DEFAULT_VERTEX_PRECISION;

    @CommandLine.Option(names = "--template-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for template vertices (default: ${DEFAULT-VALUE}).")
    private int templatePrecision = GeometrySerializer.DEFAULT_TEMPLATE_PRECISION;

    @CommandLine.Option(names = "--texture-vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVertexPrecision = AppearanceSerializer.DEFAULT_TEXTURE_VERTEX_PRECISION;

    @CommandLine.Option(names = "--no-transform-coordinates", negatable = true, defaultValue = "true",
            description = "Transform coordinates to integer values when exporting in " +
                    "CityJSON 1.0 (default: ${DEFAULT-VALUE}).")
    private boolean transformCoordinates;

    @CommandLine.Option(names = "--replace-templates",
            description = "Replace template geometries with real coordinates.")
    private Boolean replaceTemplateGeometries;

    @CommandLine.Option(names = "--no-material-defaults", negatable = true, defaultValue = "true",
            description = "Use CityGML default values for material properties (default: ${DEFAULT-VALUE}).")
    private boolean useMaterialDefaults;

    @CommandLine.ArgGroup(exclusive = false, order = ARG_GROUP_ORDER,
            heading = "Upgrade options for CityGML 2.0 and 1.0:%n")
    private UpgradeOptions upgradeOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    private volatile boolean shouldRun = true;
    private Throwable exception;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityJSONAdapter.class);
    }

    @Override
    protected OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions) throws ExecutionException {
        CityJSONFormatOptions options;
        try {
            options = formatOptions.get(CityJSONFormatOptions.class);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get CityJSON format options from config.", e);
        }

        if (options != null) {
            if (Command.hasMatchedOption("--cityjson-version", commandSpec)) {
                options.setVersion(version);
            }

            if (Command.hasMatchedOption("--no-json-lines", commandSpec)) {
                options.setJsonLines(jsonLines);
            }

            if (Command.hasMatchedOption("--vertex-precision", commandSpec)) {
                options.setVertexPrecision(vertexPrecision);
            }

            if (Command.hasMatchedOption("--template-precision", commandSpec)) {
                options.setTemplatePrecision(templatePrecision);
            }

            if (Command.hasMatchedOption("--texture-vertex-precision", commandSpec)) {
                options.setTextureVertexPrecision(textureVertexPrecision);
            }

            if (Command.hasMatchedOption("--no-transform-coordinates", commandSpec)) {
                options.setTransformCoordinates(transformCoordinates);
            }

            if (Command.hasMatchedOption("--no-material-defaults", commandSpec)) {
                options.setUseMaterialDefaults(useMaterialDefaults);
            }
        } else {
            options = new CityJSONFormatOptions()
                    .setVersion(version)
                    .setJsonLines(jsonLines)
                    .setVertexPrecision(vertexPrecision)
                    .setTemplatePrecision(templatePrecision)
                    .setTextureVertexPrecision(textureVertexPrecision)
                    .setTransformCoordinates(transformCoordinates)
                    .setUseMaterialDefaults(useMaterialDefaults);
        }

        if (prettyPrint != null) {
            options.setPrettyPrint(prettyPrint);
        }

        if (htmlSafe != null) {
            options.setHtmlSafe(htmlSafe);
        }

        if (replaceTemplateGeometries != null) {
            options.setReplaceTemplateGeometries(replaceTemplateGeometries);
        }

        if (upgradeOptions != null && upgradeOptions.getUseLod4AsLod3() != null) {
            options.setUseLod4AsLod3(upgradeOptions.getUseLod4AsLod3());
        }

        return options;
    }

    @Override
    protected void initialize(ExportOptions exportOptions, WriteOptions writeOptions, DatabaseManager databaseManager) throws ExecutionException {
        try {
            if (databaseManager.getAdapter().getGeometryAdapter().hasImplicitGeometries()) {
                logger.info("Retrieving global template geometries...");
                Map<ImplicitGeometry, String> globalTemplates = new IdentityHashMap<>();
                Exporter exporter = Exporter.newInstance();
                SqlHelper helper = databaseManager.getAdapter().getSchemaAdapter().getSqlHelper();

                Select featureQuery = SqlQueryBuilder.of(databaseManager.getAdapter())
                        .build(getQuery(exportOptions), SqlBuildOptions.defaults().omitDistinct(true));
                SqlObject query = databaseManager.getAdapter().getSchemaAdapter()
                        .getRecursiveImplicitGeometryQuery(featureQuery);

                try (Connection connection = databaseManager.getAdapter().getPool().getConnection();
                     PreparedStatement stmt = helper.prepareStatement(query, connection);
                     ResultSet rs = stmt.executeQuery()) {
                    exporter.startSession(databaseManager.getAdapter(), exportOptions);
                    while (shouldRun && rs.next()) {
                        long id = rs.getLong("id");
                        String lod = rs.getString("lod");

                        exporter.exportImplicitGeometry(id).whenComplete((implicitGeometry, e) -> {
                            if (implicitGeometry != null) {
                                globalTemplates.put(implicitGeometry, lod);
                            } else {
                                shouldRun = false;
                                exception = e;
                            }
                        });
                    }
                } finally {
                    exporter.closeSession();
                }

                if (exception != null) {
                    throw exception;
                } else if (!globalTemplates.isEmpty()) {
                    CityJSONFormatOptions options = writeOptions.getFormatOptions()
                            .getOrElse(CityJSONFormatOptions.class, CityJSONFormatOptions::new);
                    globalTemplates.forEach(options::addGlobalTemplate);
                }
            }
        } catch (Throwable e) {
            throw new ExecutionException("Failed to process global template geometries.", e);
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (version == CityJSONVersion.v1_0) {
            jsonLines = false;
        }

        if (jsonLines && prettyPrint != null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --json-lines and --pretty-print are mutually exclusive (specify only one)");
        }

        if (!transformCoordinates && version != CityJSONVersion.v1_0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --no-transform-coordinates can only be used with CityJSON 1.0");
        }
    }
}
