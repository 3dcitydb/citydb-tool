package org.citydb.web.listener;

import org.apache.logging.log4j.Logger;
import org.citydb.database.DatabaseException;
import org.citydb.logging.LoggerManager;
import org.citydb.web.config.WebOptions;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.util.DatabaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
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
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        DatabaseController databaseController = DatabaseController.getInstance();
        try {
            databaseController.connect(webOptions.getDatabaseConnection());
        } catch (DatabaseException e) {
            throw new RuntimeException("Failed to connect to the city database", e);
        }

        if (webOptions.getFeatureTypes().isEmpty()) {
            Map<String, FeatureType> featureTypes = databaseController.getDatabaseManager().getAdapter().getSchemaAdapter()
                    .getSchemaMapping().getFeatureTypes().stream()
                    .filter(org.citydb.database.schema.FeatureType::isTopLevel)
                    .map(f -> new FeatureType(f.getName().getLocalName(), f.getName().getNamespace()))
                    .collect(Collectors.toMap(FeatureType::getName, Function.identity()));
            webOptions.setFeatureTypes(featureTypes);
        }

        logger.info("OGC API Service initialized successfully.");
    }
}
