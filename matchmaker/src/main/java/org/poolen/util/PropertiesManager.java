package org.poolen.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages the external properties file for the application.
 * This class provides methods to check for, read, and write settings.
 * The file is ONLY created during the setup/save process.
 */
public final class PropertiesManager {

    private static final String PROPERTIES_FILENAME = "application.properties";

    private PropertiesManager() {
        // Utility class
    }

    /**
     * Gets the expected path to the properties file in the AppData directory.
     * This method does NOT create the file or its directories.
     *
     * @return The Path to where the properties file should be, or null if the app data dir is not found.
     */
    public static Path getPropertiesPath() {
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir == null) {
            System.err.println("Could not determine the application data directory.");
            return null;
        }
        return appDataDir.resolve(PROPERTIES_FILENAME);
    }

    /**
     * Checks if the properties file exists in the application's AppData directory.
     *
     * @return true if the file exists, false otherwise.
     */
    public static boolean propertiesFileExists() {
        Path propertiesPath = getPropertiesPath();
        return propertiesPath != null && Files.exists(propertiesPath);
    }

    /**
     * Loads the application properties from the AppData directory.
     *
     * @return A Properties object with the loaded settings, or null if the file doesn't exist or an error occurs.
     */
    public static Properties loadProperties() {
        if (!propertiesFileExists()) {
            return null;
        }
        Path propertiesPath = getPropertiesPath();
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            props.load(in);
            return props;
        } catch (IOException e) {
            System.err.println("Could not load properties file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the database connection details to the properties file in AppData.
     * This is the ONLY method that should create the properties file.
     * It constructs the full JDBC URL and preserves any other existing settings in the file.
     *
     * @param dbAddress The database address (e.g., "hostname:port/database?ssl-mode=REQUIRED").
     * @param username  The database username.
     * @param password  The database password.
     */
    public static void saveDatabaseCredentials(String dbAddress, String username, String password) {
        Path propertiesPath = getPropertiesPath();
        if (propertiesPath == null) {
            System.err.println("Cannot save properties, path is null.");
            return;
        }

        // Ensure the parent directory exists before we try to write the file.
        try {
            Files.createDirectories(propertiesPath.getParent());
        } catch (IOException e) {
            System.err.println("Failed to create parent directories for properties file: " + e.getMessage());
            return;
        }

        Properties props = new Properties();
        // Load the default properties from the JAR as a base.
        try (InputStream in = PropertiesManager.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Could not load default properties from JAR: " + e.getMessage());
        }

        // Construct the full JDBC URL from the user-provided address
        final String fullUrl = "jdbc:mysql://" + dbAddress;

        // Set the new values
        props.setProperty("spring.datasource.url", fullUrl);
        props.setProperty("spring.datasource.username", username);
        props.setProperty("spring.datasource.password", password);

        // Save the properties back to the file
        try (OutputStream out = Files.newOutputStream(propertiesPath)) {
            props.store(out, "Database connection details configured by user");
        } catch (IOException e) {
            System.err.println("Failed to save updated properties file: " + e.getMessage());
        }
    }
}

