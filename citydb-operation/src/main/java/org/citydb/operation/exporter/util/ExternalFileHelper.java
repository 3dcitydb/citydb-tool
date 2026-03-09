/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;

public class ExternalFileHelper {
    private final OutputFile outputFile;
    private final boolean useAbsoluteResourcePaths;
    private String outputFolder;
    private String fileNamePrefix;
    private boolean createUniqueFileNames;
    private int numBuckets;

    private boolean useBuckets;
    private boolean[] isCreated = new boolean[1];

    private ExternalFileHelper(ExportHelper helper) {
        outputFile = helper.getOptions().getOutputFile();
        useAbsoluteResourcePaths = helper.getOptions().isUseAbsoluteResourcePaths();
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

        path = (path != null ? path + "/" : "") + fileName;
        return !useAbsoluteResourcePaths || outputFile.getFileType() == FileType.ARCHIVE ?
                ExternalFile.of(path) :
                ExternalFile.of(outputFile.getFile().getParent().resolve(path));
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
