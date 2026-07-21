/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.vis.geometry.TriangleMesh;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A composited texture atlas page: the packed image plus the per-texture
 * UV regions, tile offsets, and rotation flags needed to remap mesh UVs from
 * per-texture space into atlas space. Instances are produced by
 * {@link TextureAtlasBuilder}; this class only carries the finished page and
 * its consumer-side operations ({@link #remapUVs}, {@link #write}).
 * <p>
 * Textures may have been rotated 90° by the packer to improve density;
 * rotation is handled transparently in {@link #remapUVs}. Tiled/wrapping
 * textures (CityGML UVs outside {@code [0,1]}) are covered by repeated copies
 * in the atlas region, mapped back via the per-texture tile offsets.
 */
public class TextureAtlas {
    /**
     * Sentinel texture id for the reserved solid-white region appended to the
     * atlas for mixed nodes (some features textured, some not). The I3S path
     * keeps a single node-level textured material, so untextured triangles
     * need UVs that sample a guaranteed-white pixel to render as the default
     * PBR color.
     */
    static final int WHITE_PIXEL_TEX_ID = Integer.MIN_VALUE;

    private final Map<Integer, float[]> uvRegions;
    private final BufferedImage image;
    /** Per-texture tile mapping: texId → [offsetU, offsetV, rangeU, rangeV]. */
    private final Map<Integer, float[]> tileOffsets;
    /** Texture IDs that were rotated 90° CCW by the packer. */
    private final Set<Integer> rotatedTextureIds;
    /** Atlas-space UV of the reserved white pixel center, or {@code null}. */
    private final float[] whitePixelUV;
    /**
     * Final scale applied to source textures. Equal to the user's
     * {@code --texture-scale} request when no rescale fired; less than
     * the request when overflow forced a Phase 1 or post-expansion rescale.
     * Callers compare against {@code formatOptions.getTextureScale()} to
     * detect a {@code --texture-scale} violation.
     */
    private final double actualScale;

    TextureAtlas(Map<Integer, float[]> uvRegions, BufferedImage image,
                 Map<Integer, float[]> tileOffsets,
                 Set<Integer> rotatedTextureIds,
                 float[] whitePixelUV,
                 double actualScale) {
        this.uvRegions = uvRegions;
        this.image = image;
        this.tileOffsets = tileOffsets;
        this.rotatedTextureIds = rotatedTextureIds;
        this.whitePixelUV = whitePixelUV;
        this.actualScale = actualScale;
    }

    /**
     * Remap UV coordinates from per-texture space to atlas space.
     * <p>
     * For rotated textures, applies a 90° CCW UV rotation before the atlas
     * position transform: {@code rotU = origV, rotV = offU + rangeU - origU}.
     * <p>
     * For wrapping/tiling textures (UVs outside [0,1]), the mapping is:
     * {@code atlasUV = regionOffset + ((uv - tileOffset) / tileRange) * regionScale}
     */
    public void remapUVs(TriangleMesh mesh) {
        int vertexCount = mesh.getVertexCount();
        int[] vertexTexId = new int[vertexCount];
        Arrays.fill(vertexTexId, -1);
        List<int[]> triangles = mesh.getTriangles();
        List<Integer> triTexIds = mesh.getTriangleTextureIds();
        for (int t = 0; t < triangles.size(); t++) {
            int texId = triTexIds.get(t);
            if (texId >= 0) {
                int[] tri = triangles.get(t);
                for (int vi : tri) {
                    vertexTexId[vi] = texId;
                }
            }
        }

        float[] defaultTile = {0f, 0f, 1f, 1f};

        List<float[]> texCoords = mesh.getTexCoords();
        for (int v = 0; v < vertexCount; v++) {
            int texId = vertexTexId[v];
            if (texId >= 0) {
                float[] region = uvRegions.get(texId);
                if (region != null) {
                    float[] tile = tileOffsets.getOrDefault(texId, defaultTile);
                    float[] uv = texCoords.get(v);

                    if (rotatedTextureIds.contains(texId)) {
                        // 90° CCW rotation in texture space:
                        //   rotU = origV  (V axis → U axis)
                        //   rotV = offU + rangeU - origU  (inverted U axis → V axis)
                        // Then map with swapped tile parameters:
                        //   normU = (rotU - offV) / rangeV
                        //   normV = rotV / rangeU
                        float origU = uv[0], origV = uv[1];
                        float rotU = origV;
                        float rotV = tile[0] + tile[2] - origU;
                        uv[0] = region[0] + ((rotU - tile[1]) / tile[3]) * region[2];
                        uv[1] = region[1] + (rotV / tile[2]) * region[3];
                    } else {
                        uv[0] = region[0] + ((uv[0] - tile[0]) / tile[2]) * region[2];
                        uv[1] = region[1] + ((uv[1] - tile[1]) / tile[3]) * region[3];
                    }
                }
            }
        }

        // Mixed node: point every vertex reached only from untextured triangles
        // at the reserved white pixel. The single textured material then
        // renders those vertices as white × COLOR_0 — which yields the baked
        // X3DMaterial color where one is present, and solid white otherwise
        // (matching the untextured-node PBR default).
        if (whitePixelUV != null) {
            for (int v = 0; v < vertexCount; v++) {
                if (vertexTexId[v] < 0) {
                    float[] uv = texCoords.get(v);
                    uv[0] = whitePixelUV[0];
                    uv[1] = whitePixelUV[1];
                }
            }
        }
    }

    public void write(Path target) throws IOException {
        ImageIO.write(image, "jpg", target.toFile());
    }

    public void write(java.io.OutputStream out) throws IOException {
        ImageIO.write(image, "jpg", out);
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    /**
     * Final scale applied to source textures by the rescale loop. Equal to
     * the user's clamped {@code --texture-scale} when no rescale fired; less
     * when overflow forced one. Callers compare against
     * {@code formatOptions.getTextureScale()} to detect a violation.
     */
    public double getActualScale() {
        return actualScale;
    }

    /**
     * Texture IDs packed into this atlas (excluding the reserved white-pixel
     * sentinel). Used by the multi-atlas 3D Tiles path to route each textured
     * triangle to the GLB primitive backed by the correct atlas page.
     */
    public Set<Integer> getTextureIds() {
        return uvRegions.keySet();
    }
}
