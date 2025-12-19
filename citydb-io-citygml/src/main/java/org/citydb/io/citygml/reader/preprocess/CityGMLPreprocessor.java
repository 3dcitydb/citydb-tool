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

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.file.InputFile;
import org.citydb.io.citygml.reader.CityGMLReaderFactory;
import org.citydb.io.reader.ReadException;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.xml.reader.CityGMLChunk;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReader;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

public class CityGMLPreprocessor {
    private final GlobalAppearanceConverter appearanceConverter;
    private final ImplicitGeometryResolver implicitGeometryResolver;
    private final GeometryReferenceResolver globalReferenceResolver;
    private final DeprecatedPropertiesProcessor propertiesProcessor;
    private final CrossLodReferenceResolver crossLodResolver;
    private final ConcurrentLinkedQueue<CityObjectGroup> cityObjectGroups = new ConcurrentLinkedQueue<>();
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();

    private boolean resolveCrossLodReferences = true;
    private int numberOfThreads;
    private Throwable exception;
    private volatile boolean shouldRun = true;

    public CityGMLPreprocessor() {
        ThreadLocal<CopyBuilder> copyBuilders = ThreadLocal.withInitial(() ->
                new CopyBuilder().failOnError(true));

        appearanceConverter = new GlobalAppearanceConverter(copyBuilders::get);
        implicitGeometryResolver = new ImplicitGeometryResolver(copyBuilders::get, referenceResolver);
        globalReferenceResolver = new GeometryReferenceResolver(copyBuilders::get);
        propertiesProcessor = new DeprecatedPropertiesProcessor(copyBuilders::get);
        crossLodResolver = new CrossLodReferenceResolver(copyBuilders::get)
                .setMode(CrossLodReferenceResolver.Mode.REMOVE_LOD4_REFERENCES);
    }

    public CityGMLPreprocessor useLod4AsLod3(boolean useLod4AsLod3) {
        propertiesProcessor.useLod4AsLod3(useLod4AsLod3);
        return this;
    }

    public CityGMLPreprocessor mapLod0RoofEdge(boolean mapLod0RoofEdge) {
        propertiesProcessor.mapLod0RoofEdge(mapLod0RoofEdge);
        return this;
    }

    public CityGMLPreprocessor mapLod1MultiSurfaces(boolean mapLod1MultiSurfaces) {
        propertiesProcessor.mapLod1MultiSurfaces(mapLod1MultiSurfaces);
        return this;
    }

    public CityGMLPreprocessor createCityObjectRelations(boolean createCityObjectRelations) {
        globalReferenceResolver.createCityObjectRelations(createCityObjectRelations);
        return this;
    }

    public CityGMLPreprocessor resolveCrossLodReferences(boolean resolveCrossLodReferences) {
        this.resolveCrossLodReferences = resolveCrossLodReferences;
        crossLodResolver.setMode(resolveCrossLodReferences ?
                CrossLodReferenceResolver.Mode.RESOLVE :
                CrossLodReferenceResolver.Mode.REMOVE_LOD4_REFERENCES);
        return this;
    }

    public CityGMLPreprocessor setGlobalAppearanceMode(GlobalAppearanceConverter.Mode mode) {
        appearanceConverter.setMode(mode);
        return this;
    }

    public CityGMLPreprocessor setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public Collection<CityObjectGroup> getCityObjectGroups() {
        return cityObjectGroups;
    }

    public void processGlobalObjects(InputFile file, CityGMLReaderFactory factory) throws ReadException {
        CityGMLInputFactory inputFactory = factory.createInputFactory();
        ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(numberOfThreads > 0 ?
                numberOfThreads :
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        CountLatch countLatch = new CountLatch();

        try {
            try (CityGMLReader reader = factory.createReader(file, inputFactory)) {
                List<Appearance> appearances = Collections.synchronizedList(new ArrayList<>());
                ImplicitGeometryCollector collector = new ImplicitGeometryCollector();
                int featureId = 0;

                while (shouldRun && reader.hasNext()) {
                    CityGMLChunk chunk = reader.nextChunk();
                    chunk.getLocalProperties().set("featureId", featureId++);

                    countLatch.increment();
                    service.execute(() -> {
                        try {
                            AbstractFeature feature = chunk.build();
                            if (feature instanceof Appearance appearance) {
                                appearances.add(appearance);
                            } else if (feature instanceof CityObjectGroup group) {
                                cityObjectGroups.add(group);
                            } else {
                                feature.accept(collector);
                                globalReferenceResolver.processGeometryReferences(feature,
                                        (int) chunk.getLocalProperties().get("featureId"));
                            }
                        } catch (Throwable e) {
                            shouldRun = false;
                            exception = e;
                        } finally {
                            countLatch.decrement();
                        }
                    });
                }

                countLatch.await();

                if (shouldRun && !appearances.isEmpty()) {
                    appearanceConverter.preprocess(appearances);
                }

                if (shouldRun && !cityObjectGroups.isEmpty()) {
                    propertiesProcessor.withCityObjectGroups(cityObjectGroups);
                }
            }

            if (shouldRun && implicitGeometryResolver.hasImplicitGeometries()) {
                for (ImplicitGeometry implicitGeometry : implicitGeometryResolver.getImplicitGeometries()) {
                    countLatch.increment();
                    service.execute(() -> {
                        try {
                            appearanceConverter.convertGlobalAppearance(implicitGeometry);
                            referenceResolver.resolveReferences(implicitGeometry);
                        } finally {
                            countLatch.decrement();
                        }
                    });
                }

                countLatch.await();
            }

            if (shouldRun && globalReferenceResolver.hasReferences()) {
                try (CityGMLReader reader = factory.createReader(file, inputFactory,
                        "CityObjectGroup", "Appearance")) {
                    while (shouldRun && reader.hasNext()) {
                        CityGMLChunk chunk = reader.nextChunk();

                        countLatch.increment();
                        service.execute(() -> {
                            try {
                                globalReferenceResolver.processReferencedGeometries(chunk.build());
                            } catch (Exception e) {
                                shouldRun = false;
                                exception = e;
                            } finally {
                                countLatch.decrement();
                            }
                        });
                    }

                    countLatch.await();
                }
            }

            if (exception != null) {
                throw exception;
            }
        } catch (Throwable e) {
            throw new ReadException("Failed to read global objects.", e);
        } finally {
            service.shutdown();
        }
    }

    public boolean process(AbstractFeature feature, int featureId) {
        if (!shouldRun
                || feature instanceof Appearance
                || feature instanceof CityObjectGroup) {
            return false;
        }

        appearanceConverter.convertGlobalAppearance(feature);
        referenceResolver.resolveReferences(feature);
        implicitGeometryResolver.resolveImplicitGeometries(feature);
        globalReferenceResolver.resolveGeometryReferences(feature, featureId);
        if (resolveCrossLodReferences || !propertiesProcessor.isUseLod4AsLod3()) {
            crossLodResolver.resolveCrossLodReferences(feature);
        }

        return propertiesProcessor.process(feature);
    }

    public void postprocess() {
        if (shouldRun) {
            propertiesProcessor.postprocess();
        }
    }

    public void cancel() {
        shouldRun = false;
    }

    private class ImplicitGeometryCollector extends ObjectWalker {
        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometryResolver.addImplicitGeometry(implicitGeometry);
        }
    }
}
