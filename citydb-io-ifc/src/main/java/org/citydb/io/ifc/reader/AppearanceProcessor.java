package org.citydb.io.ifc.reader;

import org.bimserver.models.ifc4.IfcProduct;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.AppearanceProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AppearanceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppearanceProcessor.class);

    private final Map<String, List<double[]>> materialCache;
    private final boolean noAppearances;

    public AppearanceProcessor(Map<String, List<double[]>> materialCache, boolean noAppearances) {
        this.materialCache = materialCache;
        this.noAppearances = noAppearances;
    }

    public void addAppearance(Feature feature, IfcProduct element, List<Polygon> polygons) {
        if (noAppearances) return;
        if (materialCache == null) return;

        List<double[]> materials = materialCache.get(element.getGlobalId());
        if (materials == null || materials.isEmpty()) return;

        Map<String, List<Integer>> materialGroups = new LinkedHashMap<>();
        for (int i = 0; i < materials.size() && i < polygons.size(); i++) {
            double[] mat = materials.get(i);
            if (mat == null) continue;
            String key = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", mat[0], mat[1], mat[2], mat[3]);
            materialGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        if (materialGroups.isEmpty()) return;

        String objId = feature.getObjectId().orElse("unknown");
        Appearance appearance = Appearance.of("RGB");

        int matIdx = 0;
        for (Map.Entry<String, List<Integer>> group : materialGroups.entrySet()) {
            String[] parts = group.getKey().split(",");
            double r = Double.parseDouble(parts[0]);
            double g = Double.parseDouble(parts[1]);
            double b = Double.parseDouble(parts[2]);
            double transparency = Double.parseDouble(parts[3]);

            X3DMaterial x3dMaterial = X3DMaterial.newInstance();
            x3dMaterial.setObjectId("MAT_" + objId + "_" + matIdx);
            x3dMaterial.setIsFront(true);
            x3dMaterial.setDiffuseColor(Color.of(r, g, b));
            if (transparency > 0) {
                x3dMaterial.setTransparency(transparency);
            }

            for (int faceIdx : group.getValue()) {
                if (faceIdx < polygons.size()) {
                    x3dMaterial.addTarget(polygons.get(faceIdx));
                }
            }

            appearance.addSurfaceData(SurfaceDataProperty.of(x3dMaterial));
            matIdx++;
        }

        feature.addAppearance(AppearanceProperty.of(
                Name.of("appearance", Namespaces.APPEARANCE), appearance));
    }
}
