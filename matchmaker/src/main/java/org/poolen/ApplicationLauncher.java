package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.util.LoggingManager;
import org.poolen.util.PropertiesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static org.poolen.util.SpringManager.setupSpringProperties;

public class ApplicationLauncher {


    public static void main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        // First, we initialize our new logging system!
        LoggingManager.initialize();

        final Logger logger = LoggerFactory.getLogger(ApplicationLauncher.class);
        logger.info("Matchmaker Application starting up...");

        setupSpringProperties(args);

        Loader.loadNativeLibraries();
        Application.launch(LoginApplication.class, args);
    }
}


