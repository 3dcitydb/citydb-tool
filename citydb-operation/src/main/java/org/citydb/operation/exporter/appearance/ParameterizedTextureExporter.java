/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
