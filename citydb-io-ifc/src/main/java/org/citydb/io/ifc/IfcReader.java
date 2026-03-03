package org.citydb.io.ifc;

import org.bimserver.emf.IfcModelInterface;
import org.citydb.config.ConfigException;
import org.citydb.core.file.InputFile;
import org.citydb.io.ifc.converter.BuildingConverter;
import org.citydb.io.ifc.converter.IfcElementHelper;
import org.citydb.io.ifc.reader.*;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citydb.model.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class IfcReader implements FeatureReader {

    private static final Logger logger = LoggerFactory.getLogger(IfcReader.class);

    private final InputFile file;
    private final ReadOptions options;
    private final IfcFormatOptions formatOptions;
    private volatile boolean shouldRun = true;

    IfcReader(InputFile file, ReadOptions options) throws ReadException {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.options = Objects.requireNonNull(options, "The read options must not be null.");

        try {
            formatOptions = options.getFormatOptions()
                    .getOrElse(IfcFormatOptions.class, IfcFormatOptions::new);
        } catch (ConfigException e) {
            throw new ReadException("Failed to get IFC format options from config.", e);
        }
    }

    @Override
    public void read(Consumer<Feature> consumer) throws ReadException {
        shouldRun = true;
        String inputPath = file.getFile().toString();

        try {
            // Step 1: Load IFC model via BIMserver
            logger.info("Step 1: Loading IFC model...");
            IfcModelLoader modelLoader = new IfcModelLoader();
            IfcModelInterface model = modelLoader.loadModel(inputPath);

            if (!shouldRun) return;

            // Step 2: Extract geometry via Python
            logger.info("Step 2: Extracting geometry...");
            GeometryJsonLoader geometryLoader = new GeometryJsonLoader();
            geometryLoader.load(inputPath, formatOptions.isReorientShells());

            if (!shouldRun) return;

            // Step 3: Setup georeferencing
            logger.info("Step 3: Setting up georeferencing...");
            GeoReferencing geoRef = new GeoReferencing();
            geoRef.setup(model, formatOptions.getTargetSrid(),
                    formatOptions.getXOffset(), formatOptions.getYOffset(), formatOptions.getZOffset());

            // Step 4: Create processors
            GeometryProcessor geometryProcessor = new GeometryProcessor(
                    geometryLoader.getGeometryCache(), geoRef);
            PropertyProcessor propertyProcessor = new PropertyProcessor(
                    model, formatOptions.isNoProperties(),
                    formatOptions.isNoGenericAttributeSets(),
                    formatOptions.isPsetNamesAsPrefixes());
            propertyProcessor.buildPropertySetMap();
            AppearanceProcessor appearanceProcessor = new AppearanceProcessor(
                    geometryLoader.getMaterialCache(), formatOptions.isNoAppearances());

            // Step 5: Create helper and converter
            String ifcFilename = file.getFile().getFileName().toString();
            IfcElementHelper helper = new IfcElementHelper(
                    geometryProcessor, propertyProcessor, appearanceProcessor,
                    ifcFilename, formatOptions.isNoReferences());
            BuildingConverter buildingConverter = new BuildingConverter(
                    helper, formatOptions.isNoStoreys(),
                    formatOptions.isListUnmappedDoorsAndWindows(),
                    formatOptions.isUnrelatedDoorsAndWindowsInDummyBce());

            // Step 6: Convert buildings and emit features
            logger.info("Step 6: Converting buildings...");
            List<Feature> buildings = buildingConverter.convertAll(model);

            for (Feature building : buildings) {
                if (!shouldRun) return;
                consumer.accept(building);
            }

            logger.info("IFC import completed: {} building(s) converted", buildings.size());

        } catch (ReadException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadException("Failed to read IFC file.", e);
        }
    }

    @Override
    public void cancel() {
        shouldRun = false;
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
