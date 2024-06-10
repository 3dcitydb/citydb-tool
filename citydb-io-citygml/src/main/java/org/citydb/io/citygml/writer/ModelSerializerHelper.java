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

package org.citydb.io.citygml.writer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.config.common.SpatialReference;
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.io.citygml.CityGMLAdapterContext;
import org.citydb.io.citygml.adapter.address.AddressAdapter;
import org.citydb.io.citygml.adapter.address.AddressPropertyAdapter;
import org.citydb.io.citygml.adapter.appearance.serializer.AppearanceHelper;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryAdapter;
import org.citydb.io.citygml.adapter.generics.*;
import org.citydb.io.citygml.adapter.geometry.serializer.GeometryHelper;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.options.AddressMode;
import org.citydb.io.citygml.writer.preprocess.Preprocessor;
import org.citydb.io.citygml.writer.util.CityGMLVersionHelper;
import org.citydb.io.citygml.writer.util.GlobalFeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.Child;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.MultiLineString;
import org.citydb.model.geometry.SolidCollection;
import org.citydb.model.geometry.SurfaceCollection;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.property.*;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AddressProperty;
import org.citygml4j.core.model.core.*;
import org.xmlobjects.XMLObjects;
import org.xmlobjects.gml.model.base.*;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.aggregates.MultiCurve;
import org.xmlobjects.gml.model.geometry.aggregates.MultiPoint;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSolid;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.complexes.CompositeSolid;
import org.xmlobjects.gml.model.geometry.complexes.CompositeSurface;
import org.xmlobjects.gml.model.geometry.primitives.*;
import org.xmlobjects.stream.XMLReadException;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.stream.XMLReaderFactory;

import java.io.StringReader;
import java.util.*;

public class ModelSerializerHelper {
    private final Logger logger = LoggerManager.getInstance().getLogger(ModelSerializerHelper.class);
    private final GlobalFeatureWriter writer;
    private final PersistentMapStore store;
    private final CityGMLAdapterContext context;
    private final AppearanceHelper appearanceHelper;
    private final GeometryHelper geometryHelper;
    private final Preprocessor preprocessor;
    private final Set<String> addressIdCache = new HashSet<>();
    private final Set<String> geometryIdCache = new HashSet<>();
    private final List<AbstractFeature> globalFeatures = new ArrayList<>();
    private final Map<Class<?>, ModelSerializer<?, ?>> serializerCache = new IdentityHashMap<>();

    private XMLReaderFactory factory;
    private boolean failFast;
    private CityGMLVersion version;
    private CityGMLVersionHelper versionHelper;
    private String srsName;
    private boolean mapLod0RoofEdge;
    private boolean mapLod1MultiSurfaces;
    private AddressMode addressMode;

    ModelSerializerHelper(GlobalFeatureWriter writer, PersistentMapStore store, CityGMLAdapterContext context) {
        this.writer = Objects.requireNonNull(writer, "The global feature writer must not be null.");
        this.store = Objects.requireNonNull(store, "The persistent map store must not be null.");
        this.context = Objects.requireNonNull(context, "CityGML adapter context must not be null.");

        appearanceHelper = new AppearanceHelper(this);
        geometryHelper = new GeometryHelper(this);
        preprocessor = new Preprocessor();
    }

    ModelSerializerHelper initialize(WriteOptions options, CityGMLFormatOptions formatOptions) {
        failFast = options.isFailFast();
        version = formatOptions.getVersion();
        versionHelper = CityGMLVersionHelper.of(version);
        srsName = options.getSpatialReference().flatMap(SpatialReference::getURI).orElse(null);
        mapLod0RoofEdge = version == CityGMLVersion.v3_0 && formatOptions.isMapLod0RoofEdge();
        mapLod1MultiSurfaces = version == CityGMLVersion.v3_0 && formatOptions.isMapLod1MultiSurfaces();
        preprocessor.checkForDeprecatedLod4Geometry(version == CityGMLVersion.v3_0 && formatOptions.isUseLod4AsLod3());
        addressMode = formatOptions.getAddressMode();
        return this;
    }

    ModelSerializerHelper initialize(WriteOptions options, CityJSONFormatOptions formatOptions) {
        return initialize(options, new CityGMLFormatOptions()
                .setVersion(CityGMLVersion.v3_0)
                .setMapLod0RoofEdge(true)
                .setMapLod1MultiSurfaces(true)
                .setUseLod4AsLod3(formatOptions.isUseLod4AsLod3())
                .setAddressMode(AddressMode.COLUMNS_FIRST));
    }

