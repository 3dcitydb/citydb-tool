package org.citydb.web.listener;

import org.apache.logging.log4j.Logger;
import org.citydb.database.DatabaseException;
import org.citydb.logging.LoggerManager;
import org.citydb.web.util.DatabaseConnector;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class ServiceInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private final Logger logger = LoggerManager.getInstance().getLogger(ServiceInitializer.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        DatabaseConnector databaseConnector = DatabaseConnector.getInstance();
        try {
            databaseConnector.connect();
        } catch (SQLException | DatabaseException e) {
            throw new RuntimeException(e);
        }
        logger.info("OGC API Service initialized successfully.");
    }
}
