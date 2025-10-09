package org.poolen.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * A utility class to manage and provide configuration properties to the Spring Framework application context.
 * It determines the location of the `application.properties` file based on command-line arguments,
 * allowing for different configurations such as an in-memory H2 database mode or a standard file-based setup.
 */
public class SpringManager {
    private static final Properties springProperties = new Properties();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SpringManager() {
        // This class should not be instantiated.
    }

    /**
     * Sets up the Spring properties, specifically the `spring.config.location`, based on application arguments.
     * <p>
     * If the {@code --h2} argument is passed, the application is configured to use an internal, classpath-based
     * properties file for an H2 database. This is typically used for testing or initial setup.
     * <p>
     * Otherwise, it checks for an external properties file in the application's data directory. If the file
     * exists, its location is set. If it does not exist, the properties remain empty, which signals the main
     * application to launch a setup or configuration screen.
     *
     * @param args The command-line arguments passed to the application's main method.
     */
    public static void setupSpringProperties(String[] args) {
        if (Arrays.stream(args).anyMatch("--h2"::equalsIgnoreCase)) {
            System.out.println("H2 mode requested. Loading internal configuration.");
            springProperties.setProperty("spring.config.location", "classpath:h2-application.properties");
        } else {
            Path propertiesPath = PropertiesManager.getPropertiesPath();
            // We only set the location if the file actually exists.
            if (Files.exists(propertiesPath)) {
                springProperties.setProperty("spring.config.location", "file:" + propertiesPath.toAbsolutePath());
            }
            // If it doesn't exist, we leave the properties empty. The LoginApplication's logic
            // will catch this and launch the setup screen.
        }
    }

    /**
     * Retrieves the configured Spring properties. This is intended to be passed to the SpringApplication
     * builder to specify the configuration file location.
     *
     * @return A {@link Properties} object containing the `spring.config.location` key if a configuration
     * was found and set. Otherwise, returns an empty Properties object.
     */
    public static Properties getConfigLocation() {
        return springProperties;
    }
}
