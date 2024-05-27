package org.citydb.web.util;


import jakarta.servlet.http.HttpServletRequest;
import org.citydb.web.config.Constants;
import org.citydb.web.exception.ServiceException;

import java.net.MalformedURLException;
import java.net.URL;

public class ServerUtil {

    public static String getServiceURL(HttpServletRequest request) throws ServiceException {
        try {
            URL requestURL = new URL(request.getRequestURL().toString());
            StringBuilder serverURL = new StringBuilder(requestURL.getProtocol()).append("://").append(requestURL.getHost());
            if (requestURL.getPort() != -1 && requestURL.getPort() != requestURL.getDefaultPort())
                serverURL.append(":").append(requestURL.getPort());

            return serverURL.append(request.getContextPath()).append(Constants.SERVICE_CONTEXT_PATH).toString();
        } catch (MalformedURLException e) {
            throw new ServiceException("Failed to create server URL.", e);
        }
    }
}
