package org.citydb.web.command;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.database.DatabaseOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.common.Name;
import org.citydb.web.config.WebOptions;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.config.feature.FeatureTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;

@Component
@CommandLine.Command(name = "mailCommand")
public class Command implements Callable<Integer> {

    @CommandLine.Option(names = "--config-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Load configuration from this file.")
    private Path configFile;

    private final WebOptions webOptions;

    private final Logger logger = LoggerManager.getInstance().getLogger(Command.class);
    private final Config config = new Config();

    @Autowired
    public Command(WebOptions webOptions) {
        this.webOptions = webOptions;
    }

    @Override
    public Integer call() {
        try {
            loadConfig();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        DatabaseOptions databaseOptions = config.get(DatabaseOptions.class);
        if (databaseOptions.getDefaultConnection().isPresent()) {
            webOptions.setDatabaseConnection(databaseOptions.getDefaultConnection().get());
        }

        FeatureTypes featureTypes = new FeatureTypes();
        featureTypes.setFeatureTypes(Collections.singletonList(new FeatureType(Name.of("test"))));
        webOptions.setFeatureTypes(featureTypes);

        return CommandLine.ExitCode.OK;
    }

    private void loadConfig() throws ExecutionException {
        logger.info("Loading configuration from file " + configFile + "...");
        try {
            config.putAll(ConfigManager.newInstance().read(configFile, Config.class, Config::new));
        } catch (Exception e) {
            throw new ExecutionException("Failed to load config file.", e);
        }
    }

}