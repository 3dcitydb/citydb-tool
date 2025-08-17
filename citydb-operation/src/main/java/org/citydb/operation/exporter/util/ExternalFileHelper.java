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

package org.citydb.operation.exporter.util;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.citydb.core.file.OutputFile;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;

public class ExternalFileHelper {
    private final OutputFile outputFile;
    private String outputFolder;
    private String fileNamePrefix;
    private boolean createUniqueFileNames;
    private int numBuckets;

    private boolean useBuckets;
    private boolean[] isCreated = new boolean[1];

    private ExternalFileHelper(ExportHelper helper) {
        outputFile = helper.getOptions().getOutputFile();
    }

    public static ExternalFileHelper newInstance(ExportHelper helper) {
        return new ExternalFileHelper(helper);
    }

    public ExternalFileHelper withRelativeOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
        return this;
    }

    public ExternalFileHelper withFileNamePrefix(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
        return this;
    }

    public ExternalFileHelper createUniqueFileNames(boolean createUniqueFileNames) {
        this.createUniqueFileNames = createUniqueFileNames;
        return this;
    }

    public ExternalFileHelper withNumberOfBuckets(int numBuckets) {
        this.numBuckets = numBuckets;
        useBuckets = numBuckets > 0;
        isCreated = new boolean[Math.max(1, numBuckets)];
        return this;
    }

    public ExternalFile createExternalFile(long id, String uri, String mimeType) throws ExportException {
        String fileName = createUniqueFileNames || uri == null ?
                getFileNamePrefix() + id + getFileExtension(uri, mimeType) :
                getFileNamePrefix() + uri;

        String path;
        int index;
        if (useBuckets) {
            index = (int) Math.abs(id % numBuckets);
            path = (outputFolder != null ? outputFolder + "/" : "") + (index + 1);
        } else {
            index = 0;
            path = outputFolder;
        }

        if (path != null && !isCreated[index]) {
            try {
                outputFile.createDirectories(path);
                isCreated[index] = true;
            } catch (Exception e) {
                throw new ExportException("Failed to create output folder '" + path + "'.", e);
            }
        }

        return ExternalFile.of((path != null ? path + "/" : "") + fileName);
    }

    private String getFileNamePrefix() {
        return fileNamePrefix != null ? fileNamePrefix : "";
    }

    private String getFileExtension(String uri, String mimeType) {
        if (uri != null) {
            int index = uri.lastIndexOf(".");
            if (index > 0) {
                return uri.substring(index);
            }
        }

        if (mimeType != null) {
            try {
                MimeType registeredType = MimeTypes.getDefaultMimeTypes().getRegisteredMimeType(mimeType);
                if (registeredType != null) {
                    return registeredType.getExtension();
                }
            } catch (MimeTypeException e) {
                //
            }
        }

        return "";
    }
}
