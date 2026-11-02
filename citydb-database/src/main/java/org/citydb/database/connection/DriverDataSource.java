/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.connection;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

public class DriverDataSource implements DataSource {
    public static final String USER_PROPERTY = "user";
    public static final String PASSWORD_PROPERTY = "password";

    private final Driver driver;
    private final String url;
    private final Properties properties;
    private volatile PrintWriter logWriter;
    private volatile int loginTimeout;

    private DriverDataSource(Driver driver, String url, Properties properties) {
        this.driver = Objects.requireNonNull(driver, "The JDBC driver must not be null.");
        this.url = Objects.requireNonNull(url, "The connection URL must not be null.");
        this.properties = copyOf(properties);
    }

    public static DriverDataSource of(Driver driver, String url, Properties properties) {
        return new DriverDataSource(driver, url, properties);
    }

    public static DriverDataSource of(Class<?> driverClass, String url, Properties properties) throws SQLException {
        Objects.requireNonNull(driverClass, "The JDBC driver class must not be null.");
        if (!Driver.class.isAssignableFrom(driverClass)) {
            throw new SQLException("The class " + driverClass.getName() + " is not a JDBC driver.");
        }

        try {
            return new DriverDataSource((Driver) driverClass.getDeclaredConstructor().newInstance(), url, properties);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new SQLException("Failed to instantiate the JDBC driver " + driverClass.getName() + ".", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connect(copyOf(properties));
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties properties = copyOf(this.properties);
        if (username != null) {
            properties.setProperty(USER_PROPERTY, username);
        }

        if (password != null) {
            properties.setProperty(PASSWORD_PROPERTY, password);
        }

        return connect(properties);
    }

    private Connection connect(Properties properties) throws SQLException {
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("The JDBC driver " + driver.getClass().getName() +
                    " does not accept the connection URL " + url + ".");
        }

        return connection;
    }

    private static Properties copyOf(Properties properties) {
        Properties copy = new Properties();
        if (properties != null) {
            copy.putAll(properties);
        }

        return copy;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return driver.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        } else if (iface.isInstance(driver)) {
            return iface.cast(driver);
        } else {
            throw new SQLException("Cannot unwrap to " + iface.getName() + ".");
        }
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this) || iface.isInstance(driver);
    }
}
