/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.terrain;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.VisExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/**
 * {@link TerrainElevationProvider} backed by Cesium World Terrain, served as
 * quantized-mesh tiles from Cesium ion.
 * <p>
 * Construction performs the one-time ion handshake: it exchanges the user's ion
 * access token for the World Terrain asset endpoint (a tile base URL plus a
 * short-lived asset token) and downloads the {@code layer.json} describing the
 * tiling scheme and tile availability. Both steps are fatal on failure
 * ({@link VisExportException}).
 * <p>
 * {@link #sampleHeight} picks the deepest available level covering the point,
 * fetches and decodes the containing tile (cached, see {@link #tileCache}), and
 * barycentrically interpolates the height. Tiles use the geodetic TMS scheme
 * Cesium terrain always uses: {@code 2 × 1} tiles at level 0, each level
 * halving the tile span, with the row origin at the south pole. This provider
 * stays entirely in TMS tile coordinates (Cesium internally flips TMS→north-up
 * and back; staying in TMS for both availability and tile URLs is equivalent
 * and simpler).
 * <p>
 * <b>Availability.</b> Cesium World Terrain advertises {@code metadataAvailability}
 * (a.k.a. {@code availabilityLevels}): the {@code layer.json} {@code available}
 * array describes only the top levels, and deeper availability is carried by a
 * METADATA extension embedded in the "availability tiles" at levels that are
 * multiples of {@code availabilityLevels}. When that field is present this
 * provider descends block by block — loading each availability tile (requested
 * with {@code extensions=metadata}) and reading its {@code available} ranges —
 * to reach the deepest LOD, matching CesiumJS. When the field is absent it falls
 * back to the static {@code available} array.
 * <p>
 * Tile fetch/decode errors are recoverable — they yield {@code NaN} (with a
 * single warning) so the caller can fall back to ellipsoid clamping.
 * <p>
 * Thread-safe: the {@link HttpClient} and the {@link ConcurrentHashMap} tile
 * cache tolerate concurrent {@link #sampleHeight} calls from the writer pool.
 */
public final class CesiumWorldTerrainProvider implements TerrainElevationProvider {
    private static final Logger logger = LoggerFactory.getLogger(CesiumWorldTerrainProvider.class);

    // Cesium World Terrain is ion asset id 1.
    private static final String ION_ENDPOINT =
            "https://api.cesium.com/v1/assets/1/endpoint?access_token=";
    private static final String ACCEPT_PLAIN =
            "application/vnd.quantized-mesh,application/octet-stream;q=0.9";
    private static final String ACCEPT_METADATA =
            "application/vnd.quantized-mesh;extensions=metadata,application/octet-stream;q=0.9,*/*;q=0.01";
    // Safety cap for the metadata descent when layer.json carries no maxzoom.
    private static final int DEFAULT_MAX_ZOOM = 22;

    /**
     * A decoded tile: its sampleable geometry plus, for availability tiles, the
     * METADATA {@code available} ranges (offset-indexed: entry {@code o}
     * describes level {@code tileLevel + o + 1}; each range is
     * {@code [startX, startY, endX, endY]} in TMS tile coordinates — not
     * y-flipped, matching this provider's TMS convention). {@code metaAvailable}
     * is {@code null} when the tile carries no METADATA extension.
     */
    private record TerrainTile(QuantizedMeshTile geometry, List<int[][]> metaAvailable) {
    }

    private final HttpClient httpClient;
    private final String assetToken;
    private final String tileUrlTemplate; // absolute, with {z}/{x}/{y}, version already substituted
    private final String acceptHeader;

    // Metadata-availability mode: availabilityLevels > 0 enables the descent;
    // 0 selects the static-available fallback. maxZoom bounds the descent.
    private final int availabilityLevels;
    private final int maxZoom;
    // staticAvailable[level] = ranges in TMS coordinates; only used (and only
    // non-null) when availabilityLevels == 0.
    private final List<int[][]> staticAvailable;

