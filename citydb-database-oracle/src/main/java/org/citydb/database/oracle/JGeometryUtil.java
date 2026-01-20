/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import oracle.spatial.geometry.J3D_Geometry;

import oracle.spatial.geometry.JGeometry;

/**
 * This class contains utility methods to construct a JGeometry object.
 */
public class JGeometryUtil {
    static final int ETYPE_PREFIX_EXTERIOR = 1;    //counterclockwise
    static final int ETYPE_PREFIX_INTERIOR = 2;    //clockwise
    static final int ETYPE_CURVE           = 2;

    static final int EITPR_RECTANGLE       = 3;

    public static int getSDOGType(int gtype, int dim){
        return dim*1000 + gtype%10;
    }

    /**
     * Return the 4 digit SDO element type give the interior or exterior single digit etype.
     * @param etype single digit etype
     * @param isExterior whether the give element if an exterior element or not
     * @return
     */
    public static int getSDOEType(int etype, boolean isExterior){
        if(isExterior){
            return ETYPE_PREFIX_EXTERIOR * 1000 + etype;
        }
        else {
            return ETYPE_PREFIX_INTERIOR * 1000 + etype;
        }
    }

    private static JGeometry pad2DTo3D(JGeometry geometry){
        if(geometry.getDimensions()==3){
            return geometry;
        }
        int gtype = getSDOGType(geometry.getType(), 3);
        int srid = geometry.getSRID();
        int[] elemInfo2D = geometry.getElemInfo();
        int[] elemInfo3D = new int[elemInfo2D.length];
        System.arraycopy(elemInfo2D, 0, elemInfo3D, 0, elemInfo2D.length);
        int numElements = elemInfo3D.length/3;
        for(int i=0; i<numElements; i++){
            elemInfo3D[i*3] = (elemInfo2D[i*3]/2)*3 + elemInfo2D[i*3]%2;
        }
        double[] ordinates2D = geometry.getOrdinatesArray();
        double[] ordinates3D = new double[ordinates2D.length*3/2];
        int numPoints = ordinates2D.length/2;
        for(int i=0; i<numPoints; i++){
            ordinates3D[i*3] = ordinates2D[i*2];
            ordinates3D[i*3+1] = ordinates2D[i*2+1];
            ordinates3D[i*3+2] = 0;
        }
        return new JGeometry(gtype, srid, elemInfo3D, ordinates3D);
    }

    private static JGeometry createLinearPolygon(int[] startIndexOfRings, double[] coords, int dim, int srid)
    {
        if(startIndexOfRings==null){
            startIndexOfRings = new int[]{0};
        }
        int numRings = startIndexOfRings!=null && startIndexOfRings.length>0 ? startIndexOfRings.length : 1;
        int[] elemInfo = new int[3*numRings];
        elemInfo[0] = 1;
        elemInfo[1] = getSDOEType(J3D_Geometry.ETYPE_POLYGON, true); //outer ring
        elemInfo[2] = 1;    //linear
        for(int i=1; i<startIndexOfRings.length; i++){
            elemInfo[i*3] = startIndexOfRings[i] + 1;
            elemInfo[i*3+1] = getSDOEType(J3D_Geometry.ETYPE_POLYGON, false); //inner ring
            elemInfo[i*3+2] = 1;    //linear
        }
        JGeometry geometry = new JGeometry(getSDOGType(JGeometry.GTYPE_POLYGON, dim), srid, elemInfo, coords);
        return geometry;
    }

    /**
     * Creates a linear polygon with one or more rings.
     * @param coordsOfRings
     * @param dim
     * @param srid
     * @return
     */
    public static JGeometry createLinearPolygon(double[][] coordsOfRings, int dim, int srid)
    {
        int totalCoordsLength = 0;
        int[] startIndexOfRings = new int[coordsOfRings.length];
        for(int i=0; i<coordsOfRings.length; i++){
            startIndexOfRings[i] = totalCoordsLength;
            totalCoordsLength += coordsOfRings[i].length;
        }
        double[] coords = new double[totalCoordsLength];
        for(int i=0; i<coordsOfRings.length; i++){
            System.arraycopy(coordsOfRings[i], 0, coords, startIndexOfRings[i], coordsOfRings[i].length);
        }
        return createLinearPolygon(startIndexOfRings, coords, dim, srid);
    }

    /**
     * Creates a linear polygon with a single ring.
     * @param coords
     * @param dim
     * @param srid
     * @return
     */
    public static JGeometry createLinearPolygon(double[] coords, int dim, int srid)
    {
        return createLinearPolygon(new int[]{0}, coords, dim, srid);
    }

    /**
     * Creates a surface consisting of one or more polygons.
     * @param polygons
     * @return
     */
    public static JGeometry createSurface(JGeometry[] polygons) {
        for(int i=0; i<polygons.length; i++){
            polygons[i] = pad2DTo3D(polygons[i]);   //surface requires 3D polygons
        }
        return createMultiPolygon(polygons, true);
    }

