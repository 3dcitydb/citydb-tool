package org.citydb.io.ifc.reader;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.IfcMapConversion;
import org.bimserver.models.ifc4.IfcSite;
import org.geotools.referencing.CRS;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GeoReferencing {

    private static final Logger logger = LoggerFactory.getLogger(GeoReferencing.class);

    private double eastings;
    private double northings;
    private double orthogonalHeight;
    private double scale = 1.0;
    private double[][] rotationMatrix = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    private double xOffset;
    private double yOffset;
    private double zOffset;

    public void setup(IfcModelInterface model, int targetSrid,
                      double xOffset, double yOffset, double zOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        if (xOffset != 0.0 || yOffset != 0.0 || zOffset != 0.0) {
            logger.info("Coordinate offsets: X={}, Y={}, Z={}", xOffset, yOffset, zOffset);
        }
        try {
            List<IfcMapConversion> mapConversions = model.getAll(IfcMapConversion.class);

            if (!mapConversions.isEmpty()) {
                IfcMapConversion mc = mapConversions.get(0);
                this.eastings = mc.getEastings();
                this.northings = mc.getNorthings();
                this.orthogonalHeight = mc.getOrthogonalHeight();
                this.scale = mc.getScale() != 0 ? mc.getScale() : 1.0;

                if (mc.isSetXAxisAbscissa() && mc.isSetXAxisOrdinate()) {
                    double cosR = mc.getXAxisAbscissa();
                    double sinR = mc.getXAxisOrdinate();
                    this.rotationMatrix = new double[][]{
                            {cosR, -sinR, 0},
                            {sinR, cosR, 0},
                            {0, 0, 1}
                    };
                }

                logger.info("Georeferencing from IfcMapConversion: E={}, N={}, H={}",
                        eastings, northings, orthogonalHeight);
            } else if (targetSrid > 0) {
                setupFromIfcSite(model, targetSrid);
            } else {
                logger.info("No IfcMapConversion found and no target SRID. Using local coordinates.");
            }
        } catch (Exception e) {
            logger.warn("Error setting up georeferencing: {}", e.getMessage());
        }
    }

    private void setupFromIfcSite(IfcModelInterface model, int targetSrid) {
        List<IfcSite> sites = model.getAll(IfcSite.class);
        if (sites.isEmpty()) {
            logger.info("No IfcMapConversion or IfcSite found. Using local coordinates.");
            return;
        }

        IfcSite site = sites.get(0);
        if (!site.isSetRefLatitude() || !site.isSetRefLongitude()) {
            logger.info("IfcSite has no RefLatitude/RefLongitude. Using local coordinates.");
            return;
        }

        double lat = dmsToDecimal(site.getRefLatitude());
        double lon = dmsToDecimal(site.getRefLongitude());
        double elevation = site.isSetRefElevation() ? site.getRefElevation() : 0.0;

        try {
            CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
            CoordinateReferenceSystem targetCrs = CRS.decode("EPSG:" + targetSrid, true);
            MathTransform transform = CRS.findMathTransform(wgs84, targetCrs, true);

            double[] src = {lon, lat};
            double[] dst = new double[2];
            transform.transform(src, 0, dst, 0, 1);

            this.eastings = dst[0];
            this.northings = dst[1];
            this.orthogonalHeight = elevation;

            logger.info("Georeferencing from IfcSite (lat={}, lon={}) -> EPSG:{}: E={}, N={}, H={}",
                    lat, lon, targetSrid, eastings, northings, orthogonalHeight);
        } catch (Exception e) {
            logger.warn("Failed to transform IfcSite coordinates to EPSG:{}: {}", targetSrid, e.getMessage());
        }
    }

    private double dmsToDecimal(List<Long> dms) {
        double result = 0;
        if (dms.size() > 0) result += dms.get(0);
        if (dms.size() > 1) result += dms.get(1) / 60.0;
        if (dms.size() > 2) result += dms.get(2) / 3600.0;
        if (dms.size() > 3) result += dms.get(3) / 3600000000.0;
        return result;
    }

    public double[] transformVertex(double[] vertex) {
        double[] v = new double[3];
        v[0] = vertex[0] * scale;
        v[1] = vertex[1] * scale;
        v[2] = vertex[2] * scale;

        double[] rotated = new double[3];
        for (int i = 0; i < 3; i++) {
            rotated[i] = rotationMatrix[i][0] * v[0] +
                    rotationMatrix[i][1] * v[1] +
                    rotationMatrix[i][2] * v[2];
        }

        rotated[0] += eastings + xOffset;
        rotated[1] += northings + yOffset;
        rotated[2] += orthogonalHeight + zOffset;

        return rotated;
    }
}
