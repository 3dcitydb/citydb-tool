package org.citydb.web.util;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.walker.ModelWalker;
import org.citydb.web.schema.geojson.FeatureCollectionGeoJSON;
import org.citydb.web.schema.geojson.FeatureGeoJSON;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GeoJSONWriter implements FeatureWriter {
    private final GeoJSONConverter geoJsonConverter;
    private final FeatureCollectionGeoJSON featureCollectionGeoJSON;

    public GeoJSONWriter() {
        geoJsonConverter = new GeoJSONConverter();
        featureCollectionGeoJSON = FeatureCollectionGeoJSON.newInstance();
    }

    @Override
    public void initialize(OutputFile file, WriteOptions options) throws WriteException {
        //
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        MultiSurface surfaces = MultiSurface.empty();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(Polygon polygon) {
                surfaces.getPolygons().add(polygon);
            }
        });
        FeatureGeoJSON featureGeoJSON = FeatureGeoJSON.of(geoJsonConverter.convert(surfaces));
        Map<String, Object> properties = new HashMap<>();
        for (Attribute attribute : feature.getAttributes().getAll()) {
            if (attribute.getDataType().isPresent()) {
                String attrName = attribute.getName().getLocalName();
                switch (DataType.of(attribute.getDataType().get())) {
                    case INTEGER -> properties.put(attrName, attribute.getIntValue().orElse(null));
                    case DOUBLE -> properties.put(attrName, attribute.getDoubleValue().orElse(null));
                    case STRING, CODE -> properties.put(attrName, attribute.getStringValue().orElse(null));
                    case URI -> properties.put(attrName, attribute.getURI().orElse(null));
                    case TIMESTAMP -> properties.put(attrName, attribute.getTimeStamp().orElse(null));
                    case BOOLEAN -> properties.put(attrName, 1 == attribute.getIntValue().orElse(0L));
                }
            }
        }
        featureGeoJSON.setProperties(properties);
        featureCollectionGeoJSON.addFeature(featureGeoJSON);

        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void cancel() {
        //
    }

    @Override
    public void close() throws WriteException {
        featureCollectionGeoJSON.getFeatures().clear();
    }

    public FeatureCollectionGeoJSON getFeatureCollectionGeoJSON() {
        return featureCollectionGeoJSON;
    }
}
