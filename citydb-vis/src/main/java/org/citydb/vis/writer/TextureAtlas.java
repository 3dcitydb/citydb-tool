/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.geometry.TriangleMesh;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Packs multiple texture images into a single atlas and remaps UV coordinates.
 * Uses shelf-based packing: textures sorted by height descending, placed in rows.
 * Atlas dimensions are capped at {@code maxAtlasSize} to stay within WebGL
 * texture size limits; when textures cannot fit in a single atlas, they are
 * partitioned into multiple atlases via {@link #buildMultiple}.
 * <p>
 * Supports tiled/wrapping textures: when CityGML UV coordinates exceed [0,1],
 * the source texture is repeated in the atlas region to cover the full UV range.
 */
class TextureAtlas {
    static final int DEFAULT_MAX_ATLAS_SIZE = 2048;

    private final Map<Integer, float[]> uvRegions;
    private final BufferedImage image;
    private final Set<Integer> containedTextureIds;
    /** Per-texture tile mapping: texId → [offsetU, offsetV, rangeU, rangeV]. */
    private final Map<Integer, float[]> tileOffsets;

    private TextureAtlas(Map<Integer, float[]> uvRegions, BufferedImage image,
                         Set<Integer> containedTextureIds,
                         Map<Integer, float[]> tileOffsets) {
        this.uvRegions = uvRegions;
        this.image = image;
        this.containedTextureIds = containedTextureIds;
        this.tileOffsets = tileOffsets;
    }

    Set<Integer> getContainedTextureIds() {
        return containedTextureIds;
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

    /**
     * Build one or more texture atlases from the given texture IDs.
     * <p>
     * If all textures fit within a single {@code maxAtlasSize} atlas at the
     * requested scale, a singleton list is returned. Otherwise the textures are
     * partitioned into groups using greedy shelf-packing, and each group is
     * built as a separate atlas — preserving the requested scale instead of
     * downscaling everything to fit in one atlas.
     *
     * @param uvExtents per-texture UV extent: texId → [minU, minV, maxU, maxV].
     */
    static List<TextureAtlas> buildMultiple(Collection<Integer> textureIds,
                                            TextureStore textureStore,
                                            double textureScale, int maxAtlasSize,
                                            Map<Integer, float[]> uvExtents) throws IOException {
        List<int[]> dims = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        Map<Integer, int[]> tileInfo = new HashMap<>();
        Map<Integer, float[]> tileOffsets = new HashMap<>();

        loadAndPrepareTiled(textureIds, textureStore, uvExtents, dims, images,
                tileInfo, tileOffsets);
        if (dims.isEmpty()) return List.of();

        double scale = Math.min(1.0, Math.max(0.01, textureScale));

        if (dims.size() == 1) {
            TextureAtlas atlas = buildSingleAtlas(dims, images, scale, maxAtlasSize, tileInfo, tileOffsets);
            return atlas != null ? List.of(atlas) : List.of();
        }

        // Sort by height descending for shelf packing
        Integer[] order = new Integer[dims.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Integer.compare(dims.get(b)[2], dims.get(a)[2]));

        // Check if single atlas is sufficient
        int[] trialSize = computePackedSize(dims, order, scale, maxAtlasSize);
        if (trialSize[0] <= maxAtlasSize && trialSize[1] <= maxAtlasSize) {
            TextureAtlas atlas = buildSingleAtlas(dims, images, scale, maxAtlasSize, tileInfo, tileOffsets);
            return atlas != null ? List.of(atlas) : List.of();
        }

        // Partition textures into groups where each group fits within maxAtlasSize
        List<List<Integer>> groups = new ArrayList<>();
        List<Integer> currentGroup = new ArrayList<>();
        int gShelfX = 0, gShelfY = 0, gShelfH = 0, gMaxW = 0;

        for (int idx : order) {
            int w = Math.max(1, (int) (dims.get(idx)[1] * scale));
            int h = Math.max(1, (int) (dims.get(idx)[2] * scale));

            int sX = gShelfX, sY = gShelfY, sH = gShelfH;
            if (sX + w > maxAtlasSize && sX > 0) {
                sY += sH;
                sX = 0;
                sH = 0;
            }
            sX += w;
            sH = Math.max(sH, h);
            int mW = Math.max(gMaxW, sX);

            boolean fits = mW <= maxAtlasSize && (sY + sH) <= maxAtlasSize;

            if (!fits && !currentGroup.isEmpty()) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                gShelfX = w;
                gShelfY = 0;
                gShelfH = h;
                gMaxW = w;
                currentGroup.add(idx);
            } else {
                gShelfX = sX;
                gShelfY = sY;
                gShelfH = sH;
                gMaxW = mW;
                currentGroup.add(idx);
            }
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        // Build each group as a separate atlas
        List<TextureAtlas> result = new ArrayList<>();
        for (List<Integer> group : groups) {
            List<int[]> groupDims = new ArrayList<>();
            List<BufferedImage> groupImages = new ArrayList<>();
            for (int idx : group) {
                groupDims.add(dims.get(idx));
                groupImages.add(images.get(idx));
            }
            TextureAtlas atlas = buildSingleAtlas(groupDims, groupImages, scale,
                    maxAtlasSize, tileInfo, tileOffsets);
            if (atlas != null) {
                result.add(atlas);
            }
        }
        return result;
    }

    // ---- Image loading and tiling preparation --------------------------------

    /**
     * Load texture images and compute tiling information from UV extents.
     * <p>
     * Populates {@code dims} with <b>effective</b> (tiled) dimensions so that
     * shelf packing allocates enough space for all tiles.
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
            Set<Integer> contained = Set.of(texId);
            int[] ti = tileInfo.getOrDefault(texId, new int[]{1, 1});
            if (ti[0] == 1 && ti[1] == 1) {
                // No tiling — return single image as-is
                Map<Integer, float[]> regions = Map.of(texId, new float[]{0f, 0f, 1f, 1f});
                return new TextureAtlas(regions, TextureStore.toOpaqueRgb(images.get(0)),
                        contained, tileOffsets);
            }
            // Single texture with tiling — fall through to atlas compositing
        }

        // Sort by effective height descending for shelf packing
        Integer[] order = new Integer[dims.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Integer.compare(dims.get(b)[2], dims.get(a)[2]));

        int[] trialSize = computePackedSize(dims, order, scale, maxAtlasSize);

        // Scale down uniformly if atlas exceeds WebGL max texture size
        if (trialSize[0] > maxAtlasSize || trialSize[1] > maxAtlasSize) {
            scale = Math.min(
                    (double) maxAtlasSize / trialSize[0],
                    (double) maxAtlasSize / trialSize[1]);
            int[] check = computePackedSize(dims, order, scale, maxAtlasSize);
            if (check[0] > maxAtlasSize || check[1] > maxAtlasSize) {
                scale *= Math.min(
                        (double) maxAtlasSize / check[0],
                        (double) maxAtlasSize / check[1]);
            }
        }

        // Final pack with computed scale
        Map<Integer, int[]> pixelRegions = new LinkedHashMap<>();
        int atlasWidth = 0, atlasHeight = 0;
        int shelfX = 0, shelfY = 0, shelfH = 0;
        for (int idx : order) {
            int[] d = dims.get(idx);
            int w = Math.max(1, (int) (d[1] * scale));
            int h = Math.max(1, (int) (d[2] * scale));
            if (shelfX + w > maxAtlasSize && shelfX > 0) {
                shelfY += shelfH;
                shelfX = 0;
                shelfH = 0;
            }
            pixelRegions.put(d[0], new int[]{shelfX, shelfY, w, h});
            shelfX += w;
            atlasWidth = Math.max(atlasWidth, shelfX);
            shelfH = Math.max(shelfH, h);
        }
        atlasHeight = shelfY + shelfH;

        // Compose atlas image — draw each texture tiled into its allocated region
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, atlasWidth, atlasHeight);

        for (int idx : order) {
            int[] d = dims.get(idx);
            int[] pr = pixelRegions.get(d[0]);
            int[] ti = tileInfo.getOrDefault(d[0], new int[]{1, 1});
            int tU = ti[0], tV = ti[1];

            if (tU == 1 && tV == 1) {
                // No tiling — draw source image scaled to region
                g.drawImage(images.get(idx), pr[0], pr[1], pr[2], pr[3], null);
            } else {
                // Draw source image tiled tU × tV times within the region
                for (int ty = 0; ty < tV; ty++) {
                    for (int tx = 0; tx < tU; tx++) {
                        int x1 = pr[0] + (pr[2] * tx) / tU;
                        int y1 = pr[1] + (pr[3] * ty) / tV;
                        int x2 = pr[0] + (pr[2] * (tx + 1)) / tU;
                        int y2 = pr[1] + (pr[3] * (ty + 1)) / tV;
                        g.drawImage(images.get(idx), x1, y1, x2 - x1, y2 - y1, null);
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

        Set<Integer> contained = new HashSet<>();
        for (int[] d : dims) contained.add(d[0]);
        return new TextureAtlas(uvRegions, atlas, contained, tileOffsets);
    }

    private static int[] computePackedSize(List<int[]> dims, Integer[] order, double scale,
                                              int maxAtlasSize) {
        int shelfX = 0, shelfY = 0, shelfH = 0, maxW = 0;
        for (int idx : order) {
            int w = Math.max(1, (int) (dims.get(idx)[1] * scale));
            int h = Math.max(1, (int) (dims.get(idx)[2] * scale));
            if (shelfX + w > maxAtlasSize && shelfX > 0) {
                shelfY += shelfH;
                shelfX = 0;
                shelfH = 0;
            }
            shelfX += w;
            maxW = Math.max(maxW, shelfX);
            shelfH = Math.max(shelfH, h);
        }
        return new int[]{maxW, shelfY + shelfH};
    }

    // ---- UV remapping --------------------------------------------------------

    /**
     * Remap UV coordinates from per-texture space to atlas space.
     * <p>
     * For wrapping/tiling textures (UVs outside [0,1]), the mapping is:
     * {@code atlasUV = regionOffset + ((origUV - tileOffset) / tileRange) * regionScale}
     * <p>
     * For non-tiling textures (UVs in [0,1]), this simplifies to the standard:
     * {@code atlasUV = regionOffset + origUV * regionScale}
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

        // Default: no offset, range=1 (identity for non-tiling textures)
        float[] defaultTile = {0f, 0f, 1f, 1f};

        List<float[]> texCoords = mesh.getTexCoords();
        for (int v = 0; v < vertexCount; v++) {
            int texId = vertexTexId[v];
            if (texId >= 0) {
                float[] region = uvRegions.get(texId);
                if (region != null) {
                    float[] tile = tileOffsets.getOrDefault(texId, defaultTile);
                    float offsetU = tile[0], offsetV = tile[1];
                    float rangeU = tile[2], rangeV = tile[3];

                    float[] uv = texCoords.get(v);
                    uv[0] = region[0] + ((uv[0] - offsetU) / rangeU) * region[2];
                    uv[1] = region[1] + ((uv[1] - offsetV) / rangeV) * region[3];
                }
            }
        }
    }

    void write(Path target) throws IOException {
        ImageIO.write(image, "jpg", target.toFile());
    }

    void writeDds(Path target) throws IOException {
        DdsEncoder.writeImage(image, target);
    }
}