    public CityGMLAdapterContext getContext() {
        return context;
    }

    public CityGMLVersion getCityGMLVersion() {
        return version;
    }

    public String getSrsName() {
        return srsName;
    }

    public boolean isUseLod4AsLod3() {
        return preprocessor.hasDeprecatedLod4Geometry();
    }

    public boolean isMapLod0RoofEdge() {
        return mapLod0RoofEdge;
    }

    public boolean isMapLod1MultiSurfaces() {
        return mapLod1MultiSurfaces;
    }

    public AddressMode getAddressMode() {
        return addressMode;
    }

    public void logOrThrow(Level level, String message, Throwable cause) throws ModelSerializeException {
        if (!failFast) {
            logger.log(level, message, cause);
        } else {
            throw new ModelSerializeException(message, cause);
        }
    }

    public void logOrThrow(Level level, String message) throws ModelSerializeException {
        logOrThrow(level, message, null);
    }

    public String formatMessage(Feature feature, String message) {
        return getObjectSignature(feature) + ": " + message;
    }

    public String getObjectSignature(Feature feature) {
        String objectId = feature.getObjectId().orElse(null);
        Long id = feature.getDescriptor().map(FeatureDescriptor::getId).orElse(null);
        return feature.getFeatureType().getLocalName() +
                (objectId != null ? " '" + objectId + "'" : "") +
                (id != null ? " (ID: " + id + ")" : "");
    }

    public <K, V> Map<K, V> getOrCreatePersistentMap(String name) {
        return store.getOrCreateMap(name);
    }

    public boolean isSupportedByCityGMLVersion(Feature feature) {
        return feature != null && versionHelper.isSupported(feature);
    }

    public boolean isSupportedByCityGMLVersion(FeatureProperty property) {
        if (property != null) {
            Feature feature = property.getObject().orElse(null);
            return feature != null ?
                    isSupportedByCityGMLVersion(feature) :
                    property.getReference().isPresent();
        } else {
            return false;
        }
    }

    public boolean lookupAndPut(Feature feature) {
        String objectId = feature.getObjectId().orElse(null);
        return objectId != null && getOrCreatePersistentMap("features").putIfAbsent(objectId, true) != null;
    }

    public boolean lookupAndPut(Appearance appearance) {
        String objectId = appearance.getObjectId().orElse(null);
        return objectId != null && getOrCreatePersistentMap("appearances").putIfAbsent(objectId, true) != null;
    }

    public boolean lookupAndPut(org.citydb.model.address.Address address) {
        String objectId = address.getObjectId().orElse(null);
        return objectId != null && !addressIdCache.add(objectId);
    }

    public boolean lookupAndPut(Geometry<?> geometry) {
        String objectId = geometry.getObjectId().orElse(null);
        return objectId != null && !geometryIdCache.add(objectId);
    }

    public boolean lookupAndPut(org.citydb.model.geometry.ImplicitGeometry implicitGeometry) {
        Geometry<?> geometry = implicitGeometry.getGeometry().orElse(null);
        if (geometry != null) {
            String objectId = geometry.getObjectId().orElse(null);
            if (objectId != null) {
                boolean isExported = getOrCreatePersistentMap("implicit-geometries")
                        .putIfAbsent(objectId, true) != null;
                return !geometryIdCache.add(objectId) || isExported;
            }

            return false;
        } else {
            return true;
        }
    }

    public ExternalFile lookupExternalFile(String objectId) {
        return objectId != null ? preprocessor.lookupExternalFile(objectId) : null;
    }

    public void writeAsGlobalFeature(AbstractFeature feature) {
        globalFeatures.add(feature);
    }

    public AbstractGenericAttribute<?> getGenericAttribute(Attribute source) throws ModelSerializeException {
        if (source.hasDataType(DataType.CODE)) {
            return getAttribute(source, CodeAttributeAdapter.class);
        } else if (source.hasDataType(DataType.TIMESTAMP)) {
            return getAttribute(source, DateAttributeAdapter.class);
        } else if (source.hasDataType(DataType.DOUBLE)) {
            return getAttribute(source, DoubleAttributeAdapter.class);
        } else if (source.hasDataType(DataType.INTEGER)) {
            return getAttribute(source, IntAttributeAdapter.class);
        } else if (source.hasDataType(DataType.MEASURE)) {
            return getAttribute(source, MeasureAttributeAdapter.class);
        } else if (source.hasDataType(DataType.STRING)) {
            return getAttribute(source, StringAttributeAdapter.class);
        } else if (source.hasDataType(DataType.URI)) {
            return getAttribute(source, UriAttributeAdapter.class);
        } else if (source.hasDataType(DataType.GENERIC_ATTRIBUTE_SET)) {
            return getAttribute(source, GenericAttributeSetAdapter.class);
        } else {
            return null;
        }
    }

