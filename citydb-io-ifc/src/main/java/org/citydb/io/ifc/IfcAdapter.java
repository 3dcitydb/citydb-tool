package org.citydb.io.ifc;

import org.citydb.core.file.InputFile;
import org.citydb.io.FileFormat;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@FileFormat(name = "IFC",
        mediaType = "application/x-step",
        fileExtensions = {".ifc"})
public class IfcAdapter implements IOAdapter {

    @Override
    public void initialize(ClassLoader loader) throws IOAdapterException {
        // No initialization needed
    }

    @Override
    public boolean canRead(InputFile file) {
        try (InputStream stream = file.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("ISO-10303-21") || line.contains("FILE_SCHEMA")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Not an IFC file
        }
        return false;
    }

    @Override
    public FeatureReader createReader(InputFile file, ReadOptions options) throws ReadException {
        return new IfcReader(file, options);
    }
}
