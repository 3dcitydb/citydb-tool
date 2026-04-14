/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.tiles3d;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;

import java.util.List;

/**
 * 3D Tiles 1.1 tileset descriptor POJO, serialized to {@code tileset.json}
 * or sub-tileset JSON via FastJSON2.
 */
@JSONType(alphabetic = false)
public class TilesetDescriptor {
    private TilesetAsset asset;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteNulls)
    private MetadataSchema schema;
    private double geometricError;
    private TileNode root;

    /**
     * Create a root tileset descriptor with schema, transform, and cell references.
     */
    public static TilesetDescriptor ofRoot(double geometricError,
                                           double[] extent,
                                           double[] transform,
                                           List<CellReference> cellRefs,
                                           List<AttrField> attrFields) {
        TilesetDescriptor d = new TilesetDescriptor();
        d.asset = TilesetAsset.TILES_3D;
        d.schema = MetadataSchema.of(attrFields);
        d.geometricError = geometricError;

        TileNode rootTile = TileNode.ofRoot(
                TileBoundingVolume.fromExtent(extent), geometricError, transform);
        for (CellReference ref : cellRefs) {
            rootTile.addChild(TileNode.ofContent(
                    ref.boundingVolume(), ref.geometricError(), ref.uri()));
        }

        d.root = rootTile;
        return d;
    }

    /**
     * Create a sub-tileset descriptor for a cell's quadtree.
     */
    public static TilesetDescriptor ofSubtileset(double geometricError,
                                                 TileNode rootTile) {
        TilesetDescriptor d = new TilesetDescriptor();
        d.asset = TilesetAsset.TILES_3D;
        d.geometricError = geometricError;
        d.root = rootTile;
        return d;
    }
}
