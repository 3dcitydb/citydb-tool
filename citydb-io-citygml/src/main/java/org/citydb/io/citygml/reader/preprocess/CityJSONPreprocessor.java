/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.io.reader.options.ImplicitGeometryScope;
import org.citydb.io.reader.options.LodOptions;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

public class CityJSONPreprocessor {
    private final ImplicitGeometryResolver implicitGeometryResolver;
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();
    private LodFilter lodFilter;

    public CityJSONPreprocessor() {
        implicitGeometryResolver = new ImplicitGeometryResolver();
    }

    public ImplicitGeometryResolver getImplicitGeometryResolver() {
        return implicitGeometryResolver;
    }

    public CityJSONPreprocessor setImplicitGeometryScope(ImplicitGeometryScope scope) {
        implicitGeometryResolver.withScope(scope);
        return this;
    }

    public CityJSONPreprocessor setLodOptions(LodOptions lodOptions) {
        lodFilter = LodFilter.of(lodOptions);
        return this;
    }

    public CityJSONPreprocessor retainState(boolean retainState) {
        implicitGeometryResolver.retainState(retainState);
        return this;
    }

    public void processGlobalObjects(AbstractFeature feature) {
        implicitGeometryResolver.collectImplicitGeometries(feature);
    }

    public boolean process(AbstractFeature feature) {
        referenceResolver.resolveReferences(feature);
        implicitGeometryResolver.removeTemplateGeometries(feature);
        return lodFilter == null || lodFilter.filter(feature);
    }
}
