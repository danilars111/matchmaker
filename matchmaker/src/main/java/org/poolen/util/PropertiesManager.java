package org.poolen.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertiesManager {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesManager.class);
    private static final String PROPERTIES_FILE_NAME = "application.properties";

    /**
     * A lovely new helper to get the full path to where our properties file should live.
     * @return The Path to the properties file in the AppData directory.
     */
    public static Path getPropertiesPath() {
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir == null) {
            // This is a fallback for weird systems, it's unlikely to be used.
            logger.warn("Could not determine AppData directory. Falling back to local path: {}", PROPERTIES_FILE_NAME);
            return Paths.get(PROPERTIES_FILE_NAME);
        }
        Path propertiesPath = appDataDir.resolve(PROPERTIES_FILE_NAME);
        logger.trace("Resolved properties path to: {}", propertiesPath);
        return propertiesPath;
    }

    /**
     * Checks if the external application.properties file exists.
     * @return true if it exists, false otherwise.
     */
    public static boolean propertiesFileExists() {
        Path path = getPropertiesPath();
        boolean exists = Files.exists(path);
        logger.debug("Checking if properties file exists at [{}]. Found: {}", path, exists);
        return exists;
    }

    /**
     * Loads the properties from the external file.
     * @return A Properties object.
     * @throws IOException if there's an error reading the file.
     */
    public static Properties loadProperties() throws IOException {
        Path path = getPropertiesPath();
        logger.info("Loading properties from: {}", path);
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
            logger.info("Successfully loaded {} properties.", props.size());
        } catch (IOException e) {
            logger.error("Failed to load properties file from: {}", path, e);
            throw e;
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
        logger.info("Saving database credentials to properties file.");
        try {
            // First, load the default template from our resources.
            Properties props = new Properties();
            try (InputStream templateStream = PropertiesManager.class.getResourceAsStream("/application.properties")) {
                if (templateStream == null) {
                    logger.error("Default properties template not found in JAR! Cannot save credentials.");
                    throw new IOException("Default properties template not found in JAR!");
                }
                props.load(templateStream);
                logger.debug("Loaded default properties template from resources.");
            }

            // Now, update it with the user's details, but let's be clever about it!
            String fullUrl = dbAddress;

            // Add the protocol only if it's missing.
            if (!fullUrl.toLowerCase().startsWith("jdbc:mysql://")) {
                fullUrl = "jdbc:mysql://" + fullUrl;
                logger.debug("Prepended 'jdbc:mysql://' to database address.");
            }

            // Add the default SSL mode only if no ssl-mode is specified at all.
            if (!fullUrl.contains("ssl-mode=")) {
                if (fullUrl.contains("?")) {
                    // There are other parameters, so we add ours with an ampersand.
                    fullUrl += "&ssl-mode=REQUIRED";
                } else {
                    // No other parameters, so we start the query string with a question mark.
                    fullUrl += "?ssl-mode=REQUIRED";
                }
                logger.debug("Appended default SSL mode ('&ssl-mode=REQUIRED' or '?ssl-mode=REQUIRED') to URL.");
            }

            props.setProperty("spring.datasource.url", fullUrl);
            props.setProperty("spring.datasource.username", username);
            props.setProperty("spring.datasource.password", password);
            logger.debug("Set datasource URL, username, and password properties.");

            // And finally, save it to the file.
            Path propertiesPath = getPropertiesPath();
            try (OutputStream output = Files.newOutputStream(propertiesPath)) {
                props.store(output, "User-defined database configuration");
                logger.info("Successfully saved properties file to: {}", propertiesPath);
            }
        } catch (IOException e) {
            logger.error("Failed to save properties file!", e);
        }
    }
}
