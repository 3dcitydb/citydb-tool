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

package org.citydb.operation.exporter.appearance;

import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ParameterizedTextureExporter extends TextureExporter {

    public ParameterizedTextureExporter(ExportHelper helper) throws SQLException {
        super(helper);
    }

    protected ParameterizedTexture doExport(ResultSet rs) throws ExportException, SQLException {
        return doExport(ParameterizedTexture.newInstance(), rs);
    }
}
