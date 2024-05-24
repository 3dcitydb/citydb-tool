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

    public void apply(Config config) {
        WebOptions source = config.get(WebOptions.class);
        setFeatureTypes(source.featureTypes != null ? source.featureTypes : new FeatureTypes());
        setDatabaseConnection(source.databaseConnection != null? source.databaseConnection : new ConnectionDetails());
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
