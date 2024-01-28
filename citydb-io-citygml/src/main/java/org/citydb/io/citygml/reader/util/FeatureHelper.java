/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
