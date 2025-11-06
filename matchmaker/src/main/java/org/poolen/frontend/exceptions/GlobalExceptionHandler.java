package org.poolen.frontend.exceptions;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static GlobalExceptionHandler instance;

    private final ConfigurableApplicationContext springContext;

    private GlobalExceptionHandler(ConfigurableApplicationContext context) {
        this.springContext = context;
    }

    /**
     * Creates and sets the singleton instance of the handler.
     * @param context The running Spring context.
     */
    public static void setInstance(ConfigurableApplicationContext context) {
        if (instance == null) {
            instance = new GlobalExceptionHandler(context);
        }
    }

    /**
     * Gets the singleton instance.
     * @return The handler instance.
     */
    public static GlobalExceptionHandler getInstance() {
        return instance;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // We got one! Log this naughty exception immediately!
        logger.error("--- UNCAUGHT EXCEPTION ---");
        logger.error("An uncaught exception occurred on thread: {}", t.getName(), e);
        logger.error("--- END OF EXCEPTION ---");

        // We MUST show the GUI on the JavaFX thread, or it will throw *another* tantrum!
        // What a diva!
        Platform.runLater(() -> {
            // Create and show our lovely new pop-up window
            new CrashReportWindow(e, springContext).showAndWait();

            // After the window is closed (either by sending or just closing),
            // we do a controlled shutdown.
            logger.info("Shutting down after crash report.");
            Platform.exit();
            System.exit(1); // Use a non-zero exit code to signal an error
        });
    }
}
