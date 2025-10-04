package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.util.PropertiesManager;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Path;
import java.util.Arrays;

@SpringBootApplication
public class ApplicationLauncher {

    public static void main(String[] args) {
        initializeProperties(args);

        Loader.loadNativeLibraries();
        Application.launch(LoginApplication.class, args);
    }

    private static void initializeProperties(String[] args) {
        // --- PRE-FLIGHT CHECKS & CONFIGURATION ---
        // This logic runs BEFORE Spring or JavaFX are started.
        // We will set a system property that the JavaFX app will use to start Spring correctly.
        if (Arrays.stream(args).anyMatch("--h2"::equalsIgnoreCase)) {
            System.out.println("H2 mode requested. Setting config location to internal H2 properties.");
            System.setProperty("spring.config.location", "classpath:h2-application.properties");
        } else if (PropertiesManager.propertiesFileExists()) {
            // Standard mode: use the external properties file in AppData.
            Path propertiesPath = PropertiesManager.getPropertiesPath();
            if (propertiesPath != null) {
                System.setProperty("spring.config.location", "file:" + propertiesPath.toAbsolutePath());
            } else {
                // This is a critical error, but we'll let the JavaFX app handle showing the error dialog.
                System.err.println("CRITICAL: Properties file exists but its path could not be determined.");
            }
        }
        // If neither of the above is true, the system property remains unset,
        // and the LoginApplication will know to trigger the setup process.
    }
}
