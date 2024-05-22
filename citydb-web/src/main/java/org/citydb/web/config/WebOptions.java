package org.citydb.web.config;

import org.citydb.config.SerializableConfig;
import org.citydb.web.config.feature.FeatureTypes;

@SerializableConfig(name = "webOptions")
public class WebOptions {
    private String currentAddress;
    private FeatureTypes featureTypes;

    private static final WebOptions instance = new WebOptions();

    public WebOptions() {
        featureTypes = new FeatureTypes();
    }

    public static WebOptions getInstance() {
        return instance;
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
