/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.util;

import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.util.id.DefaultIdCreator;

public class FeatureHelper {

    private FeatureHelper() {
    }

    public static String formatMessage(AbstractGML object, String message) {
        return getObjectSignature(object) + ": " + message;
    }

    public static String getObjectSignature(AbstractGML object) {
        return object.getClass().getSimpleName()
                + " '" + (object.getId() != null ? object.getId() : "unknown ID") + "'";
    }

    public static String getIdFromReference(String reference) {
        if (reference != null) {
            int index = reference.lastIndexOf("#");
            return index != -1 ? reference.substring(index + 1) : reference;
        } else {
            return null;
        }
    }

    public static String createId() {
        return DefaultIdCreator.getInstance().createId();
    }

    public static String getOrCreateId(AbstractGML object) {
        if (object.getId() == null) {
            object.setId(createId());
        }

        return object.getId();
    }
}
