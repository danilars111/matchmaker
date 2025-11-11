package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
// We no longer need Spring or the GlobalExceptionHandler here!
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.util.LoggingManager;
import org.poolen.util.SpringManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// No Spring imports needed
import org.springframework.context.ConfigurableApplicationContext;

import static org.poolen.util.SpringManager.setupSpringProperties;

public class ApplicationLauncher {

    // We no longer need a static SpringContext here
    private static final Logger logger = LoggerFactory.getLogger(ApplicationLauncher.class);

    public static void main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        // First, we initialize our new logging system!
        LoggingManager.initialize();

        logger.info("Matchmaker Application starting up...");

        try {
            // This is your existing method, let's keep it!
            setupSpringProperties(args);

            // 1. We NO LONGER build Spring first.
            // We'll load our other libraries
            Loader.loadNativeLibraries();

            // 2. And *now* we launch the JavaFX app IMMEDIATELY!
            // Spring will be started *by* the LoginApplication.
            logger.info("Launching JavaFX application...");
            Application.launch(LoginApplication.class, args);

        } catch (Throwable t) {
            // This is the absolute last-ditch catch, just in case
            logger.error("A fatal error occurred before the handler was set up!", t);
            // We can't even show our window, so we just have to exit.
            System.exit(-1);
        }
    }

    // This method is no longer needed, as LoginApplication will manage the context.
    // public static ConfigurableApplicationContext getSpringContext() { ... }
}