    public void addStandardObjectClassifiers(Feature source, StandardObjectClassifier target, String namespace) throws ModelSerializeException {
        Attribute classifier = source.getAttributes().getFirst(Name.of("class", namespace)).orElse(null);
        if (classifier != null) {
            target.setClassifier(buildObject(classifier, getOrCreateSerializer(CodeAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("function", namespace))) {
            target.getFunctions().add(buildObject(attribute, getOrCreateSerializer(CodeAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("usage", namespace))) {
            target.getUsages().add(buildObject(attribute, getOrCreateSerializer(CodeAdapter.class)));
        }
    }

    public <T> T getAttribute(Attribute source, Class<? extends ModelSerializer<Attribute, T>> type) throws ModelSerializeException {
        return buildObject(source, type);
    }

    public org.citygml4j.core.model.appearance.Appearance getAppearance(Appearance source) throws ModelSerializeException {
        return appearanceHelper.getAppearance(source);
    }

    public <T extends AbstractAssociation<? extends AbstractAppearance>> T getAppearanceProperty(AppearanceProperty source, Class<? extends ModelSerializer<AppearanceProperty, T>> type) throws ModelSerializeException {
        return buildObject(source, type);
    }

    public AbstractGeometry getGeometry(Geometry<?> source, boolean force3D) {
        return geometryHelper.getGeometry(source, force3D);
    }

    public AbstractGeometry getGeometry(Geometry<?> source) {
        return getGeometry(source, true);
    }

    public Point getPoint(org.citydb.model.geometry.Point source, boolean force3D) {
        return geometryHelper.getPoint(source, force3D);
    }

    public Point getPoint(org.citydb.model.geometry.Point source) {
        return getPoint(source, true);
    }

    public MultiPoint getMultiPoint(org.citydb.model.geometry.MultiPoint source, boolean force3D) {
        return geometryHelper.getMultiPoint(source, force3D);
    }

    public MultiPoint getMultiPoint(org.citydb.model.geometry.MultiPoint source) {
        return getMultiPoint(source, true);
    }

    public MultiPoint getMultiPoint(org.citydb.model.geometry.Point source, boolean force3D) {
        return getMultiPoint(org.citydb.model.geometry.MultiPoint.of(List.of(source)), force3D);
    }

    public MultiPoint getMultiPoint(org.citydb.model.geometry.Point source) {
        return getMultiPoint(source, true);
    }

    public LineString getLineString(org.citydb.model.geometry.LineString source, boolean force3D) {
        return geometryHelper.getLineString(source, force3D);
    }

    public LineString getLineString(org.citydb.model.geometry.LineString source) {
        return getLineString(source, true);
    }

    public MultiCurve getMultiCurve(MultiLineString source, boolean force3D) {
        return geometryHelper.getMultiCurve(source, force3D);
    }

    public MultiCurve getMultiCurve(MultiLineString source) {
        return getMultiCurve(source, true);
    }

    public MultiCurve getMultiCurve(org.citydb.model.geometry.LineString source, boolean force3D) {
        return getMultiCurve(MultiLineString.of(List.of(source)), force3D);
    }

    public MultiCurve getMultiCurve(org.citydb.model.geometry.LineString source) {
        return getMultiCurve(source, true);
    }

    public Polygon getPolygon(org.citydb.model.geometry.Polygon source, boolean force3D) {
        return geometryHelper.getPolygon(source, force3D);
    }

    public Polygon getPolygon(org.citydb.model.geometry.Polygon source) {
        return getPolygon(source, true);
    }

    public CompositeSurface getCompositeSurface(org.citydb.model.geometry.CompositeSurface source, boolean force3D) {
        return geometryHelper.getCompositeSurface(source, force3D);
    }

    public CompositeSurface getCompositeSurface(org.citydb.model.geometry.CompositeSurface source) {
        return getCompositeSurface(source, true);
    }

    public CompositeSurface getCompositeSurface(org.citydb.model.geometry.Polygon source, boolean force3D) {
        return getCompositeSurface(org.citydb.model.geometry.CompositeSurface.of(List.of(source)), force3D);
    }

    public CompositeSurface getCompositeSurface(org.citydb.model.geometry.Polygon source) {
        return getCompositeSurface(source, true);
    }

    public TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.TriangulatedSurface source, boolean force3D) {
        return geometryHelper.getTriangulatedSurface(source, force3D);
    }

    public TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.TriangulatedSurface source) {
        return getTriangulatedSurface(source, true);
    }

    public TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.Polygon source, boolean force3D) {
        return getTriangulatedSurface(org.citydb.model.geometry.TriangulatedSurface.of(List.of(source)), force3D);
    }

    public TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.Polygon source) {
        return getTriangulatedSurface(source, true);
    }

