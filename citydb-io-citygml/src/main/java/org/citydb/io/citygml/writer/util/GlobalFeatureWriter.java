/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.writer.util;

import org.citydb.io.writer.WriteException;
import org.citygml4j.core.model.core.AbstractFeature;

public interface GlobalFeatureWriter {
    void write(AbstractFeature feature) throws WriteException;
}
