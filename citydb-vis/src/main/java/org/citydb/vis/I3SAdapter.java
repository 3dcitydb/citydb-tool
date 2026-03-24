/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis;

import org.citydb.core.file.OutputFile;
import org.citydb.io.FileFormat;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.vis.writer.I3SWriter;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;

@FileFormat(name = "I3S",
        mediaType = "application/octet-stream",
        fileExtensions = {".i3s"})
public class I3SAdapter implements IOAdapter {

    @Override
    public void initialize(ClassLoader loader) throws IOAdapterException {
    }

    @Override
    public FeatureWriter createWriter(OutputFile file, WriteOptions options) throws WriteException {
        return new I3SWriter(file, options);
    }
}
