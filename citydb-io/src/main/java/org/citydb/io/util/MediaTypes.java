/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