    public MultiSurface getMultiSurface(SurfaceCollection<?> source, boolean force3D) {
        return geometryHelper.getMultiSurface(source, force3D);
    }

    public MultiSurface getMultiSurface(SurfaceCollection<?> source) {
        return getMultiSurface(source, true);
    }

    public MultiSurface getMultiSurface(org.citydb.model.geometry.Polygon source, boolean force3D) {
        return getMultiSurface(org.citydb.model.geometry.MultiSurface.of(List.of(source)), force3D);
    }

    public MultiSurface getMultiSurface(org.citydb.model.geometry.Polygon source) {
        return getMultiSurface(source, true);
    }

    public Solid getSolid(org.citydb.model.geometry.Solid source) {
        return geometryHelper.getSolid(source);
    }

    public CompositeSolid getCompositeSolid(org.citydb.model.geometry.CompositeSolid source) {
        return geometryHelper.getCompositeSolid(source);
    }

    public CompositeSolid getCompositeSolid(org.citydb.model.geometry.Solid source) {
        return getCompositeSolid(org.citydb.model.geometry.CompositeSolid.of(List.of(source)));
    }

    public MultiSolid getMultiSolid(SolidCollection<?> source) {
        return geometryHelper.getMultiSolid(source);
    }

    public MultiSolid getMultiSolid(org.citydb.model.geometry.Solid source) {
        return getMultiSolid(org.citydb.model.geometry.MultiSolid.of(List.of(source)));
    }

    public <T extends org.xmlobjects.gml.model.geometry.GeometryProperty<?>> T getGeometryProperty(Geometry<?> source, Class<? extends ModelSerializer<GeometryProperty, T>> type) throws ModelSerializeException {
        return getGeometryProperty(GeometryProperty.of(Name.of(""), source), type);
    }

    public <T extends org.xmlobjects.gml.model.geometry.GeometryProperty<?>> T getGeometryProperty(GeometryProperty source, Class<? extends ModelSerializer<GeometryProperty, T>> type) throws ModelSerializeException {
        return buildObject(source, type);
    }

    public ImplicitGeometry getImplicitGeometry(ImplicitGeometryProperty source, boolean force3D) throws ModelSerializeException {
        ImplicitGeometry target = geometryHelper.getImplicitGeometry(source, force3D);
        return target != null ?
                buildObject(source, target, getOrCreateSerializer(ImplicitGeometryAdapter.class)) :
                null;
    }

    public ImplicitGeometry getImplicitGeometry(ImplicitGeometryProperty source) throws ModelSerializeException {
        return getImplicitGeometry(source, true);
    }

    public <T extends AbstractAssociation<? extends ImplicitGeometry>> T getImplicitGeometryProperty(ImplicitGeometryProperty source, Class<? extends ModelSerializer<ImplicitGeometryProperty, T>> type) throws ModelSerializeException {
        return buildObject(source, type);
    }

    public Address getAddress(org.citydb.model.address.Address source) throws ModelSerializeException {
        return buildObject(source, AddressAdapter.class);
    }

    public AddressProperty getAddressProperty(org.citydb.model.property.AddressProperty source) throws ModelSerializeException {
        return buildObject(source, AddressPropertyAdapter.class);
    }

    public <T extends AbstractAssociation<? extends AbstractGML>> T getObjectProperty(FeatureProperty source, Class<? extends ModelSerializer<FeatureProperty, T>> type) throws ModelSerializeException {
        T target = buildObject(source, type);
        if (target instanceof AbstractInlineOrByReferenceProperty<?> property) {
            if (property.getObject() == null && property.getHref() == null) {
                return null;
            }
        } else if (target instanceof AbstractInlineProperty<?> property && property.getObject() == null) {
            return null;
        } else if (target instanceof AbstractArrayProperty<?> property && !property.isSetObjects()) {
            return null;
        }

        return target;
    }

