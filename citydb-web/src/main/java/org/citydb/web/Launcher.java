package org.citydb.web;

import org.citydb.database.DatabaseException;
import org.citydb.web.util.DatabaseConnector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.sql.SQLException;

@SpringBootApplication
public class Launcher {

    public static void main(String[] args) {
        SpringApplication.run(Launcher.class, args);
        DatabaseConnector databaseConnector = DatabaseConnector.getInstance();
        try {
            databaseConnector.connect();
        } catch (SQLException | DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
}
