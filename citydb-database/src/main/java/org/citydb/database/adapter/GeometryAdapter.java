/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.database.adapter;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.geometry.GeometryBuilder;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.geometry.PropertiesBuilder;
import org.citydb.model.geometry.Geometry;

public abstract class GeometryAdapter {
    protected final DatabaseAdapter adapter;
    private final GeometryBuilder geometryBuilder = new GeometryBuilder();
    private final PropertiesBuilder propertiesBuilder = new PropertiesBuilder();

    protected GeometryAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public abstract int getGeometrySQLType();
    public abstract String getGeometryTypeName();
    public abstract Geometry<?> getGeometry(Object geometryObject) throws GeometryException;
    public abstract Object getGeometry(Geometry<?> geometry, boolean force3D) throws GeometryException;

    public Object getGeometry(Geometry<?> geometry) throws GeometryException {
        return getGeometry(geometry, true);
    }

    public Geometry<?> buildGeometry(Object geometryObject, JSONObject properties) throws GeometryException {
        return geometryObject != null ?
                geometryBuilder.buildGeometry(getGeometry(geometryObject), properties) :
                null;
    }

    public JSONObject buildGeometryProperties(Geometry<?> geometry) {
        return propertiesBuilder.buildProperties(geometry);
    }
}
