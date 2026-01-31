/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.database.connection;

import org.apache.tomcat.jdbc.pool.PoolProperties;

import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

public class PoolOptions extends TreeMap<String, Object> {
    public static final String LOGIN_TIMEOUT = "loginTimeout";
    public static final String MAX_ACTIVE = "maxActive";
    public static final String MAX_IDLE = "maxIdle";
    public static final String MIN_IDLE = "minIdle";
    public static final String INITIAL_SIZE = "initialSize";
    public static final String MAX_WAIT = "maxWait";
    public static final String MAX_AGE = "maxAge";
    public static final String TEST_ON_BORROW = "testOnBorrow";
    public static final String TEST_ON_CONNECT = "testOnConnect";
    public static final String TEST_ON_RETURN = "testOnReturn";
    public static final String TEST_WHILE_IDLE = "testWhileIdle";
    public static final String VALIDATION_QUERY = "validationQuery";
    public static final String VALIDATION_QUERY_TIMEOUT = "validationQueryTimeout";
    public static final String VALIDATION_INTERVAL = "validationInterval";
    public static final String VALIDATOR_CLASS_NAME = "validatorClassName";
    public static final String TIME_BETWEEN_EVICTION_RUNS_MILLIS = "timeBetweenEvictionRunsMillis";
    public static final String NUM_TESTS_PER_EVICTION_RUN = "numTestsPerEvictionRun";
    public static final String MIN_EVICTABLE_IDLE_TIME_MILLIS = "minEvictableIdleTimeMillis";
    public static final String REMOVE_ABANDONED = "removeAbandoned";
    public static final String REMOVE_ABANDONED_TIMEOUT = "removeAbandonedTimeout";
    public static final String SUSPECT_TIMEOUT = "suspectTimeout";
    public static final String LOG_ABANDONED = "logAbandoned";
    public static final String ABANDON_WHEN_PERCENTAGE_FULL = "abandonWhenPercentageFull";
    public static final String INIT_SQL = "initSQL";
    public static final String JMX_ENABLED = "jmxEnabled";
    public static final String FAIR_QUEUE = "fairQueue";

    public static final int DEFAULT_LOGIN_TIMEOUT = 60;

    public PoolOptions() {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    public static PoolOptions of(PoolOptions other) {
        PoolOptions poolOptions = new PoolOptions();
        if (other != null) {
            poolOptions.putAll(other);
        }

        return poolOptions;
    }

    public Optional<String> getString(String key) {
        return Optional.ofNullable(parseString(key));
    }

    public String getStringOrDefault(String key, String defaultValue) {
        String value = parseString(key);
        return value != null ? value : defaultValue;
    }

    private String parseString(String key) {
        Object value = get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        } else if (value != null) {
            return String.valueOf(value);
        }

        return null;
    }

    public Optional<Double> getDouble(String key) {
        return Optional.ofNullable(parseDouble(key));
    }

    public double getDoubleOrDefault(String key, double defaultValue) {
        Double value = parseDouble(key);
        return value != null ? value : defaultValue;
    }

    private Double parseDouble(String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        } else if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    public Optional<Integer> getInteger(String key) {
        return Optional.ofNullable(parseInteger(key));
    }

    public int getIntegerOrDefault(String key, int defaultValue) {
        Integer value = parseInteger(key);
        return value != null ? value : defaultValue;
    }

    private Integer parseInteger(String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.intValue();
        } else if (value != null) {
            try {
                return (int) Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    public Optional<Boolean> getBoolean(String key) {
        return Optional.ofNullable(parseBoolean(key));
    }

    public boolean getBooleanOrDefault(String key, boolean defaultValue) {
        Boolean value = parseBoolean(key);
        return value != null ? value : defaultValue;
    }

    private Boolean parseBoolean(String key) {
        Object value = get(key);
        if (value instanceof Boolean bool) {
            return bool;
        } else if (value != null) {
            String stringValue = String.valueOf(value).trim();
            return switch (stringValue.toLowerCase(Locale.ROOT)) {
                case "true", "1", "yes", "y", "on" -> true;
                case "false", "0", "no", "n", "off" -> false;
                default -> null;
            };
        }

        return null;
    }

    public PoolOptions set(String key, Object value) {
        if (value != null) {
            put(key, value);
        }

        return this;
    }

    public void applyTo(PoolProperties properties) {
        getInteger(MAX_ACTIVE).ifPresent(properties::setMaxActive);
        getInteger(MAX_IDLE).ifPresent(properties::setMaxIdle);
        getInteger(MIN_IDLE).ifPresent(properties::setMinIdle);
        getInteger(INITIAL_SIZE).ifPresent(properties::setInitialSize);
        getInteger(MAX_AGE).ifPresent(properties::setMaxAge);
        getBoolean(TEST_ON_BORROW).ifPresent(properties::setTestOnBorrow);
        getBoolean(TEST_ON_CONNECT).ifPresent(properties::setTestOnConnect);
        getBoolean(TEST_ON_RETURN).ifPresent(properties::setTestOnReturn);
        getBoolean(TEST_WHILE_IDLE).ifPresent(properties::setTestWhileIdle);
        getString(VALIDATION_QUERY).ifPresent(properties::setValidationQuery);
        getInteger(VALIDATION_QUERY_TIMEOUT).ifPresent(properties::setValidationQueryTimeout);
        getInteger(VALIDATION_INTERVAL).ifPresent(properties::setValidationInterval);
        getString(VALIDATOR_CLASS_NAME).ifPresent(properties::setValidatorClassName);
        getInteger(TIME_BETWEEN_EVICTION_RUNS_MILLIS).ifPresent(properties::setTimeBetweenEvictionRunsMillis);
        getInteger(NUM_TESTS_PER_EVICTION_RUN).ifPresent(properties::setNumTestsPerEvictionRun);
        getInteger(MIN_EVICTABLE_IDLE_TIME_MILLIS).ifPresent(properties::setMinEvictableIdleTimeMillis);
        getBoolean(REMOVE_ABANDONED).ifPresent(properties::setRemoveAbandoned);
        getInteger(REMOVE_ABANDONED_TIMEOUT).ifPresent(properties::setRemoveAbandonedTimeout);
        getInteger(SUSPECT_TIMEOUT).ifPresent(properties::setSuspectTimeout);
        getBoolean(LOG_ABANDONED).ifPresent(properties::setLogAbandoned);
        getInteger(ABANDON_WHEN_PERCENTAGE_FULL).ifPresent(properties::setAbandonWhenPercentageFull);
        getString(INIT_SQL).ifPresent(properties::setInitSQL);
        getBoolean(JMX_ENABLED).ifPresent(properties::setJmxEnabled);
        getBoolean(FAIR_QUEUE).ifPresent(properties::setFairQueue);

        properties.setMaxWait(getInteger(LOGIN_TIMEOUT)
                .map(loginTimeout -> loginTimeout * 1000)
                .orElse(getIntegerOrDefault(MAX_WAIT, DEFAULT_LOGIN_TIMEOUT * 1000)));
    }
}
