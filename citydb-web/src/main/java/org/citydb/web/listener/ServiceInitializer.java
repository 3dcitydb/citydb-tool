package org.citydb.web.listener;

import org.apache.logging.log4j.Logger;
import org.citydb.database.DatabaseException;
import org.citydb.logging.LoggerManager;
import org.citydb.web.config.WebOptions;
import org.citydb.web.config.feature.FeatureType;
import org.citydb.web.config.feature.FeatureTypes;
import org.citydb.web.util.DatabaseConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.sql.SQLException;

@Component
public class ServiceInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private final Logger logger = LoggerManager.getInstance().getLogger(ServiceInitializer.class);
    private final WebOptions webOptions = WebOptions.getInstance();
    private final Environment environment;

    @Autowired
    public ServiceInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        DatabaseConnector databaseConnector = DatabaseConnector.getInstance();
        try {
            databaseConnector.connect();
        } catch (SQLException | DatabaseException e) {
            throw new RuntimeException(e);
        }

        String address = InetAddress.getLoopbackAddress().getHostName() +
                ":" + environment.getProperty("local.server.port")
                + "/ogcapi";
        webOptions.setCurrentAddress(address);

        FeatureTypes featureTypes = new FeatureTypes();
        featureTypes.getFeatureTypes().add(new FeatureType(org.citydb.model.feature.FeatureType.BUILDING.getName()));
        webOptions.setFeatureTypes(featureTypes);

        logger.info("OGC API Service initialized successfully.");
    }
}
