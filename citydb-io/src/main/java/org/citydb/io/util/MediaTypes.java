/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.util;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class MediaTypes {
    private static final MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

    public static boolean isEqualOrSubMediaType(MediaType candidate, MediaType superType) {
        if (candidate.getSubtype().endsWith("+json")) {
            candidate = MediaType.application("json");
        }

        while (candidate != null) {
            if (candidate.equals(superType) || registry.getAliases(candidate).contains(superType)) {
                return true;
            } else {
                candidate = registry.getSupertype(candidate);
            }
        }

        return false;
    }
}
