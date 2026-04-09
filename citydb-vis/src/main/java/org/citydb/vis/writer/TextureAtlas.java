/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.textureAtlas.TextureAtlasCreator;
import org.citydb.textureAtlas.model.AtlasRegion;
import org.citydb.textureAtlas.packer.Packer;
import org.citydb.vis.geometry.TriangleMesh;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Packs multiple texture images into a single atlas and remaps UV coordinates.
 * Uses the BASIC (Lightmap/BSP) algorithm from texture-atlas-creator for fast
 * packing with O(n log n) complexity. Textures may be rotated 90° to improve
 * packing density; rotation is handled transparently in compositing and UV
 * remapping.
 * <p>
 * Atlas dimensions are capped at {@code maxAtlasSize} to stay within WebGL
 * texture size limits; when textures exceed the limit, they are scaled down
 * uniformly to fit.
 * <p>
 * Supports tiled/wrapping textures: when CityGML UV coordinates exceed [0,1],
 * the source texture is repeated in the atlas region to cover the full UV range.
 */
class TextureAtlas {
    private final Map<Integer, float[]> uvRegions;
    private final BufferedImage image;
    /** Per-texture tile mapping: texId → [offsetU, offsetV, rangeU, rangeV]. */
    private final Map<Integer, float[]> tileOffsets;
    /** Texture IDs that were rotated 90° CCW by the packer. */
    private final Set<Integer> rotatedTextureIds;

    private TextureAtlas(Map<Integer, float[]> uvRegions, BufferedImage image,
                         Map<Integer, float[]> tileOffsets,
                         Set<Integer> rotatedTextureIds) {
        this.uvRegions = uvRegions;
        this.image = image;
        this.tileOffsets = tileOffsets;
        this.rotatedTextureIds = rotatedTextureIds;
    }

    /**
     * Build a single texture atlas from the given texture IDs.
     *
     * @param uvExtents per-texture UV extent: texId → [minU, minV, maxU, maxV].
     *                  Used to compute tiling for wrapping textures.
     */
    static TextureAtlas build(Collection<Integer> textureIds, TextureStore textureStore,
                              double textureScale, int maxAtlasSize,
                              Map<Integer, float[]> uvExtents) throws IOException {
        List<int[]> dims = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        Map<Integer, int[]> tileInfo = new HashMap<>();
        Map<Integer, float[]> tileOffsets = new HashMap<>();

        loadAndPrepareTiled(textureIds, textureStore, uvExtents, dims, images,
                tileInfo, tileOffsets);
        if (dims.isEmpty()) return null;

        double scale = Math.min(1.0, Math.max(0.01, textureScale));
        return buildSingleAtlas(dims, images, scale, maxAtlasSize, tileInfo, tileOffsets);
    }

    // ---- Image loading and tiling preparation --------------------------------

    /**
     * Load texture images and compute tiling information from UV extents.
     * <p>
     * Populates {@code dims} with <b>effective</b> (tiled) dimensions so that
     * packing allocates enough space for all tiles.
     */
    private static void loadAndPrepareTiled(
            Collection<Integer> textureIds, TextureStore textureStore,
            Map<Integer, float[]> uvExtents,
            List<int[]> dims, List<BufferedImage> images,
            Map<Integer, int[]> tileInfo, Map<Integer, float[]> tileOffsets) throws IOException {

        for (int texId : textureIds) {
            Path source = textureStore.getSourcePath(texId);
            if (source == null) continue;
            BufferedImage img = ImageIO.read(source.toFile());
            if (img == null) continue;

            int origW = img.getWidth();
            int origH = img.getHeight();

            float[] extent = uvExtents != null ? uvExtents.get(texId) : null;
            int tilesU, tilesV;
            float offsetU, offsetV, rangeU, rangeV;

            if (extent != null) {
                offsetU = (float) Math.floor(extent[0]);
                offsetV = (float) Math.floor(extent[1]);
                tilesU = Math.max(1, (int) Math.ceil(extent[2]) - (int) Math.floor(extent[0]));
                tilesV = Math.max(1, (int) Math.ceil(extent[3]) - (int) Math.floor(extent[1]));
                rangeU = tilesU;
                rangeV = tilesV;
            } else {
                offsetU = 0f;
                offsetV = 0f;
                tilesU = 1;
                tilesV = 1;
                rangeU = 1f;
                rangeV = 1f;
            }

            // Use effective (tiled) dimensions for packing
            dims.add(new int[]{texId, origW * tilesU, origH * tilesV});
            images.add(img);
            tileInfo.put(texId, new int[]{tilesU, tilesV});
            tileOffsets.put(texId, new float[]{offsetU, offsetV, rangeU, rangeV});
        }
    }

    // ---- Core atlas builder --------------------------------------------------