    private final ConcurrentHashMap<Long, TerrainTile> tileCache = new ConcurrentHashMap<>();
    // Sentinel cached for tiles that are unavailable or failed to fetch/decode,
    // so a missing tile is looked up at most once.
    private static final TerrainTile UNAVAILABLE = new TerrainTile(
            new QuantizedMeshTile(0, 0, 0, 0, 0, 0, new int[0], new int[0], new int[0], new int[0]),
            null);
    private final AtomicBoolean warnedOnMiss = new AtomicBoolean();

    public CesiumWorldTerrainProvider(String ionToken) throws VisExportException {
        if (ionToken == null || ionToken.isBlank()) {
            throw new VisExportException("A Cesium ion token is required for " +
                    "--clamp-to-ground=cesium-world-terrain.");
        }
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        JSONObject endpoint = fetchIonEndpoint(ionToken);
        this.assetToken = endpoint.getString("accessToken");
        String baseUrl = endpoint.getString("url");
        if (baseUrl == null || assetToken == null) {
            throw new VisExportException("Cesium ion endpoint response for World " +
                    "Terrain is missing 'url' or 'accessToken'.");
        }

        JSONObject layer = fetchLayerJson(baseUrl);
        this.tileUrlTemplate = buildTileUrlTemplate(baseUrl, layer);
        this.availabilityLevels = layer.getIntValue("metadataAvailability", 0);

        if (availabilityLevels > 0) {
            this.acceptHeader = ACCEPT_METADATA;
            this.staticAvailable = null;
            this.maxZoom = layer.getIntValue("maxzoom", DEFAULT_MAX_ZOOM);
            logger.info("Cesium World Terrain ready: metadata availability " +
                            "(availabilityLevels={}, maxzoom={}, base {}).",
                    availabilityLevels, maxZoom, baseUrl);
        } else {
            JSONArray levels = layer.getJSONArray("available");
            if (levels == null || levels.isEmpty()) {
                throw new VisExportException("Cesium World Terrain layer.json has neither " +
                        "'metadataAvailability' nor a non-empty 'available' array.");
            }
            this.acceptHeader = ACCEPT_PLAIN;
            this.staticAvailable = parseLevelRanges(levels);
            this.maxZoom = staticAvailable.size() - 1;
            logger.info("Cesium World Terrain ready: static availability " +
                    "({} levels, base {}).", staticAvailable.size(), baseUrl);
        }
    }

    @Override
    public double sampleHeight(double lon, double lat) {
        int deepest = availabilityLevels > 0
                ? deepestLevelViaMetadata(lon, lat)
                : maxZoom; // static mode: try from the deepest listed level, guarded per level

        // Sample from the deepest available level upward; the first tile that
        // actually contains the point wins (a tile flagged available can still
        // miss at the very edge, so falling back to a coarser level is safe).
        for (int level = deepest; level >= 0; level--) {
            int x = tileX(lon, level);
            int y = tileY(lat, level);
            if (availabilityLevels == 0 && !isAvailableStatic(level, x, y)) {
                continue;
            }
            TerrainTile tile = getTile(level, x, y);
            double h = tile.geometry().sampleHeight(lon, lat);
            if (!Double.isNaN(h)) {
                return h;
            }
        }
        warnOnMiss(lon, lat);
        return Double.NaN;
    }

    @Override
    public void close() {
        tileCache.clear();
    }

    // ---- availability -------------------------------------------------------

