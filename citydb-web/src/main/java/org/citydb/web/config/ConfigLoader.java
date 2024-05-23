package org.citydb.web.config;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.database.DatabaseOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.common.Name;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.config.feature.FeatureTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;

@Component
public class ConfigLoader {
    private final WebOptions webOptions;
    private final Logger logger = LoggerManager.getInstance().getLogger(ConfigLoader.class);

    @Autowired
    public ConfigLoader(WebOptions webOptions) {
        this.webOptions = webOptions;
    }

    public WebOptions getWebOptions() {
        return webOptions;
    }

    public void loadConfig(Path configFile) throws ExecutionException {
        Config config = new Config();
        logger.info("Loading configuration from file " + configFile + "...");
        try {
            config.putAll(ConfigManager.newInstance().read(configFile, Config.class, Config::new));
        } catch (Exception e) {
            throw new ExecutionException("Failed to load config file.", e);
        }

        DatabaseOptions databaseOptions = config.get(DatabaseOptions.class);
        if (databaseOptions.getDefaultConnection().isPresent()) {
            webOptions.setDatabaseConnection(databaseOptions.getDefaultConnection().get());
        }

        FeatureTypes featureTypes = new FeatureTypes();
        featureTypes.setFeatureTypes(Collections.singletonList(new FeatureType(Name.of("test"))));
        webOptions.setFeatureTypes(featureTypes);
    }
}
