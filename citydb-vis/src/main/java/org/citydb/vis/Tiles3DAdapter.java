/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis;

import org.citydb.core.file.OutputFile;
import org.citydb.io.FileFormat;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.writer.tiles3d.Tiles3DWriter;

@FileFormat(name = "3DTiles",
        mediaType = "application/json",
        fileExtensions = {".3dtiles"})
public class Tiles3DAdapter implements IOAdapter {
    @Override
    public void initialize(ClassLoader loader) throws IOAdapterException {
    }

    @Override
    public FeatureWriter createWriter(OutputFile file, WriteOptions options) throws WriteException {
        return new Tiles3DWriter(file, options);
    }
}
