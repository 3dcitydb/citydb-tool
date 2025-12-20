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

package org.citydb.io.citygml.reader;

import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.file.FileLocator;
import org.citydb.core.file.InputFile;
import org.citydb.io.citygml.CityGMLAdapterContext;
import org.citydb.io.citygml.adapter.address.AddressAdapter;
import org.citydb.io.citygml.adapter.appearance.builder.AppearanceHelper;
import org.citydb.io.citygml.adapter.core.ImplicitGeometryAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.GeometryHelper;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.options.FormatOptions;
import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citydb.io.citygml.reader.util.FileMetadata;
import org.citydb.io.reader.ReadOptions;
import org.citydb.model.address.Address;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.Child;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.common.Reference;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.*;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractAppearance;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.StandardObjectClassifier;
import org.citygml4j.xml.module.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.xmlobjects.XMLObjects;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.base.AbstractInlineProperty;
import org.xmlobjects.gml.model.base.ResolvableAssociation;
import org.xmlobjects.gml.model.basictypes.Code;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.SRSReference;
import org.xmlobjects.stream.XMLWriter;
import org.xmlobjects.stream.XMLWriterFactory;
import org.xmlobjects.xml.Namespaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class ModelBuilderHelper {
    private final Logger logger = LoggerFactory.getLogger(ModelBuilderHelper.class);
    private final InputFile file;
    private final PersistentMapStore store;
    private final CityGMLAdapterContext context;
    private final AppearanceHelper appearanceHelper;
    private final GeometryHelper geometryHelper;
    private final Map<Class<?>, ModelBuilder<?, ?>> builderCache = new IdentityHashMap<>();
    private final Set<String> geometryIdCache = new HashSet<>();

    private CityGMLVersion version;
    private String encoding;
    private String rootSrsName;
    private boolean failFast;
    private boolean computeEnvelopes;
    private boolean includeXALSource;

    ModelBuilderHelper(InputFile file, PersistentMapStore store, CityGMLAdapterContext context) {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.store = Objects.requireNonNull(store, "The persistent map store must not be null.");
        this.context = Objects.requireNonNull(context, "CityGML adapter context must not be null.");

        appearanceHelper = new AppearanceHelper(this);
        geometryHelper = new GeometryHelper(appearanceHelper, this);
    }

    private ModelBuilderHelper doInitialize(FileMetadata metadata, ReadOptions options, FormatOptions<?> formatOptions) {
        version = metadata.getVersion() != null ? metadata.getVersion() : CityGMLVersion.v3_0;
        encoding = metadata.getEncoding() != null ? metadata.getEncoding() : StandardCharsets.UTF_8.name();
        rootSrsName = metadata.getSrsName();
        failFast = options.isFailFast();
        computeEnvelopes = options.isComputeEnvelopes();
        appearanceHelper.initialize(formatOptions);
        return this;
    }

    ModelBuilderHelper initialize(FileMetadata metadata, ReadOptions options, CityGMLFormatOptions formatOptions) {
        includeXALSource = formatOptions.isIncludeXALSource();
        return doInitialize(metadata, options, formatOptions);
    }

    ModelBuilderHelper initialize(FileMetadata metadata, ReadOptions options, CityJSONFormatOptions formatOptions) {
        return doInitialize(metadata, options, formatOptions);
    }

    public InputFile getInputFile() {
        return file;
    }

    public CityGMLAdapterContext getContext() {
        return context;
    }

    public CityGMLVersion getCityGMLVersion() {
        return version;
    }

    public String getEncoding() {
        return encoding;
    }

    public boolean isComputeEnvelopes() {
        return computeEnvelopes;
    }

    public boolean isIncludeXALSource() {
        return includeXALSource;
    }

    public void logOrThrow(Level level, String message, Throwable cause) throws ModelBuildException {
        if (!failFast) {
            logger.atLevel(level).setCause(cause).log(message);
        } else {
            throw new ModelBuildException(message, cause);
        }
    }

    public void logOrThrow(Level level, String message) throws ModelBuildException {
        logOrThrow(level, message, null);
    }

    public String formatMessage(AbstractGML object, String message) {
        return FeatureHelper.formatMessage(object, message);
    }

    public String getObjectSignature(AbstractGML object) {
        return FeatureHelper.getObjectSignature(object);
    }

    public String getIdFromReference(String reference) {
        return FeatureHelper.getIdFromReference(reference);
    }

    public String getInheritedSrsName(SRSReference source) {
        SRSReference reference = source.getInheritedSRSReference();
        return reference.getSrsName() != null ?
                reference.getSrsName() :
                rootSrsName;
    }

    public <K, V> Map<K, V> getOrCreatePersistentMap(String name) {
        return store.getOrCreateMap(name);
    }

    public ExternalFile getExternalFile(String location) throws IOException {
        if (location != null) {
            FileLocator locator = FileLocator.of(file, location);
            return locator.getPath().map(ExternalFile::of)
                    .orElseGet(() -> ExternalFile.of(location));
        }

        return null;
    }

    public boolean lookupAndPut(ExternalFile file) {
        return lookupAndPut(file, null);
    }

    public boolean lookupAndPut(ExternalFile file, String token) {
        String id = file != null ? file.getObjectId().orElse(null) : null;
        return id != null && getOrCreatePersistentMap("external-files")
                .putIfAbsent(token == null ? id : token + id, Boolean.TRUE) != null;
    }

    public boolean lookupAndPut(AbstractGeometry geometry) {
        return geometry.getId() != null && !geometryIdCache.add(geometry.getId());
    }

    @SuppressWarnings("unchecked")
    public <T> Attribute getAttribute(T source) throws ModelBuildException {
        if (source != null) {
            ModelBuilder<T, Attribute> builder = context.getBuilder((Class<T>) source.getClass(), Attribute.class);
            if (builder != null) {
                return buildObject(source, builder.createModel(source), builder);
            }
        }

        return null;
    }

    public <T> Attribute getAttribute(Name name, T source, Class<? extends ModelBuilder<T, Attribute>> type) throws ModelBuildException {
        return source != null ? buildObject(source, Attribute.of(name), getOrCreateBuilder(type)) : null;
    }

    public void addAttribute(Object source, Feature feature) throws ModelBuildException {
        addProperty(getAttribute(source), feature::addAttribute);
    }

    public void addAttribute(Object source, Attribute attribute) throws ModelBuildException {
        addProperty(getAttribute(source), attribute::addProperty);
    }

    public <T> void addAttribute(Name name, T source, Feature feature, Class<? extends ModelBuilder<T, Attribute>> type) throws ModelBuildException {
        addProperty(getAttribute(name, source, type), feature::addAttribute);
    }

    public <T> void addAttribute(Name name, T source, Attribute attribute, Class<? extends ModelBuilder<T, Attribute>> type) throws ModelBuildException {
        addProperty(getAttribute(name, source, type), attribute::addProperty);
    }

    public void addStandardObjectClassifiers(StandardObjectClassifier source, Feature target, String namespace) throws ModelBuildException {
        if (source.getClassifier() != null) {
            addAttribute(Name.of("class", namespace), source.getClassifier(), target, CodeAdapter.class);
        }

        if (source.isSetFunctions()) {
            for (Code function : source.getFunctions()) {
                addAttribute(Name.of("function", namespace), function, target, CodeAdapter.class);
            }
        }

        if (source.isSetUsages()) {
            for (Code usage : source.getUsages()) {
                addAttribute(Name.of("usage", namespace), usage, target, CodeAdapter.class);
            }
        }
    }

    public AppearanceProperty getAppearanceProperty(Name name, AbstractAppearanceProperty source) throws ModelBuildException {
        if (source != null && source.getObject() != null) {
            Appearance appearance = getAppearance(source.getObject());
            if (appearance != null) {
                return AppearanceProperty.of(name, appearance);
            }
        }

        return null;
    }

    public void addAppearance(Name name, AbstractAppearanceProperty source, Feature feature) throws ModelBuildException {
        addProperty(getAppearanceProperty(name, source), feature::addAppearance);
    }

    public void addAppearance(Name name, AbstractAppearanceProperty source, Attribute attribute) throws ModelBuildException {
        addProperty(getAppearanceProperty(name, source), attribute::addProperty);
    }

    public void addAppearance(Name name, AbstractAppearanceProperty source, ImplicitGeometry geometry) throws ModelBuildException {
        addProperty(getAppearanceProperty(name, source), geometry::addAppearance);
    }

    public Appearance getAppearance(AbstractAppearance source) throws ModelBuildException {
        return appearanceHelper.getAppearance(source);
    }

    public GeometryProperty getGeometryProperty(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D) throws ModelBuildException {
        return source != null && source.getObject() != null ?
                getGeometryProperty(name, geometryHelper.getGeometry(source.getObject(), force2D), lod) :
                null;
    }

    public GeometryProperty getGeometryProperty(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod) throws ModelBuildException {
        return getGeometryProperty(name, source, lod, false);
    }

    public GeometryProperty getGeometryProperty(Name name, Geometry<?> geometry, Lod lod) {
        if (geometry != null) {
            GeometryProperty property = GeometryProperty.of(name, geometry);
            if (lod != null && lod != Lod.NONE) {
                property.setLod(lod.getValue());
            }

            return property;
        }

        return null;
    }

    public void addPointGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Feature feature) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getPointGeometry(source, force2D), lod), feature::addGeometry);
    }

    public void addPointGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Feature feature) throws ModelBuildException {
        addPointGeometry(name, source, lod, false, feature);
    }

    public void addPointGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Attribute attribute) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getPointGeometry(source, force2D), lod), attribute::addProperty);
    }

    public void addPointGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Attribute attribute) throws ModelBuildException {
        addPointGeometry(name, source, lod, false, attribute);
    }

    public void addCurveGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Feature feature) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getCurveGeometry(source, force2D), lod), feature::addGeometry);
    }

    public void addCurveGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Feature feature) throws ModelBuildException {
        addCurveGeometry(name, source, lod, false, feature);
    }

    public void addCurveGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Attribute attribute) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getCurveGeometry(source, force2D), lod), attribute::addProperty);
    }

    public void addCurveGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Attribute attribute) throws ModelBuildException {
        addCurveGeometry(name, source, lod, false, attribute);
    }

    public void addSurfaceGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Feature feature) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getSurfaceGeometry(source, force2D), lod), feature::addGeometry);
    }

    public void addSurfaceGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Feature feature) throws ModelBuildException {
        addSurfaceGeometry(name, source, lod, false, feature);
    }

    public void addSurfaceGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Attribute attribute) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getSurfaceGeometry(source, force2D), lod), attribute::addProperty);
    }

    public void addSurfaceGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Attribute attribute) throws ModelBuildException {
        addSurfaceGeometry(name, source, lod, false, attribute);
    }

    public void addSolidGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Feature feature) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getSolidGeometry(source), lod), feature::addGeometry);
    }

    public void addSolidGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Attribute attribute) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getSolidGeometry(source), lod), attribute::addProperty);
    }

    public void addGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Feature feature) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getGeometry(source, force2D), lod), feature::addGeometry);
    }

    public void addGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Feature feature) throws ModelBuildException {
        addGeometry(name, source, lod, false, feature);
    }

    public void addGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, boolean force2D, Attribute attribute) throws ModelBuildException {
        addProperty(getGeometryProperty(name, geometryHelper.getGeometry(source, force2D), lod), attribute::addProperty);
    }

    public void addGeometry(Name name, org.xmlobjects.gml.model.geometry.GeometryProperty<?> source, Lod lod, Attribute attribute) throws ModelBuildException {
        addGeometry(name, source, lod, false, attribute);
    }

    public Geometry<?> getPointGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return geometryHelper.getPointGeometry(source, force2D);
    }

    public Geometry<?> getPointGeometry(AbstractGeometry source) throws ModelBuildException {
        return getPointGeometry(source, false);
    }

    public <T extends Geometry<?>> T getPointGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return geometryHelper.getPointGeometry(source, force2D, type);
    }

    public <T extends Geometry<?>> T getPointGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return getPointGeometry(source, false, type);
    }

    public Geometry<?> getCurveGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return geometryHelper.getCurveGeometry(source, force2D);
    }

    public Geometry<?> getCurveGeometry(AbstractGeometry source) throws ModelBuildException {
        return getCurveGeometry(source, false);
    }

    public <T extends Geometry<?>> T getCurveGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return geometryHelper.getCurveGeometry(source, force2D, type);
    }

    public <T extends Geometry<?>> T getCurveGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return getCurveGeometry(source, false, type);
    }

    public Geometry<?> getSurfaceGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return geometryHelper.getSurfaceGeometry(source, force2D);
    }

    public Geometry<?> getSurfaceGeometry(AbstractGeometry source) throws ModelBuildException {
        return getSurfaceGeometry(source, false);
    }

    public <T extends Geometry<?>> T getSurfaceGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return geometryHelper.getSurfaceGeometry(source, force2D, type);
    }

    public <T extends Geometry<?>> T getSurfaceGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return getSurfaceGeometry(source, false, type);
    }

    public Geometry<?> getSolidGeometry(AbstractGeometry source) throws ModelBuildException {
        return geometryHelper.getSolidGeometry(source);
    }

    public <T extends Geometry<?>> T getSolidGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return geometryHelper.getSolidGeometry(source, type);
    }

    public Geometry<?> getGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return geometryHelper.getGeometry(source, force2D);
    }

    public Geometry<?> getGeometry(AbstractGeometry source) throws ModelBuildException {
        return getGeometry(source, false);
    }

    public <T extends Geometry<?>> T getGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return geometryHelper.getGeometry(source, force2D, type);
    }

    public <T extends Geometry<?>> T getGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return getGeometry(source, false, type);
    }

    public ImplicitGeometryProperty getImplicitGeometryProperty(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod, boolean force2D) throws ModelBuildException {
        return source != null ?
                getImplicitGeometryProperty(name, source.getObject(), lod, force2D) :
                null;
    }

    public ImplicitGeometryProperty getImplicitGeometryProperty(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod) throws ModelBuildException {
        return getImplicitGeometryProperty(name, source, lod, false);
    }

    public ImplicitGeometryProperty getImplicitGeometryProperty(Name name, org.citygml4j.core.model.core.ImplicitGeometry source, Lod lod, boolean force2D) throws ModelBuildException {
        return source != null ?
                getImplicitGeometryProperty(source, geometryHelper.getImplicitGeometry(source, name, force2D), lod) :
                null;
    }

    public ImplicitGeometryProperty getImplicitGeometryProperty(Name name, org.citygml4j.core.model.core.ImplicitGeometry source, Lod lod) throws ModelBuildException {
        return getImplicitGeometryProperty(name, source, lod, false);
    }

    private ImplicitGeometryProperty getImplicitGeometryProperty(org.citygml4j.core.model.core.ImplicitGeometry source, ImplicitGeometryProperty target, Lod lod) throws ModelBuildException {
        if (source != null && target != null) {
            target = buildObject(source, target, getOrCreateBuilder(ImplicitGeometryAdapter.class));
            if (lod != null && lod != Lod.NONE) {
                target.setLod(lod.getValue());
            }

            return target;
        }

        return null;
    }

    public void addImplicitGeometry(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod, boolean force2D, Feature feature) throws ModelBuildException {
        addProperty(getImplicitGeometryProperty(name, source, lod, force2D), feature::addImplicitGeometry);
    }

    public void addImplicitGeometry(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod, Feature feature) throws ModelBuildException {
        addImplicitGeometry(name, source, lod, false, feature);
    }

    public void addImplicitGeometry(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod, boolean force2D, Attribute attribute) throws ModelBuildException {
        addProperty(getImplicitGeometryProperty(name, source, lod, force2D), attribute::addProperty);
    }

    public void addImplicitGeometry(Name name, org.citygml4j.core.model.core.ImplicitGeometryProperty source, Lod lod, Attribute attribute) throws ModelBuildException {
        addImplicitGeometry(name, source, lod, false, attribute);
    }

    public AddressProperty getAddressProperty(Name name, org.citygml4j.core.model.core.AddressProperty source) throws ModelBuildException {
        if (source != null) {
            if (source.isSetInlineObject()) {
                return AddressProperty.of(name, buildObject(source.getObject(),
                        Address.newInstance(),
                        getOrCreateBuilder(AddressAdapter.class)));
            } else {
                Reference reference = getFeatureReference(source);
                return reference != null ? AddressProperty.of(name, reference) : null;
            }
        }

        return null;
    }

    public void addAddress(Name name, org.citygml4j.core.model.core.AddressProperty source, Feature feature) throws ModelBuildException {
        addProperty(getAddressProperty(name, source), feature::addAddress);
    }

    public void addAddress(Name name, org.citygml4j.core.model.core.AddressProperty source, Attribute attribute) throws ModelBuildException {
        addProperty(getAddressProperty(name, source), attribute::addProperty);
    }

    public FeatureProperty getFeatureProperty(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, RelationType relationType) throws ModelBuildException {
        if (source != null && !AbstractGeometry.class.isAssignableFrom(source.getTargetType())) {
            if (source.isSetInlineObject()) {
                Feature feature = getFeature(source.getObject());
                if (feature != null) {
                    return FeatureProperty.of(name, feature, relationType);
                }
            } else {
                return getFeatureProperty(name, (ResolvableAssociation<? extends AbstractGML>) source, relationType);
            }
        }

        return null;
    }

    public FeatureProperty getFeatureProperty(Name name, AbstractInlineProperty<? extends AbstractGML> source, RelationType relationType) throws ModelBuildException {
        if (source != null
                && source.getObject() != null
                && !(source.getObject() instanceof AbstractGeometry)) {
            Feature feature = getFeature(source.getObject());
            if (feature != null) {
                return FeatureProperty.of(name, feature, relationType);
            }
        }

        return null;
    }

    public FeatureProperty getFeatureProperty(Name name, ResolvableAssociation<? extends AbstractGML> source, RelationType relationType) {
        Reference reference = getFeatureReference(source);
        return reference != null ? FeatureProperty.of(name, reference, relationType) : null;
    }

    public void addFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Feature feature, RelationType relationType) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, relationType), feature::addFeature);
    }

    public void addRelatedFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Feature feature) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), feature::addFeature);
    }

    public void addContainedFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Feature feature) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), feature::addFeature);
    }

    public void addFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Attribute attribute, RelationType relationType) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, relationType), attribute::addProperty);
    }

    public void addRelatedFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Attribute attribute) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), attribute::addProperty);
    }

    public void addContainedFeature(Name name, AbstractInlineOrByReferenceProperty<? extends AbstractGML> source, Attribute attribute) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), attribute::addProperty);
    }

    public void addFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Feature feature, RelationType relationType) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, relationType), feature::addFeature);
    }

    public void addRelatedFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Feature feature) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), feature::addFeature);
    }

    public void addContainedFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Feature feature) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), feature::addFeature);
    }

    public void addFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Attribute attribute, RelationType relationType) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, relationType), attribute::addProperty);
    }

    public void addRelatedFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Attribute attribute) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), attribute::addProperty);
    }

    public void addContainedFeature(Name name, AbstractInlineProperty<? extends AbstractGML> source, Attribute attribute) throws ModelBuildException {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), attribute::addProperty);
    }

    public void addFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Feature feature, RelationType relationType) {
        addProperty(getFeatureProperty(name, source, relationType), feature::addFeature);
    }

    public void addRelatedFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Feature feature) {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), feature::addFeature);
    }

    public void addContainedFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Feature feature) {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), feature::addFeature);
    }

    public void addFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Attribute attribute, RelationType relationType) {
        addProperty(getFeatureProperty(name, source, relationType), attribute::addProperty);
    }

    public void addRelatedFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Attribute attribute) {
        addProperty(getFeatureProperty(name, source, RelationType.RELATES), attribute::addProperty);
    }

    public void addContainedFeature(Name name, ResolvableAssociation<? extends AbstractGML> source, Attribute attribute) {
        addProperty(getFeatureProperty(name, source, RelationType.CONTAINS), attribute::addProperty);
    }

    private <T extends Property<?>> void addProperty(T property, Consumer<T> consumer) {
        if (property != null) {
            consumer.accept(property);
        }
    }

    public Reference getFeatureReference(ResolvableAssociation<?> association) {
        return association != null && !AbstractGeometry.class.isAssignableFrom(association.getTargetType()) ?
                getReference(association) :
                null;
    }

    private Reference getReference(ResolvableAssociation<?> association) {
        return association != null && association.getHref() != null ?
                Reference.of(getIdFromReference(association.getHref())) :
                null;
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractGML> Feature getFeature(T source) throws ModelBuildException {
        if (source != null && !(source instanceof AbstractGeometry)) {
            ModelBuilder<T, Feature> builder = context.getBuilder((Class<T>) source.getClass(), Feature.class);
            if (builder != null) {
                return buildObject(source, builder.createModel(source), builder);
            } else {
                logOrThrow(Level.DEBUG, formatMessage(source,
                        "Skipping object because the object type is not supported."));
            }
        }

        return null;
    }

    Feature getTopLevelFeature(AbstractFeature source) throws ModelBuildException {
        try {
            Feature feature = getFeature(source);
            appearanceHelper.processTargets(source);
            return feature;
        } finally {
            clear();
        }
    }

    private <T, R extends Child> R buildObject(T source, R target, ModelBuilder<T, R> builder) throws ModelBuildException {
        if (target != null) {
            builder.build(source, target, this);
            return target;
        } else {
            throw new ModelBuildException("The builder " + builder.getClass().getName() + " returned a null object.");
        }
    }

    public <T extends ModelBuilder<?, ?>> T getOrCreateBuilder(Class<T> type) throws ModelBuildException {
        ModelBuilder<?, ?> cachedBuilder = builderCache.get(type);
        if (cachedBuilder != null && type.isAssignableFrom(cachedBuilder.getClass())) {
            return type.cast(cachedBuilder);
        } else {
            try {
                T builder = type.getDeclaredConstructor().newInstance();
                builderCache.put(type, builder);
                return builder;
            } catch (Exception e) {
                throw new ModelBuildException("The builder " + type.getName() + " lacks a default constructor.");
            }
        }
    }

    public String toXML(Object source, Module... modules) throws ModelBuildException {
        if (source != null) {
            XMLObjects xmlObjects = context.getCityGMLContext().getXMLObjects();
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream(1024)) {
                try (XMLWriter writer = XMLWriterFactory.newInstance(xmlObjects)
                        .createWriter(stream, encoding)
                        .writeXMLDeclaration(false)) {
                    Namespaces namespaces;
                    if (modules != null && modules.length > 0) {
                        namespaces = Namespaces.newInstance();
                        Arrays.stream(modules).forEach(module -> {
                            namespaces.add(module.getNamespaceURI());
                            writer.withPrefix(module.getNamespacePrefix(), module.getNamespaceURI());
                        });
                    } else {
                        namespaces = Namespaces.of(xmlObjects.getSerializableNamespaces());
                    }

                    xmlObjects.toXML(writer, source, namespaces);
                }

                return stream.toString(encoding);
            } catch (Exception e) {
                throw new ModelBuildException("Failed to build XML representation from object.", e);
            }
        } else {
            return null;
        }
    }

    private void clear() {
        appearanceHelper.reset();
        geometryIdCache.clear();
    }
}