    /**
     * Creates a multi-surface.
     * @param surfaces
     * @return
     */
    public static JGeometry createMultiSurface(JGeometry[] surfaces) {
        return createMultiGeometry(surfaces, J3D_Geometry.GTYPE_MULTISURFACE, -1);
    }

    /**
     * Creates a multi-polygon.
     * @param polygons
     * @return
     */
    public static JGeometry createMultiPolygon(JGeometry[] polygons) {
        return createMultiPolygon(polygons, false);
    }

    /**
     * Creates a multi-polygon or multi-surface type JGeometry.
     * @param polygons
     * @param isSurface set to true when creating multi-surface, or when any of the input
     *                               polygons have holes.
     * @return a multi-polygon or multi-surface type JGeometry
     */
    private static JGeometry createMultiPolygon(JGeometry[] polygons, boolean isSurface) {
        return createMultiGeometry(polygons, J3D_Geometry.GTYPE_MULTIPOLYGON,
                isSurface? J3D_Geometry.ETYPE_COMPOSITEPOLYGON: -1);
    }

    /**
     * Creates a solid with the given surface as its shell.
     * @param surface
     * @return
     */
    public static JGeometry createSolid(JGeometry surface){
        int gtype  = getSDOGType(J3D_Geometry.GTYPE_SOLID, 3);
        int srid = surface.getSRID();
        int[] oldElemInfo = surface.getElemInfo();
        int[] elemInfo = new int[oldElemInfo.length+3];
        elemInfo[0] = 1;
        elemInfo[1] = getSDOEType(J3D_Geometry.ETYPE_SOLID, true);
        elemInfo[2] = 1;
        System.arraycopy(oldElemInfo, 0, elemInfo, 3, oldElemInfo.length);
        double[] oldOrdinates = surface.getOrdinatesArray();
        double[] ordinates = new double[oldOrdinates.length];
        System.arraycopy(oldOrdinates, 0, ordinates, 0, oldOrdinates.length);
        return new J3D_Geometry(gtype, srid, elemInfo, ordinates);
    }

    /**
     * Creates a multi-solid.
     * @param solids
     * @return
     */
    public static J3D_Geometry createMultiSolid(JGeometry[] solids) {
        return createMultiGeometry(solids, J3D_Geometry.GTYPE_MULTISOLID, -1);
    }

    /**
     * Creates a composite solid.
     * @param solids
     * @return
     */
    public static J3D_Geometry createCompositeSolid(JGeometry[] solids) {
        return createMultiGeometry(solids, J3D_Geometry.GTYPE_SOLID, J3D_Geometry.ETYPE_COMPOSITESOLID);
    }

    /**
     * Creates a multi-geometry consisting of the given geometries.
     * @param geoms geometries to be included in the multi-geometry
     * @param gtype single digit gtype of the multi-geometry
     * @param etype single digit etype of the multi-geometry. If less than or equal to zero, then no additional
     *              element info triplet will be added to the beginning of the element info array.
     * @return
     */
    private static J3D_Geometry createMultiGeometry(JGeometry[] geoms, int gtype, int etype){
        if(geoms == null || geoms.length == 0){
            return null;
        }
        int srid = geoms[0].getSRID();
        int dim = geoms[0].getDimensions();
        int internalGType = getSDOGType(gtype, dim);
        int[][] elemInfoArray = new int[geoms.length][];
        double[][] ordinatesArray = new double[geoms.length][];
        int elemInfoLength = 0;
        int ordinatesLength = 0;
        for(int i = 0; i < geoms.length; i++){
            elemInfoArray[i] = geoms[i].getElemInfo();
            elemInfoLength += elemInfoArray[i].length;
            ordinatesArray[i] = geoms[i].getOrdinatesArray();
            ordinatesLength += ordinatesArray[i].length;
        }
        double[] ordinates = new double[ordinatesLength];
        int currElemInfoPos = 0;
        int currOrdinatesPos = 0;
        int[] elemInfo = null;
        if(etype > 0) {
            elemInfo = new int[elemInfoLength + 3];
            elemInfo[currElemInfoPos++] = 1;
            elemInfo[currElemInfoPos++] = getSDOEType(etype, true);
            elemInfo[currElemInfoPos++] = geoms.length;
        }
        else{
            elemInfo = new int[elemInfoLength];
        }
        for(int i=0; i<geoms.length; i++){
            System.arraycopy(elemInfoArray[i], 0, elemInfo, currElemInfoPos, elemInfoArray[i].length);
            for(int j=0; j<elemInfoArray[i].length; j += 3){
                elemInfo[currElemInfoPos + j] += currOrdinatesPos;
            }
            currElemInfoPos += elemInfoArray[i].length;
            System.arraycopy(ordinatesArray[i], 0, ordinates, currOrdinatesPos, ordinatesArray[i].length);
            currOrdinatesPos += ordinatesArray[i].length;
        }
        return new J3D_Geometry(internalGType, srid, elemInfo, ordinates);
    }

}

