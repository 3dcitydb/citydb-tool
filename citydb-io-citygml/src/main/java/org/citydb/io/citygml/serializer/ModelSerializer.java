/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.serializer;

import org.atteo.classindex.IndexSubclasses;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Child;

@IndexSubclasses
public interface ModelSerializer<T extends Child, R> {
    R createObject(T source) throws ModelSerializeException;

    void serialize(T source, R target, ModelSerializerHelper helper) throws ModelSerializeException;

    default void postSerialize(T source, R target, ModelSerializerHelper helper) throws ModelSerializeException {
    }
}
