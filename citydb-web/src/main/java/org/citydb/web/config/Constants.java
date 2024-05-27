package org.citydb.web.config;

public class Constants {
	public static final String SERVICE_CONTEXT_PATH = "/ogcapi";
	public static final String CONFIG_FILE;

	public static final String GEOJSON_MEDIA_TYPE = "application/json";
	public static final String CITYGML_MEDIA_TYPE = "application/gml+xml";
	static {
		if (System.getenv("WEB_CONFIG_FILE") != null) {
			CONFIG_FILE = System.getenv("WEB_CONFIG_FILE");
		} else {
			CONFIG_FILE = "WEB-INF/config.json";
		}
	}
}