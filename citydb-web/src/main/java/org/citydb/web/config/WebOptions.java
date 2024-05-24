package org.citydb.web.config;

import org.citydb.config.Config;
import org.citydb.config.SerializableConfig;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.web.config.feature.FeatureTypes;
import org.springframework.context.annotation.Configuration;

@SerializableConfig(name = "webOptions")
@Configuration
public class WebOptions {
    private FeatureTypes featureTypes;
    private ConnectionDetails databaseConnection;

    public WebOptions() {
        featureTypes = new FeatureTypes();
        databaseConnection = new ConnectionDetails();
    }

    public void apply(Config config) {
        WebOptions source = config.getOrElse(WebOptions.class, WebOptions::new);
        setFeatureTypes(source.featureTypes);
        setDatabaseConnection(source.databaseConnection);
    }

    public FeatureTypes getFeatureTypes() {
        return featureTypes;
    }

    public void setFeatureTypes(FeatureTypes featureTypes) {
        this.featureTypes = featureTypes;
    }

    public ConnectionDetails getDatabaseConnection() {
        return databaseConnection;
    }

    public void setDatabaseConnection(ConnectionDetails databaseConnection) {
        this.databaseConnection = databaseConnection;
    }
}
