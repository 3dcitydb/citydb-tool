package org.citydb.io.ifc.reader;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.emf.Schema;
import org.bimserver.ifc.step.deserializer.Ifc4StepDeserializer;
import org.bimserver.models.ifc4.Ifc4Package;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class IfcModelLoader {

    private static final Logger logger = LoggerFactory.getLogger(IfcModelLoader.class);

    @SuppressWarnings("deprecation")
    public IfcModelInterface loadModel(String inputPath) throws Exception {
        logger.info("Loading IFC file: {}", inputPath);

        Path schemaTmpDir = Files.createTempDirectory("bimserver-schema-");
        PackageMetaData packageMetaData = new PackageMetaData(
                Ifc4Package.eINSTANCE,
                Schema.IFC4,
                schemaTmpDir
        );

        Ifc4StepDeserializer deserializer = new Ifc4StepDeserializer(Schema.IFC4);
        deserializer.init(packageMetaData);

        File ifcFile = new File(inputPath);
        File fileToRead = preprocessIfcFile(ifcFile);
        IfcModelInterface model;
        try (FileInputStream fis = new FileInputStream(fileToRead)) {
            model = deserializer.read(fis, ifcFile.getName(), fileToRead.length(), null);
        } finally {
            if (fileToRead != ifcFile) {
                Files.delete(fileToRead.toPath());
            }
            deleteDirectory(schemaTmpDir);
        }

        logger.info("IFC model loaded successfully. Schema: {}",
                model.getPackageMetaData().getSchema());
        return model;
    }

    private File preprocessIfcFile(File ifcFile) throws IOException {
        boolean needsConversion = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(ifcFile), StandardCharsets.UTF_8))) {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.contains("FILE_SCHEMA") && line.contains("IFC4X3")) {
                    needsConversion = true;
                    break;
                }
            }
        }

        if (!needsConversion) {
            return ifcFile;
        }

        logger.info("Detected IFC4X3 schema - converting header to IFC4 for BIMserver compatibility");

        Path tempFile = Files.createTempFile("ifc4x3_to_ifc4_", ".ifc");
        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(new FileInputStream(ifcFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(new FileOutputStream(tempFile.toFile()), StandardCharsets.UTF_8))) {
            String line;
            boolean inHeader = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("HEADER")) {
                    inHeader = true;
                }
                if (inHeader && line.contains("FILE_SCHEMA")) {
                    line = line.replaceAll("IFC4X3[A-Z0-9_]*", "IFC4");
                }
                if (line.startsWith("ENDSEC") && inHeader) {
                    inHeader = false;
                }
                writer.write(line);
                writer.newLine();
            }
        }

        return tempFile.toFile();
    }

    private static void deleteDirectory(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
