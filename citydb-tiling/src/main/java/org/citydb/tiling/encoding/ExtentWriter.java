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

package org.citydb.tiling.encoding;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import org.citydb.config.common.SrsReference;
import org.citydb.model.geometry.Envelope;

import java.lang.reflect.Type;
import java.util.List;

public class ExtentWriter implements ObjectWriter<Envelope> {
    @Override
    public void write(JSONWriter jsonWriter, Object o, Object o1, Type type, long l) {
        if (o instanceof Envelope extent) {
            jsonWriter.startObject();
            jsonWriter.writeName("coordinates");
            jsonWriter.writeColon();
            jsonWriter.write(List.of(extent.getLowerCorner().getX(),
                    extent.getLowerCorner().getY(),
                    extent.getUpperCorner().getX(),
                    extent.getUpperCorner().getY()));

            if (extent.getSRID().isPresent() || extent.getSrsIdentifier().isPresent()) {
                jsonWriter.writeName("srs");
                jsonWriter.writeColon();
                jsonWriter.writeAs(new SrsReference()
                                .setSRID(extent.getSRID().orElse(null))
                                .setIdentifier(extent.getSrsIdentifier().orElse(null)),
                        SrsReference.class);
            }

            jsonWriter.endObject();
        } else {
            jsonWriter.writeNull();
        }
    }
}