    /**
     * Core atlas builder that works on pre-loaded images with tiling support.
     *
     * @param dims        [texId, effectiveWidth, effectiveHeight] per texture
     * @param images      source (untiled) images
     * @param scale       user-requested texture scale
     * @param tileInfo    texId → [tilesU, tilesV]
     * @param tileOffsets texId → [offsetU, offsetV, rangeU, rangeV]
     */
    private static TextureAtlas buildSingleAtlas(List<int[]> dims, List<BufferedImage> images,
                                                  double scale, int maxAtlasSize,
                                                  Map<Integer, int[]> tileInfo,
                                                  Map<Integer, float[]> tileOffsets) {
        if (dims.size() == 1) {
            int texId = dims.get(0)[0];
            int[] ti = tileInfo.getOrDefault(texId, new int[]{1, 1});
            if (ti[0] == 1 && ti[1] == 1) {
                // No tiling — return single image as-is
                Map<Integer, float[]> regions = Map.of(texId, new float[]{0f, 0f, 1f, 1f});
                return new TextureAtlas(regions, TextureStore.toOpaqueRgb(images.get(0)),
                        tileOffsets, Set.of());
            }
            // Single texture with tiling — fall through to atlas compositing
        }

        // Pack with BASIC (Lightmap/BSP) — O(n log n), may rotate textures
        Map<Integer, Integer> texIdToIdx = new HashMap<>();
        Packer packer = new Packer(maxAtlasSize, maxAtlasSize,
                TextureAtlasCreator.BASIC, false);
        for (int i = 0; i < dims.size(); i++) {
            int[] d = dims.get(i);
            int w = Math.max(1, (int) (d[1] * scale));
            int h = Math.max(1, (int) (d[2] * scale));
            packer.addRegion(String.valueOf(d[0]), w, h);
            texIdToIdx.put(d[0], i);
        }
        var packed = packer.pack(false);

        // If any textures overflow to a second page, scale down and retry
        boolean hasOverflow = false;
        for (AtlasRegion r : packed.getRegions()) {
            if (r.level > 0) { hasOverflow = true; break; }
        }
        if (hasOverflow) {
            long totalArea = 0;
            for (int[] d : dims) {
                totalArea += (long) Math.max(1, (int) (d[1] * scale))
                        * Math.max(1, (int) (d[2] * scale));
            }
            double areaRatio = (double) ((long) maxAtlasSize * maxAtlasSize) / totalArea;
            scale *= Math.sqrt(Math.min(1.0, areaRatio * 0.9));

            packer = new Packer(maxAtlasSize, maxAtlasSize,
                    TextureAtlasCreator.BASIC, false);
            for (int[] d : dims) {
                int w = Math.max(1, (int) (d[1] * scale));
                int h = Math.max(1, (int) (d[2] * scale));
                packer.addRegion(String.valueOf(d[0]), w, h);
            }
            packed = packer.pack(false);
        }

        // Collect placement results and track rotated textures
        int atlasWidth = 0, atlasHeight = 0;
        Map<Integer, int[]> pixelRegions = new LinkedHashMap<>();
        Set<Integer> rotated = new HashSet<>();
        for (AtlasRegion r : packed.getRegions()) {
            int texId = Integer.parseInt(r.texImageName);
            pixelRegions.put(texId, new int[]{r.x, r.y, r.width, r.height});
            atlasWidth = Math.max(atlasWidth, r.x + r.width);
            atlasHeight = Math.max(atlasHeight, r.y + r.height);
            if (r.isRotated) {
                rotated.add(texId);
            }
        }

        // Compose atlas image — draw each texture into its allocated region
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, atlasWidth, atlasHeight);

        for (Map.Entry<Integer, int[]> entry : pixelRegions.entrySet()) {
            int texId = entry.getKey();
            int[] pr = entry.getValue();
            int idx = texIdToIdx.get(texId);
            BufferedImage srcImg = images.get(idx);
            int[] ti = tileInfo.getOrDefault(texId, new int[]{1, 1});
            int tU = ti[0], tV = ti[1];
            boolean isRotated = rotated.contains(texId);

            if (isRotated) {
                // Rotated 90° CCW: swap tile counts, rotate source image
                int tmp = tU; tU = tV; tV = tmp;
                srcImg = rotateImage90CCW(srcImg);
            }

            if (tU == 1 && tV == 1) {
                g.drawImage(srcImg, pr[0], pr[1], pr[2], pr[3], null);
            } else {
                for (int ty = 0; ty < tV; ty++) {
                    for (int tx = 0; tx < tU; tx++) {
                        int x1 = pr[0] + (pr[2] * tx) / tU;
                        int y1 = pr[1] + (pr[3] * ty) / tV;
                        int x2 = pr[0] + (pr[2] * (tx + 1)) / tU;
                        int y2 = pr[1] + (pr[3] * (ty + 1)) / tV;
                        g.drawImage(srcImg, x1, y1, x2 - x1, y2 - y1, null);
                    }
                }
            }
        }
        g.dispose();
        images.clear();

        // Compute UV regions
        Map<Integer, float[]> uvRegions = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : pixelRegions.entrySet()) {
            int[] pr = e.getValue();
            float uOff = (float) pr[0] / atlasWidth;
            float vOff = (float) pr[1] / atlasHeight;
            float uScale = (float) pr[2] / atlasWidth;
            float vScale = (float) pr[3] / atlasHeight;
            uvRegions.put(e.getKey(), new float[]{uOff, vOff, uScale, vScale});
        }

        return new TextureAtlas(uvRegions, atlas, tileOffsets, rotated);
    }

    /**
     * Rotate a BufferedImage 90° counter-clockwise.
     */
    private static BufferedImage rotateImage90CCW(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform tx = new AffineTransform();
        tx.translate(0, w);
        tx.rotate(-Math.PI / 2);
        g.drawImage(src, tx, null);
        g.dispose();
        return dst;
    }

    // ---- UV remapping --------------------------------------------------------

    /**
     * Remap UV coordinates from per-texture space to atlas space.
     * <p>
     * For rotated textures, applies a 90° CCW UV rotation before the atlas
     * position transform: {@code rotU = origV, rotV = offU + rangeU - origU}.
     * <p>
     * For wrapping/tiling textures (UVs outside [0,1]), the mapping is:
     * {@code atlasUV = regionOffset + ((uv - tileOffset) / tileRange) * regionScale}
     */
    void remapUVs(TriangleMesh mesh) {
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
    }

    void write(Path target) throws IOException {
        ImageIO.write(image, "jpg", target.toFile());
    }
}
