package org.citydb.io.ifc.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class GeometryJsonLoader {

    private static final Logger logger = LoggerFactory.getLogger(GeometryJsonLoader.class);

    private Map<String, List<double[]>> geometryCache = null;
    private Map<String, List<double[]>> materialCache = null;

    public void load(String inputPath, boolean reorientShells) {
        try {
            File tempFile = Files.createTempFile("citydb-ifc-", ".geom.json").toFile();
            tempFile.deleteOnExit();

            String resultPath = runPythonGeometryExtraction(
                    inputPath, tempFile.getPath(), reorientShells);
            if (resultPath != null) {
                loadJsonGeometry(resultPath);
            }
            tempFile.delete();
        } catch (IOException e) {
            logger.warn("Failed to load JSON geometry: {}", e.getMessage());
        }
    }

    public Map<String, List<double[]>> getGeometryCache() {
        return geometryCache;
    }

    public Map<String, List<double[]>> getMaterialCache() {
        return materialCache;
    }

    @SuppressWarnings("unchecked")
    private void loadJsonGeometry(String jsonPath) throws IOException {
        logger.info("Loading geometry from JSON: {}", jsonPath);
        try (FileReader reader = new FileReader(jsonPath)) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<
                    Map<String, Object>>(){}.getType();
            Map<String, Object> raw = gson.fromJson(reader, type);

            geometryCache = new HashMap<>();
            int materialsCount = 0;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object value = entry.getValue();
                List<?> polyLists;
                List<?> matLists = null;

                if (value instanceof Map) {
                    Map<String, Object> obj = (Map<String, Object>) value;
                    polyLists = (List<?>) obj.get("polygons");
                    Object matsObj = obj.get("materials");
                    if (matsObj instanceof List) {
                        matLists = (List<?>) matsObj;
                    }
                } else if (value instanceof List) {
                    polyLists = (List<?>) value;
                } else {
                    continue;
                }

                if (polyLists != null) {
                    List<double[]> polygons = new ArrayList<>();
                    for (Object polyObj : polyLists) {
                        List<?> poly = (List<?>) polyObj;
                        double[] arr = new double[poly.size()];
                        for (int i = 0; i < poly.size(); i++) {
                            arr[i] = ((Number) poly.get(i)).doubleValue();
                        }
                        polygons.add(arr);
                    }
                    geometryCache.put(entry.getKey(), polygons);

                    if (matLists != null) {
                        if (materialCache == null) materialCache = new HashMap<>();
                        List<double[]> materials = new ArrayList<>();
                        boolean hasMaterials = false;
                        for (Object mat : matLists) {
                            if (mat instanceof List<?> matValues) {
                                double[] arr = new double[matValues.size()];
                                for (int i = 0; i < matValues.size(); i++) {
                                    arr[i] = ((Number) matValues.get(i)).doubleValue();
                                }
                                materials.add(arr);
                                hasMaterials = true;
                            } else {
                                materials.add(null);
                            }
                        }
                        if (hasMaterials) {
                            materialCache.put(entry.getKey(), materials);
                            materialsCount++;
                        }
                    }
                }
            }
            logger.info("Loaded JSON geometry for {} elements ({} with materials)",
                    geometryCache.size(), materialsCount);
        }
    }

    private String runPythonGeometryExtraction(String ifcPath, String jsonOutputPath,
                                               boolean reorientShells) {
        String scriptName = "extract_geometry.py";
        File scriptFile = null;

        // Try relative to the code location
        try {
            java.net.URL codeUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
            File dir = new File(codeUrl.toURI());
            // Walk up from code source, looking for src/main/python/ (IDE and Gradle run)
            // and python/ (distribution layout: APP_HOME/lib/app.jar -> APP_HOME/python/)
            for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
                File candidate = new File(new File(dir, "src/main/python"), scriptName);
                if (candidate.exists()) {
                    scriptFile = candidate;
                    break;
                }
                candidate = new File(new File(dir, "python"), scriptName);
                if (candidate.exists()) {
                    scriptFile = candidate;
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Try current working directory
        if (scriptFile == null) {
            File cwd = new File(scriptName);
            if (cwd.exists()) scriptFile = cwd;
        }

        if (scriptFile == null) {
            logger.info("extract_geometry.py not found, skipping Python geometry extraction");
            return null;
        }

        logger.info("Running Python geometry extraction: {} -> {}", ifcPath, jsonOutputPath);

        for (String pythonCmd : new String[]{"python3", "py", "python"}) {
            try {
                List<String> command = new ArrayList<>();
                command.add(pythonCmd);
                command.add(scriptFile.getAbsolutePath());
                command.add(ifcPath);
                command.add(jsonOutputPath);
                if (reorientShells) {
                    command.add("--reorient-shells");
                }
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0 && new File(jsonOutputPath).exists()) {
                    logger.info("Python geometry extraction completed successfully");
                    return jsonOutputPath;
                } else if (exitCode == 9009) {
                    continue;
                } else {
                    logger.warn("Python geometry extraction failed (exit code {})", exitCode);
                    return null;
                }
            } catch (IOException e) {
                // command not found, try next
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Python geometry extraction interrupted");
                return null;
            }
        }
        logger.info("Python not available, skipping geometry extraction");
        return null;
    }
}
