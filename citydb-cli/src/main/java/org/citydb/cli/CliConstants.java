/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class CliConstants {
    public static final Path APP_HOME;
    public static final String APP_NAME;
    public static final String APP_VERSION;
    public static final String APP_COPYRIGHT_YEAR;
    public static final String APP_VENDOR;
    public static final String APP_COMMAND = "citydb";
    public static final Path WORKING_DIR;
    public static final String EXTENSIONS_DIR = "extensions";

    static {
        try {
            APP_HOME = getAppHome();
            WORKING_DIR = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize application directories.", e);
        }

        try (InputStream stream = CliConstants.class.getResourceAsStream("/org/citydb/cli/application.properties")) {
            Properties properties = new Properties();
            properties.load(stream);
            APP_NAME = properties.getProperty("name");
            APP_VERSION = properties.getProperty("version");
            APP_COPYRIGHT_YEAR = properties.getProperty("copyrightYear");
            APP_VENDOR = properties.getProperty("vendor");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize application properties.", e);
        }
    }

    private static Path getAppHome() throws Exception {
        Path location = Path.of(CliConstants.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(location)
                && location.getParent() != null
                && location.getParent().getFileName().toString().equals("lib")
                && location.getParent().getParent() != null) {
            return location.getParent().getParent().toAbsolutePath().normalize();
        }

        return Path.of(".").toAbsolutePath().normalize();
    }
}
