package org.poolen.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A utility class to determine the appropriate application data directory based on the operating system.
 * This ensures that configuration files, tokens, and other data are stored in standard, user-specific locations.
 */
public final class AppDataHandler {

    /**
     * Private constructor to prevent instantiation of this utility class.
     * All methods are static and should be accessed via the class name.
     */
    private AppDataHandler() {
        // This class should not be instantiated.
    }

    /**
     * Gets the application data directory, automatically resolving the app name from the project properties.
     * This is the recommended method to use as it avoids hardcoding the application name.
     *
     * @return A Path object pointing to the application data directory, or null if it could not be created.
     */
    public static Path getAppDataDirectory() {
        return getAppDataDirectory(AppConfig.getAppName());
    }

    /**
     * Gets the appropriate application data directory for the current operating system.
     * It will create the directory if it does not already exist.
     * <p>
     * The locations are determined as follows:
     * - Windows: %APPDATA%\[appName]
     * - macOS: ~/Library/Application Support/[appName]
     * - Linux/Other: ~/.config/[appName]
     *
     * @param appName The name of the application. This will be used as the folder name within the data directory.
     * @return A Path object pointing to the application data directory, or null if the directory could not be created.
     */
    public static Path getAppDataDirectory(String appName) {
        String os = System.getProperty("os.name").toLowerCase();
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path appDataPath;

        if (os.contains("win")) {
            // Windows: C:\Users\<Username>\AppData\Roaming
            String appDataEnv = System.getenv("APPDATA");
            if (appDataEnv != null && !appDataEnv.isEmpty()) {
                appDataPath = Paths.get(appDataEnv, appName);
            } else {
                // Fallback if APPDATA is not set for some reason
                appDataPath = userHome.resolve("AppData/Roaming/" + appName);
            }
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support
            appDataPath = userHome.resolve("Library/Application Support/" + appName);
        } else {
            // Linux and other Unix-like systems, following XDG Base Directory Specification
            appDataPath = userHome.resolve(".config/" + appName);
        }

        try {
            // Create all necessary parent directories if they don't exist
            Files.createDirectories(appDataPath);
        } catch (IOException e) {
            // Using System.err is a standard way to log errors without crashing the app
            System.err.println("Failed to create application data directory at " + appDataPath);
            e.printStackTrace(); // This provides more detail for debugging
            return null; // Return null to indicate failure
        }

        return appDataPath;
    }
}

