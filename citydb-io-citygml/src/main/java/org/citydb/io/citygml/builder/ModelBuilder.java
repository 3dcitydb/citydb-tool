/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.builder;

import org.atteo.classindex.IndexSubclasses;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.model.common.Child;

@IndexSubclasses
public interface ModelBuilder<T, R extends Child> {
    default R createModel(T source) throws ModelBuildException {
        return null;
    }

    void build(T source, R target, ModelBuilderHelper helper) throws ModelBuildException;
}
