package org.citydb.database.connection;

import org.citydb.database.DatabaseConstants;

public class AdminOptions {
    private String user;
    private String password;

    public static AdminOptions of(AdminOptions other) {
        return new AdminOptions()
                .setUser(other.user)
                .setPassword(other.password);
    }

    public String getUser() {
        return user;
    }

    public AdminOptions setUser(String user) {
        this.user = user;
        return this;
    }

    public AdminOptions setUserIfAbsent(String user) {
        return this.user == null ? setUser(user) : this;
    }

    public String getPassword() {
        return password;
    }

    public AdminOptions setPassword(String password) {
        this.password = password;
        return this;
    }

    public AdminOptions setPasswordIfAbsent(String password) {
        return this.password == null ? setPassword(password) : this;
    }

    public AdminOptions fillAbsentValuesFrom(AdminOptions other) {
        return other != null ?
                setUserIfAbsent(other.user)
                        .setPasswordIfAbsent(other.password) :
                this;
    }

    public AdminOptions fillAbsentValuesFromEnv() {
        return setUserIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_ADMIN_USERNAME))
                .setPasswordIfAbsent(System.getenv(DatabaseConstants.ENV_CITYDB_ADMIN_PASSWORD));
    }

    boolean hasValues() {
        return user != null || password != null;
    }
}
