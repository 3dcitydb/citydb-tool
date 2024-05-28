package org.citydb.web.config;

import org.citydb.config.Config;
import org.citydb.config.ConfigException;
import org.citydb.config.SerializableConfig;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.web.config.feature.FeatureType;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@SerializableConfig(name = "webOptions")
@Configuration
public class WebOptions {
    private Map<String, FeatureType> featureTypes;
    private ConnectionDetails databaseConnection;

    public WebOptions() {
        featureTypes = new HashMap<>();
        databaseConnection = new ConnectionDetails();
    }

    public void apply(Config config) throws ConfigException {
        WebOptions source = config.getOrElse(WebOptions.class, WebOptions::new);
        setFeatureTypes(source.featureTypes);
        setDatabaseConnection(source.databaseConnection);
    }

    public Map<String, FeatureType> getFeatureTypes() {
        return featureTypes;
    }

    public void setFeatureTypes(Map<String, FeatureType> featureTypes) {
        this.featureTypes = featureTypes;
    }

    public ConnectionDetails getDatabaseConnection() {
        return databaseConnection;
    }

    public void setDatabaseConnection(ConnectionDetails databaseConnection) {
        this.databaseConnection = databaseConnection;
    }
}
