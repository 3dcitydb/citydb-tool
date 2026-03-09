/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.util;

import org.citygml4j.cityjson.model.metadata.ReferenceSystem;
import org.citygml4j.cityjson.reader.CityJSONReadException;
import org.citygml4j.cityjson.reader.CityJSONReader;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.reader.FeatureInfo;

public class FileMetadata {
    private CityGMLVersion version;
    private String encoding;
    private String srsName;

    private FileMetadata() {
    }

    public static FileMetadata of(CityGMLReader reader) throws CityGMLReadException {
        FileMetadata metadata = new FileMetadata();
        metadata.encoding = reader.getEncoding();

        if (reader.hasNext()) {
            metadata.version = CityGMLVersionHelper.getInstance().getCityGMLVersion(reader.getNamespaces());
            FeatureInfo featureInfo = reader.getParentInfo();
            if (featureInfo != null
                    && featureInfo.getBoundedBy() != null
                    && featureInfo.getBoundedBy().isSetEnvelope()) {
                metadata.srsName = featureInfo.getBoundedBy().getEnvelope().getSrsName();
            }
        }

        return metadata;
    }

    public static FileMetadata of(CityJSONReader reader) throws CityJSONReadException {
        FileMetadata metadata = new FileMetadata();

        if (reader.hasNext()) {
            if (reader.getMetadata() != null
                    && reader.getMetadata().getReferenceSystem() != null) {
                ReferenceSystem referenceSystem = reader.getMetadata().getReferenceSystem();
                metadata.srsName = referenceSystem.getAuthority() + ":" + referenceSystem.getCode();
            }
        }

        return metadata;
    }

    public CityGMLVersion getVersion() {
        return version;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getSrsName() {
        return srsName;
    }
}
