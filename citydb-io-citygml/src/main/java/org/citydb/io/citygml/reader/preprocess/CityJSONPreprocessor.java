/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

public class CityJSONPreprocessor {
    private final ImplicitGeometryResolver implicitGeometryResolver;
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();

    public CityJSONPreprocessor() {
        implicitGeometryResolver = new ImplicitGeometryResolver();
    }

    public ImplicitGeometryResolver getImplicitGeometryResolver() {
        return implicitGeometryResolver;
    }

    public void processGlobalObjects(AbstractFeature feature) {
        implicitGeometryResolver.collectImplicitGeometries(feature);
    }

    public void process(AbstractFeature feature) {
        referenceResolver.resolveReferences(feature);
        implicitGeometryResolver.removeTemplateGeometries(feature);
    }
}
