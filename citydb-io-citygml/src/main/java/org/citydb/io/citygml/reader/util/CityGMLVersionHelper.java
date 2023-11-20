/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.xmlobjects.xml.Namespaces;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CityGMLVersionHelper {
    private static CityGMLVersionHelper instance;
    private final Map<String, CityGMLVersion> versions = new HashMap<>();

    private CityGMLVersionHelper() {
    }

    public static synchronized CityGMLVersionHelper getInstance() {
        if (instance == null) {
            instance = new CityGMLVersionHelper();
            for (CityGMLModules context : CityGMLModules.all()) {
                for (Module module : context.getModules()) {
                    instance.versions.putIfAbsent(module.getNamespaceURI(), context.getCityGMLVersion());
                }
            }
        }

        return instance;
    }

    public CityGMLVersion getCityGMLVersion(String namespaceURI) {
        return versions.get(namespaceURI);
    }

    public CityGMLVersion getCityGMLVersion(Namespaces namespaces) {
        return namespaces != null ?
                namespaces.get().stream()
                        .filter(CityGMLModules::isCityGMLNamespace)
                        .map(versions::get)
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null) :
                null;
    }
}
