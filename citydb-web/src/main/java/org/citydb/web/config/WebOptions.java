package org.citydb.web.config;

import org.citydb.config.SerializableConfig;
import org.citydb.web.config.feature.FeatureTypes;
import org.springframework.context.annotation.Configuration;

@SerializableConfig(name = "webOptions")
@Configuration
public class WebOptions {
    private String currentAddress;
    private FeatureTypes featureTypes;

    public WebOptions() {
        featureTypes = new FeatureTypes();
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress) {
        this.currentAddress = currentAddress;
    }

    public FeatureTypes getFeatureTypes() {
        return featureTypes;
    }

    public void setFeatureTypes(FeatureTypes featureTypes) {
        this.featureTypes = featureTypes;
    }
}
