/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