    /**
     * Deepest level at which {@code (lon, lat)}'s tile is available, discovered
     * by descending through the METADATA-extension availability tiles. Always
     * returns at least 0 (level 0 is the bootstrapped root).
     */
    private int deepestLevelViaMetadata(double lon, double lat) {
        int deepest = 0;
        for (int blockLevel = 0; blockLevel < maxZoom; blockLevel += availabilityLevels) {
            // The availability tile at blockLevel describes levels
            // blockLevel+1 .. blockLevel+availabilityLevels. It is itself known
            // to be available (level 0 is the root; deeper block roots are only
            // reached when the previous block confirmed availability down to them).
            TerrainTile meta = getTile(blockLevel, tileX(lon, blockLevel), tileY(lat, blockLevel));
            List<int[][]> avail = meta.metaAvailable();
            if (avail == null) {
                break;
            }
            int blockDeepest = blockLevel;
            for (int offset = 0; offset < avail.size(); offset++) {
                int level = blockLevel + offset + 1;
                if (level > maxZoom) {
                    break;
                }
                if (rangesContain(avail.get(offset), tileX(lon, level), tileY(lat, level))) {
                    blockDeepest = level;
                } else {
                    break; // terrain pyramid is nested: first miss ends the descent
                }
            }
            deepest = blockDeepest;
            if (blockDeepest < blockLevel + availabilityLevels) {
                break; // not available to the block bottom — cannot descend further
            }
        }
        return deepest;
    }

    private boolean isAvailableStatic(int level, int x, int y) {
        return level < staticAvailable.size() && rangesContain(staticAvailable.get(level), x, y);
    }

