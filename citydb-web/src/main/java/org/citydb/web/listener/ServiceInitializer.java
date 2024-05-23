package org.citydb.web.listener;

import org.apache.logging.log4j.Logger;
import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.ConfigManager;
import org.citydb.database.DatabaseException;
import org.citydb.database.adapter.DatabaseAdapterException;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.logging.LoggerManager;
import org.citydb.web.config.Constants;
import org.citydb.web.config.WebOptions;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.config.feature.FeatureTypes;
import org.citydb.web.util.DatabaseConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.stream.Collectors;

@Component
public class ServiceInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private final Logger logger = LoggerManager.getInstance().getLogger(ServiceInitializer.class);
    private final WebOptions webOptions;

    @Autowired
    public ServiceInitializer(WebOptions webOptions) {
        this.webOptions = webOptions;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        DatabaseConnector databaseConnector = DatabaseConnector.getInstance();

        try {
            databaseConnector.connect(webOptions.getDatabaseConnection());
        } catch (SQLException | DatabaseException | DatabaseAdapterException e) {
            throw new RuntimeException(e);
        }

        SchemaMapping schemaMapping = databaseConnector.getDatabaseManager().getAdapter().getSchemaAdapter().getSchemaMapping();

        FeatureTypes featureTypes = new FeatureTypes();
        if (featureTypes.getFeatureTypes().isEmpty()) {
            featureTypes.setFeatureTypes(
                    schemaMapping.getFeatureTypes().stream()
                            .filter(org.citydb.database.schema.FeatureType::isTopLevel)
                            .map(f -> new FeatureType(f.getName())).collect(Collectors.toList())
            );
        }
        webOptions.setFeatureTypes(featureTypes);

        logger.info("OGC API Service initialized successfully.");
    }
}
