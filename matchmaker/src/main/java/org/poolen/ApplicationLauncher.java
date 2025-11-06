package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.frontend.exceptions.GlobalExceptionHandler;
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.util.LoggingManager;
import org.poolen.util.SpringManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.poolen.util.SpringManager.setupSpringProperties;

public class ApplicationLauncher {

    private static ConfigurableApplicationContext springContext;
    private static final Logger logger = LoggerFactory.getLogger(ApplicationLauncher.class);

    public static void main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        // First, we initialize our new logging system!
        LoggingManager.initialize();

        logger.info("Matchmaker Application starting up...");

        try {
            // This is your existing method, let's keep it!
            setupSpringProperties(args);

            // 1. We MUST build and run Spring *first*!
            // We point it to your @SpringBootApplication class
            logger.info("Starting Spring context...");
            springContext = new SpringApplicationBuilder(MatchmakerApplication.class)
                    .headless(false) // Tell Spring it's okay to have a GUI
                    .properties(SpringManager.getConfigLocation())
                    .run(args);

            logger.info("Spring context loaded. Setting global exception handler.");

            // 2. Now that Spring is alive, we create our nanny and tell her to watch all threads
            GlobalExceptionHandler.setInstance(springContext);
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler.getInstance());

            // 3. Load your other libraries
            Loader.loadNativeLibraries();

            // 4. And *now* we launch the JavaFX app
            logger.info("Launching JavaFX application...");
            Application.launch(LoginApplication.class, args);

        } catch (Throwable t) {
            // This is the absolute last-ditch catch, just in case
            logger.error("A fatal error occurred before the handler was set up!", t);
            // We can't even show our window, so we just have to exit.
            System.exit(-1);
        }
    }

    /**
     * A lovely little helper so our JavaFX app can find the Spring beans!
     * @return The running Spring application context.
     */
    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }
}
