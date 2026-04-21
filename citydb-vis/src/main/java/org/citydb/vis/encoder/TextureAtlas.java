/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder;

import org.citydb.textureAtlas.TextureAtlasCreator;
import org.citydb.textureAtlas.model.AtlasRegion;
import org.citydb.textureAtlas.packer.Packer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.store.TextureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class TextureAtlas {
    private static final Logger logger = LoggerFactory.getLogger(TextureAtlas.class);

    /**
     * Sentinel texture id for the reserved solid-white region appended to the
     * atlas for mixed nodes (some features textured, some not). The I3S path
     * keeps a single node-level textured material, so untextured triangles
     * need UVs that sample a guaranteed-white pixel to render as the default
     * PBR color.
     */
    static final int WHITE_PIXEL_TEX_ID = Integer.MIN_VALUE;
    private static final int WHITE_PIXEL_SIZE = 4;

    private final Map<Integer, float[]> uvRegions;
    private final BufferedImage image;
    /** Per-texture tile mapping: texId → [offsetU, offsetV, rangeU, rangeV]. */
    private final Map<Integer, float[]> tileOffsets;
    /** Texture IDs that were rotated 90° CCW by the packer. */
    private final Set<Integer> rotatedTextureIds;
    /** Atlas-space UV of the reserved white pixel center, or {@code null}. */
    private final float[] whitePixelUV;

    private TextureAtlas(Map<Integer, float[]> uvRegions, BufferedImage image,
                         Map<Integer, float[]> tileOffsets,
                         Set<Integer> rotatedTextureIds,
                         float[] whitePixelUV) {
        this.uvRegions = uvRegions;
        this.image = image;
        this.tileOffsets = tileOffsets;
        this.rotatedTextureIds = rotatedTextureIds;
        this.whitePixelUV = whitePixelUV;
    }

    /**
     * Build a single texture atlas from the given texture IDs.
     *
     * @param uvExtents       per-texture UV extent: texId → [minU, minV, maxU, maxV].
     *                        Used to compute tiling for wrapping textures.
     * @param needsWhitePixel reserve a small solid-white region so untextured
     *                        triangles in a mixed node can sample a
     *                        known-white color while sharing the textured
     *                        material.
     */
    public static TextureAtlas build(Collection<Integer> textureIds, TextureStore textureStore,
                                     double textureScale, int maxAtlasSize,
                                     Map<Integer, float[]> uvExtents,
                                     boolean needsWhitePixel) throws IOException {
        List<TextureMeta> metas = new ArrayList<>();
        Map<Integer, float[]> tileOffsets = new HashMap<>();

        gatherMetaTiled(textureIds, textureStore, uvExtents, metas, tileOffsets);
        if (metas.isEmpty()) return null;

        if (needsWhitePixel) {
            metas.add(new TextureMeta(WHITE_PIXEL_TEX_ID,
                    WHITE_PIXEL_SIZE, WHITE_PIXEL_SIZE, 1, 1));
            tileOffsets.put(WHITE_PIXEL_TEX_ID, new float[]{0f, 0f, 1f, 1f});
        }

        double scale = Math.min(1.0, Math.max(0.01, textureScale));
        return buildSingleAtlas(metas, scale, maxAtlasSize, tileOffsets, textureStore);
    }

    // ---- Metadata gathering (no pixel decode) -------------------------------

    /**
     * Per-texture metadata used during packing. Holds the source dimensions
     * read from file headers plus any tiling derived from UV extents.
     * Source pixels are <b>not</b> decoded here — decoding happens JIT during
     * compositing with an appropriate subsampling factor.
     */
    private record TextureMeta(int texId, int srcWidth, int srcHeight,
                               int tilesU, int tilesV) {
        int effectiveWidth() { return srcWidth * tilesU; }
        int effectiveHeight() { return srcHeight * tilesV; }
    }

    /**
     * Read per-texture dimensions (no pixel decode) and compute tiling from
     * UV extents. Decoupling this from the pixel decode phase means we never
     * hold more than one source bitmap resident at a time during compositing.
     */
    private static void gatherMetaTiled(
            Collection<Integer> textureIds, TextureStore textureStore,
            Map<Integer, float[]> uvExtents,
            List<TextureMeta> metas, Map<Integer, float[]> tileOffsets) throws IOException {

        for (int texId : textureIds) {
            int[] size;
            try {
                size = textureStore.readDimensions(texId);
            } catch (IOException e) {
                logger.warn("Skipping corrupt texture {} ({}): {}",
                        texId, textureStore.getSourcePath(texId), e.getMessage());
                continue;
            }
            if (size == null) continue;
            int origW = size[0];
            int origH = size[1];

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

            metas.add(new TextureMeta(texId, origW, origH, tilesU, tilesV));
            tileOffsets.put(texId, new float[]{offsetU, offsetV, rangeU, rangeV});
        }
    }

    // ---- Core atlas builder --------------------------------------------------

    /**
     * Maximum subsampling factor passed to the decoder. JPEG's libjpeg fast
     * path supports 1, 2, 4, 8 via DCT-level downscaling; larger values fall
     * back to generic pixel skipping and are rarely needed (source > 8 ×
     * {@code maxAtlasSize}). Anything beyond is handled by {@code drawImage}.
     */
    private static final int MAX_DECODE_SUBSAMPLE = 8;

    /**
     * Core atlas builder. Works in two phases:
     * <ol>
     *   <li>Run the packer on metadata only (no pixel decode) to determine
     *       each texture's target region in the atlas.</li>
     *   <li>Iterate the packed regions; for each, decode the source with an
     *       appropriate subsampling factor (so a 16K source destined for a
     *       512-pixel atlas region decodes at ≤ 2K), draw into the atlas,
     *       and let the bitmap fall out of scope before the next texture.</li>
     * </ol>
     * This keeps the resident BufferedImage footprint to one source at a
     * time — independent of how many textures the node references or how
     * large each source happens to be.
     *
     * @param metas       per-texture metadata (source dims + tiling)
     * @param scale       user-requested texture scale
     * @param tileOffsets texId → [offsetU, offsetV, rangeU, rangeV]
     */
    private static TextureAtlas buildSingleAtlas(List<TextureMeta> metas,
                                                  double scale, int maxAtlasSize,
                                                  Map<Integer, float[]> tileOffsets,
                                                  TextureStore textureStore) throws IOException {
        if (metas.size() == 1) {
            TextureMeta m = metas.get(0);
            if (m.tilesU == 1 && m.tilesV == 1) {
                return buildSingleTextureAtlas(m, maxAtlasSize, tileOffsets, textureStore);
            }
            // Single texture with tiling — fall through to atlas compositing
        }

        // Pack with BASIC (Lightmap/BSP) — O(n log n), may rotate textures
        Map<Integer, Integer> texIdToIdx = new HashMap<>();
        Packer packer = new Packer(maxAtlasSize, maxAtlasSize,
                TextureAtlasCreator.BASIC, false);
        for (int i = 0; i < metas.size(); i++) {
            TextureMeta m = metas.get(i);
            int w = Math.max(1, (int) (m.effectiveWidth() * scale));
            int h = Math.max(1, (int) (m.effectiveHeight() * scale));
            packer.addRegion(String.valueOf(m.texId), w, h);
            texIdToIdx.put(m.texId, i);
        }
        org.citydb.textureAtlas.model.TextureAtlas packed = packer.pack(false);

        // When the BASIC packer cannot fit everything on a single page it
        // spills the rest onto additional "roots" whose coordinates all
        // restart at (0,0) and are only distinguished by AtlasRegion.level.
        // Since we composite every region into one BufferedImage, a level>0
        // region would overwrite a level 0 region at the same (x,y) and the
        // affected texId's UV remap would then sample whichever texture was
        // drawn last — visible as unrelated buildings stealing each other's
        // facade textures. Scale down and repack iteratively until every
        // region lands on level 0. One rescale pass is insufficient because
        // BSP fragmentation plus rotation can still overflow at the 0.9
        // safety factor; tighten by an extra 0.85× each attempt to converge.
        final double minScale = 0.01;
        final int maxRetries = 10;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            boolean hasOverflow = false;
            for (AtlasRegion r : packed.getRegions()) {
                if (r.level > 0) { hasOverflow = true; break; }
            }
            if (!hasOverflow) break;

            long totalArea = 0;
            for (TextureMeta m : metas) {
                totalArea += (long) Math.max(1, (int) (m.effectiveWidth() * scale))
                        * Math.max(1, (int) (m.effectiveHeight() * scale));
            }
            double areaRatio = (double) ((long) maxAtlasSize * maxAtlasSize) / totalArea;
            double factor = Math.min(1.0, areaRatio * 0.9) * Math.pow(0.85, attempt);
            double nextScale = Math.max(minScale, scale * Math.sqrt(factor));
            if (nextScale >= scale) break;
            scale = nextScale;

            packer = new Packer(maxAtlasSize, maxAtlasSize,
                    TextureAtlasCreator.BASIC, false);
            for (TextureMeta m : metas) {
                int w = Math.max(1, (int) (m.effectiveWidth() * scale));
                int h = Math.max(1, (int) (m.effectiveHeight() * scale));
                packer.addRegion(String.valueOf(m.texId), w, h);
            }
            packed = packer.pack(false);
            if (scale <= minScale) break;
        }

        // Collect placement results and track rotated textures. Skip any
        // region still at level > 0 after the retry loop — compositing it
        // into the single atlas image would corrupt overlapping level 0
        // regions. Their textures won't render correctly, but this is
        // preferable to silently painting the wrong facade on other
        // buildings.
        int atlasWidth = 0, atlasHeight = 0;
        Map<Integer, int[]> pixelRegions = new LinkedHashMap<>();
        Set<Integer> rotated = new HashSet<>();
        int dropped = 0;
        for (AtlasRegion r : packed.getRegions()) {
            if (r.level > 0) {
                dropped++;
                continue;
            }
            int texId = Integer.parseInt(r.texImageName);
            pixelRegions.put(texId, new int[]{r.x, r.y, r.width, r.height});
            atlasWidth = Math.max(atlasWidth, r.x + r.width);
            atlasHeight = Math.max(atlasHeight, r.y + r.height);
            if (r.isRotated) {
                rotated.add(texId);
            }
        }
        if (dropped > 0) {
            logger.warn("Atlas overflow could not be resolved by rescaling; " +
                    "dropped {} of {} textures (scale={}). Consider lowering " +
                    "texture scale or reducing node feature count.",
                    dropped, metas.size(), scale);
        }

        // Compose atlas image — decode each source JIT with subsampling
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
            if (texId == WHITE_PIXEL_TEX_ID) {
                // Atlas is already cleared to white; nothing to composite.
                continue;
            }
            TextureMeta m = metas.get(texIdToIdx.get(texId));
            boolean isRotated = rotated.contains(texId);

            int tU = m.tilesU, tV = m.tilesV;
            if (isRotated) { int tmp = tU; tU = tV; tV = tmp; }

            // Per-tile target pixel dimensions in atlas; in rotated layout,
            // source width maps to atlas height and vice versa.
            int targetTileW = Math.max(1, pr[2] / Math.max(1, tU));
            int targetTileH = Math.max(1, pr[3] / Math.max(1, tV));
            int matchSrcW = isRotated ? targetTileH : targetTileW;
            int matchSrcH = isRotated ? targetTileW : targetTileH;
            int subsample = pickSubsample(m.srcWidth, m.srcHeight, matchSrcW, matchSrcH);

            BufferedImage srcImg;
            try {
                srcImg = textureStore.loadImage(texId, subsample);
            } catch (IOException e) {
                logger.warn("Skipping corrupt texture {} ({}): {}",
                        texId, textureStore.getSourcePath(texId), e.getMessage());
                continue;
            }
            if (srcImg == null) continue;

            if (isRotated) {
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
            // srcImg leaves scope here; atlas has its pixels, source is GC-eligible
        }
        g.dispose();

        // Compute UV regions
        Map<Integer, float[]> uvRegions = new HashMap<>();
        float[] whitePixelUV = null;
        for (Map.Entry<Integer, int[]> e : pixelRegions.entrySet()) {
            int[] pr = e.getValue();
            float uOff = (float) pr[0] / atlasWidth;
            float vOff = (float) pr[1] / atlasHeight;
            float uScale = (float) pr[2] / atlasWidth;
            float vScale = (float) pr[3] / atlasHeight;
            if (e.getKey() == WHITE_PIXEL_TEX_ID) {
                whitePixelUV = new float[]{uOff + uScale * 0.5f, vOff + vScale * 0.5f};
            } else {
                uvRegions.put(e.getKey(), new float[]{uOff, vOff, uScale, vScale});
            }
        }

        return new TextureAtlas(uvRegions, atlas, tileOffsets, rotated, whitePixelUV);
    }

    /**
     * Fast path for a single untiled texture: decode subsampled to at most
     * {@code maxAtlasSize}, further downscale via {@code drawImage} if the
     * decoder can't subsample enough (rare; only for 8×maxAtlasSize+ sources).
     * Never allocates the full-resolution bitmap.
     */
    private static TextureAtlas buildSingleTextureAtlas(TextureMeta m, int maxAtlasSize,
                                                        Map<Integer, float[]> tileOffsets,
                                                        TextureStore textureStore) throws IOException {
        int subsample = pickSubsample(m.srcWidth, m.srcHeight, maxAtlasSize, maxAtlasSize);
        BufferedImage src;
        try {
            src = textureStore.loadImage(m.texId, subsample);
        } catch (IOException e) {
            logger.warn("Skipping corrupt texture {} ({}): {}",
                    m.texId, textureStore.getSourcePath(m.texId), e.getMessage());
            return null;
        }
        if (src == null) return null;

        BufferedImage finalImg;
        if (src.getWidth() <= maxAtlasSize && src.getHeight() <= maxAtlasSize) {
            finalImg = TextureStore.toOpaqueRgb(src);
        } else {
            double s = (double) maxAtlasSize
                    / Math.max(src.getWidth(), src.getHeight());
            int tw = Math.max(1, (int) (src.getWidth() * s));
            int th = Math.max(1, (int) (src.getHeight() * s));
            finalImg = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = finalImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.drawImage(src, 0, 0, tw, th, null);
            g.dispose();
        }
        Map<Integer, float[]> regions = Map.of(m.texId, new float[]{0f, 0f, 1f, 1f});
        return new TextureAtlas(regions, finalImg, tileOffsets, Set.of(), null);
    }

    /**
     * Pick a decoder subsampling factor so that a source of size
     * {@code srcW × srcH} decodes at roughly {@code targetW × targetH} or the
     * next size up (never below target — the final {@code drawImage} still
     * fills any remaining gap). Clamped to a power of two ≤ 8 so JPEG uses
     * its DCT-level fast path.
     */
    private static int pickSubsample(int srcW, int srcH, int targetW, int targetH) {
        int rx = srcW / Math.max(1, targetW);
        int ry = srcH / Math.max(1, targetH);
        int ratio = Math.max(1, Math.min(rx, ry));
        int sub = Integer.highestOneBit(ratio);
        return Math.max(1, Math.min(sub, MAX_DECODE_SUBSAMPLE));
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
        // at the reserved white pixel so the single textured material renders
        // those features as solid white, matching the untextured-node PBR default.
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

    /** Atlas-space UV center of the reserved white pixel, or {@code null}. */
    public float[] getWhitePixelUV() {
        return whitePixelUV;
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
}
