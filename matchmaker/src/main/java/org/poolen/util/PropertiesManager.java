package org.poolen.util;

import org.poolen.util.AppDataHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertiesManager {

    private static final String PROPERTIES_FILE_NAME = "application.properties";

    /**
     * A lovely new helper to get the full path to where our properties file should live.
     * @return The Path to the properties file in the AppData directory.
     */
    public static Path getPropertiesPath() {
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir == null) {
            // This is a fallback for weird systems, it's unlikely to be used.
            return Paths.get(PROPERTIES_FILE_NAME);
        }
        return appDataDir.resolve(PROPERTIES_FILE_NAME);
    }

    /**
     * Checks if the external application.properties file exists.
     * @return true if it exists, false otherwise.
     */
    public static boolean propertiesFileExists() {
        return Files.exists(getPropertiesPath());
    }

    /**
     * Loads the properties from the external file.
     * @return A Properties object.
     * @throws IOException if there's an error reading the file.
     */
    public static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(getPropertiesPath())) {
            props.load(input);
        }
        return props;
    }

    /**
     * Saves the database credentials to the external properties file.
     * This will create the file if it doesn't exist.
     * @param dbAddress The database address (e.g., your-host.com:port/db).
     * @param username The database username.
     * @param password The database password.
     */
    public static void saveDatabaseCredentials(String dbAddress, String username, String password) {
        try {
            // First, load the default template from our resources.
            Properties props = new Properties();
            try (InputStream templateStream = PropertiesManager.class.getResourceAsStream("/application.properties")) {
                if (templateStream == null) {
                    throw new IOException("Default properties template not found in JAR!");
                }
                props.load(templateStream);
            }

            // Now, update it with the user's details.
            String fullUrl = "jdbc:mysql://" + dbAddress + "?ssl-mode=REQUIRED";
            props.setProperty("spring.datasource.url", fullUrl);
            props.setProperty("spring.datasource.username", username);
            props.setProperty("spring.datasource.password", password);

            // And finally, save it to the file.
            try (OutputStream output = Files.newOutputStream(getPropertiesPath())) {
                props.store(output, "User-defined database configuration");
            }
        } catch (IOException e) {
            // In a real GUI app, you'd show an error dialog here.
            System.err.println("Failed to save properties file!");
            e.printStackTrace();
        }
    }
}

