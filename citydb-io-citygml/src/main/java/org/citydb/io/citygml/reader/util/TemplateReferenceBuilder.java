/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.util;

import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.xml.adapter.CityGMLBuilderHelper;
import org.citygml4j.xml.adapter.core.ImplicitGeometryAdapter;
import org.xmlobjects.builder.ObjectBuildException;
import org.xmlobjects.gml.adapter.GMLBuilderHelper;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.GMLConstants;
import org.xmlobjects.stream.EventType;
import org.xmlobjects.stream.XMLReadException;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.xml.Attributes;

import javax.xml.namespace.QName;

public class TemplateReferenceBuilder extends ImplicitGeometryAdapter {

    @Override
    public void buildChildObject(ImplicitGeometry object, QName name, Attributes attributes, XMLReader reader) throws ObjectBuildException, XMLReadException {
        if (CityGMLBuilderHelper.isCoreNamespace(name.getNamespaceURI())
                && (name.getLocalPart().equals("relativeGeometry")
                || name.getLocalPart().equals("relativeGMLGeometry"))) {
            GeometryProperty<?> property = new GeometryProperty<>();
            GMLBuilderHelper.buildAssociationAttributes(property, attributes);

            if (property.getHref() == null
                    && reader.hasNext()
                    && reader.nextTag() == EventType.START_ELEMENT) {
                reader.getAttributes().getValue(GMLConstants.GML_3_1_NAMESPACE, "id")
                        .ifPresent(id -> property.setHref("#" + id));
                reader.getAttributes().getValue(GMLConstants.GML_3_2_NAMESPACE, "id")
                        .ifPresent(id -> property.setHref("#" + id));
            }

            object.setRelativeGeometry(property.getHref() != null
                    ? property
                    : new GeometryProperty<>(reader.getObject(AbstractGeometry.class)));
        } else {
            super.buildChildObject(object, name, attributes, reader);
        }
    }
}