    public <T extends AbstractGML> T getObject(Feature source, Class<T> type) throws ModelSerializeException {
        if (source != null && isSupportedByCityGMLVersion(source)) {
            ModelSerializer<Feature, T> serializer = context.getSerializer(source.getFeatureType(),
                    Feature.class, type);
            if (serializer != null) {
                return buildObject(source, serializer);
            } else {
                logOrThrow(Level.DEBUG, formatMessage(source,
                        "Skipping feature because the feature type is not supported."));
            }
        }

        return null;
    }

    AbstractFeature getTopLevelFeature(Feature source) throws ModelSerializeException {
        try {
            preprocessor.process(source);
            AbstractFeature feature = getObject(source, AbstractFeature.class);
            if (feature != null) {
                appearanceHelper.processTargets(feature);
                writeGlobalFeatures();
            }

            return feature;
        } finally {
            clear();
        }
    }

    ImplicitGeometry getImplicitGeometry(org.citydb.model.geometry.ImplicitGeometry source) throws ModelSerializeException {
        try {
            ImplicitGeometry implicitGeometry = getImplicitGeometry(ImplicitGeometryProperty.of(Name.of(""), source));
            if (implicitGeometry != null) {
                appearanceHelper.processTargets(implicitGeometry);
                if (!globalFeatures.isEmpty()) {
                    globalFeatures.stream()
                            .filter(AbstractAppearance.class::isInstance)
                            .map(AbstractAppearance.class::cast)
                            .map(AbstractAppearanceProperty::new)
                            .forEach(implicitGeometry.getAppearances()::add);
                }
            }

            return implicitGeometry;
        } finally {
            clear();
        }
    }

    private void writeGlobalFeatures() throws ModelSerializeException {
        if (!globalFeatures.isEmpty()) {
            for (AbstractFeature feature : globalFeatures) {
                try {
                    writer.write(feature);
                } catch (WriteException e) {
                    throw new ModelSerializeException("Failed to write feature as global member.", e);
                }
            }
        }
    }

    private <T extends Child, R> R buildObject(T source, Class<? extends ModelSerializer<T, R>> type) throws ModelSerializeException {
        return buildObject(source, getOrCreateSerializer(type));
    }

    private <T extends Child, R> R buildObject(T source, ModelSerializer<T, R> serializer) throws ModelSerializeException {
        return source != null ? buildObject(source, serializer.createObject(source), serializer) : null;
    }

    private <T extends Child, R> R buildObject(T source, R target, ModelSerializer<T, R> serializer) throws ModelSerializeException {
        if (target != null) {
            serializer.serialize(source, target, this);
            serializer.postSerialize(source, target, this);
            return target;
        } else {
            throw new ModelSerializeException("The serializer " + serializer.getClass().getName() +
                    " returned a null object.");
        }
    }

    public <T extends ModelSerializer<?, ?>> T getOrCreateSerializer(Class<T> type) throws ModelSerializeException {
        ModelSerializer<?, ?> cachedSerializer = serializerCache.get(type);
        if (cachedSerializer != null && type.isAssignableFrom(cachedSerializer.getClass())) {
            return type.cast(cachedSerializer);
        } else {
            try {
                T serializer = type.getDeclaredConstructor().newInstance();
                serializerCache.put(type, serializer);
                return serializer;
            } catch (Exception e) {
                throw new ModelSerializeException("The serializer " + type.getName() +
                        " lacks a default constructor.");
            }
        }
    }

    public <T> T fromXML(String source, Class<T> type) {
        if (source != null) {
            XMLObjects xmlObjects = context.getCityGMLContext().getXMLObjects();
            try (XMLReader reader = getOrCreateXMLReaderFactory(xmlObjects)
                    .createReader(new StringReader(source))) {
                return xmlObjects.fromXML(reader, type);
            } catch (Exception e) {
                //
            }
        }

        return null;
    }

    private XMLReaderFactory getOrCreateXMLReaderFactory(XMLObjects xmlObjects) throws XMLReadException {
        if (factory == null) {
            factory = XMLReaderFactory.newInstance(xmlObjects);
        }

        return factory;
    }

    private void clear() {
        preprocessor.clear();
        addressIdCache.clear();
        geometryIdCache.clear();
        globalFeatures.clear();
        appearanceHelper.reset();
    }
}
