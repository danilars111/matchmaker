package org.poolen.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String EMAIL_CREDENTIALS_FILE = "/credentials/emailCredentials.json";

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
     * Loads the existing properties file from AppData, or creates a new one
     * from the resource template if it doesn't exist.
     * This now *also* injects the Brevo key from the resource JSON.
     * @return A Properties object.
     * @throws IOException if there's an error reading the files.
     */
    private static Properties loadOrCreateProperties() throws IOException {
        Path propertiesPath = getPropertiesPath();
        if (Files.exists(propertiesPath)) {
            logger.debug("Existing properties file found. Loading it.");
            // File already exists, just load it.
            // We assume if it exists, it already has the email key.
            return loadProperties();
        }

        logger.info("No properties file found. Creating new one from template.");
        // File doesn't exist, create it from the template
        Properties props = new Properties();
        try (InputStream templateStream = PropertiesManager.class.getResourceAsStream("/" + PROPERTIES_FILE_NAME)) {
            if (templateStream == null) {
                logger.error("Default properties template not found in JAR!");
                throw new IOException("Default properties template not found in JAR!");
            }
            props.load(templateStream);
            logger.debug("Loaded default properties template from resources.");
        }

        // --- NEW! Load the Email Keys from our resource JSON ---
        try (InputStream jsonStream = PropertiesManager.class.getResourceAsStream(EMAIL_CREDENTIALS_FILE)) {
            if (jsonStream == null) {
                logger.error("Email credentials file not found in JAR at: {}", EMAIL_CREDENTIALS_FILE);
                throw new IOException("Email credentials file not found in JAR!");
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonStream);

            // Read all the email properties, not just the password!
            if (rootNode.has("smtp_host")) {
                props.setProperty("spring.mail.host", rootNode.get("smtp_host").asText());
            }
            if (rootNode.has("smtp_port")) {
                props.setProperty("spring.mail.port", rootNode.get("smtp_port").asText());
            }
            if (rootNode.has("smtp_username")) {
                props.setProperty("spring.mail.username", rootNode.get("smtp_username").asText());
            }
            if (rootNode.has("smtp_password")) {
                props.setProperty("spring.mail.password", rootNode.get("smtp_password").asText());
                logger.info("Successfully loaded and set email credentials from resource JSON.");
            } else {
                logger.warn("Found {} but it's missing 'smtp_password' (and maybe others)!", EMAIL_CREDENTIALS_FILE);
            }
        } catch (IOException e) {
            logger.error("Failed to read or parse {}: {}", EMAIL_CREDENTIALS_FILE, e.getMessage(), e);
            throw e; // Re-throw so we know something went wrong
        }
        // ----------------------------------------------------

        return props;
    }

    /**
     * A private helper to save the properties file.
     * @param props The Properties object to save.
     * @param comment A comment to write at the top of the file.
     * @throws IOException if there's an error writing the file.
     */
    private static void saveProperties(Properties props, String comment) throws IOException {
        Path propertiesPath = getPropertiesPath();
        try (OutputStream output = Files.newOutputStream(propertiesPath)) {
            props.store(output, comment);
            logger.info("Successfully saved properties file to: {}", propertiesPath);
        }
    }

    /**
     * Saves *only* the database credentials to the external properties file.
     * This will create or update the file.
     * @param dbAddress The database address (e.g., your-host.com:port/db).
     * @param username The database username.
     * @param password The database password.
     */
    public static void saveDatabaseCredentials(String dbAddress, String username, String password) {
        logger.info("Saving database credentials to properties file.");
        try {
            // This will now load the template *with* the Brevo key already in it!
            Properties props = loadOrCreateProperties();

            // --- Database Credentials ---
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
                logger.debug("Appended default SSL mode to URL.");
            }

            props.setProperty("spring.datasource.url", fullUrl);
            props.setProperty("spring.datasource.username", username);
            props.setProperty("spring.datasource.password", password);
            logger.debug("Set datasource URL, username, and password properties.");

            // And finally, save it to the file.
            saveProperties(props, "User-defined application configuration");

        } catch (IOException e) {
            logger.error("Failed to save database credentials!", e);
        }
    }
}