    private static boolean rangesContain(int[][] ranges, int x, int y) {
        for (int[] r : ranges) {
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) {
                return true;
            }
        }
        return false;
    }

    // ---- tile coordinates (geodetic TMS, south-origin row) ------------------

    private static int tileX(double lon, int level) {
        double span = 180.0 / (1 << level);
        int x = (int) Math.floor((lon + 180.0) / span);
        return Math.max(0, Math.min(x, (2 << level) - 1)); // 2^(level+1) columns
    }

    private static int tileY(double lat, int level) {
        double span = 180.0 / (1 << level);
        int y = (int) Math.floor((lat + 90.0) / span);
        return Math.max(0, Math.min(y, (1 << level) - 1)); // 2^level rows
    }

    // ---- ion handshake ------------------------------------------------------

    private JSONObject fetchIonEndpoint(String ionToken) throws VisExportException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ION_ENDPOINT + ionToken))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new VisExportException("Cesium ion rejected the token (HTTP " +
                        response.statusCode() + "). Check --cesium-ion-token and that the " +
                        "token has access to the Cesium World Terrain asset.");
            }
            if (response.statusCode() != 200) {
                throw new VisExportException("Cesium ion endpoint request failed (HTTP " +
                        response.statusCode() + ").");
            }
            return JSON.parseObject(new String(decompress(response.body()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new VisExportException("Failed to contact Cesium ion for World Terrain.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisExportException("Interrupted while contacting Cesium ion.", e);
        }
    }

    private JSONObject fetchLayerJson(String baseUrl) throws VisExportException {
        String url = baseUrl.endsWith("/") ? baseUrl + "layer.json" : baseUrl + "/layer.json";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + assetToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new VisExportException("Cesium World Terrain layer.json request " +
                        "failed (HTTP " + response.statusCode() + ").");
            }
            return JSON.parseObject(new String(decompress(response.body()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new VisExportException("Failed to download Cesium World Terrain layer.json.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisExportException("Interrupted while downloading layer.json.", e);
        }
    }

    private static String buildTileUrlTemplate(String baseUrl, JSONObject layer) throws VisExportException {
        JSONArray tiles = layer.getJSONArray("tiles");
        if (tiles == null || tiles.isEmpty()) {
            throw new VisExportException("Cesium World Terrain layer.json has no 'tiles' template.");
        }
        String template = tiles.getString(0);
        String version = layer.getString("version");
        if (version != null) {
            template = template.replace("{version}", version);
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + template;
    }

    /**
     * Parse a {@code [[{startX,startY,endX,endY}, ...], ...]} availability array
     * (from {@code layer.json available} or a tile's METADATA extension) into
     * {@code result[level-or-offset] = ranges}. Coordinates are kept verbatim in
     * TMS, with no y-flip (this provider works entirely in TMS).
     */
    private static List<int[][]> parseLevelRanges(JSONArray levels) {
        List<int[][]> result = new ArrayList<>(levels.size());
        for (int l = 0; l < levels.size(); l++) {
            JSONArray rects = levels.getJSONArray(l);
            int[][] levelRects = new int[rects.size()][4];
            for (int r = 0; r < rects.size(); r++) {
                JSONObject rect = rects.getJSONObject(r);
                levelRects[r][0] = rect.getIntValue("startX");
                levelRects[r][1] = rect.getIntValue("startY");
                levelRects[r][2] = rect.getIntValue("endX");
                levelRects[r][3] = rect.getIntValue("endY");
            }
            result.add(levelRects);
        }
        return result;
    }

    // ---- tile fetch + cache -------------------------------------------------

    private TerrainTile getTile(int level, int x, int y) {
        // Deliberately NOT computeIfAbsent: loadTile() does a blocking HTTP GET
        // (up to 30 s), and computeIfAbsent holds the bucket lock for the whole
        // mapping function, which would serialize unrelated tile lookups from
        // the writer pool that happen to hash into the same bucket. get → load →
        // putIfAbsent never holds a lock across I/O; the worst case is two
        // threads fetching the same tile once each on a cold race, which is
        // cheaper than throttling the whole pool.
        long key = tileKey(level, x, y);
        TerrainTile cached = tileCache.get(key);
        if (cached != null) {
            return cached;
        }
        TerrainTile loaded = loadTile(level, x, y);
        TerrainTile existing = tileCache.putIfAbsent(key, loaded);
        return existing != null ? existing : loaded;
    }

    private TerrainTile loadTile(int level, int x, int y) {
        String url = tileUrlTemplate
                .replace("{z}", Integer.toString(level))
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + assetToken)
                    .header("Accept", acceptHeader)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                logger.debug("Terrain tile {}/{}/{} unavailable (HTTP {}).",
                        level, x, y, response.statusCode());
                return UNAVAILABLE;
            }

            byte[] body = decompress(response.body());
            double tileSpan = 180.0 / (1 << level);
            double west = x * tileSpan - 180.0;
            double south = y * tileSpan - 90.0;
            QuantizedMeshDecoder.DecodedTile decoded = QuantizedMeshDecoder.decode(
                    body, west, south, west + tileSpan, south + tileSpan);

            List<int[][]> meta = null;
            if (decoded.metadataJson() != null) {
                JSONArray avail = JSON.parseObject(decoded.metadataJson()).getJSONArray("available");
                if (avail != null) {
                    meta = parseLevelRanges(avail);
                }
            }
            return new TerrainTile(decoded.tile(), meta);
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers IllegalArgumentException from the decoder
            // and any fastjson2 parse error on the METADATA JSON — all per-tile
            // recoverable, so the point falls back to a coarser level.
            logger.debug("Failed to fetch/decode terrain tile {}/{}/{}: {}",
                    level, x, y, e.getMessage());
            return UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UNAVAILABLE;
        }
    }

    /**
     * Gunzip the body when it is gzip-compressed. Cesium ion serves both the
     * {@code layer.json} / endpoint JSON and the {@code .terrain} tiles
     * gzip-encoded, and {@code java.net.http} does not auto-decompress. Detected
     * by the gzip magic bytes ({@code 0x1f 0x8b}) — neither JSON text nor the
     * quantized-mesh header ever starts with them.
     */
    private static byte[] decompress(byte[] body) throws IOException {
        boolean gzip = body.length >= 2
                && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
        if (gzip) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return in.readAllBytes();
            }
        }
        return body;
    }

    private void warnOnMiss(double lon, double lat) {
        if (warnedOnMiss.compareAndSet(false, true)) {
            logger.warn("No Cesium World Terrain height at lon={}, lat={} (and possibly " +
                    "other points); those features fall back to ellipsoid clamping (height 0). " +
                    "Further misses are not logged.", lon, lat);
        }
    }

    private static long tileKey(int level, int x, int y) {
        // level < 32 (<<56); x has up to level+1 bits, y up to level bits — both
        // fit below bit 56 for the levels CWT serves (maxzoom well under 28).
        return ((long) level << 56) | ((long) x << 28) | (y & 0x0FFFFFFFL);
    }
}
