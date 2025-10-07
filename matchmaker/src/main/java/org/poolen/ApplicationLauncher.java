package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.util.PropertiesManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class ApplicationLauncher {

    private static final Properties springProperties = new Properties();

    public static void main(String[] args) {
        // This method decides which properties file to use and stores the decision.
        setupSpringProperties(args);

        Loader.loadNativeLibraries();
        Application.launch(LoginApplication.class, args);
    }

    private static void setupSpringProperties(String[] args) {
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

    public static Properties getConfigLocation() {
        return springProperties;
    }
}

