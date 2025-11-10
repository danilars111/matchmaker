package org.poolen.frontend.exceptions;

import javafx.application.Platform;
import org.poolen.frontend.gui.components.stages.email.CrashReportStage;
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
        Platform.runLater(() -> {

            // --- HERE IS OUR BOMB-PROOF FIX, MY LOVE! ---
            try {
                // 1. We TRY to create our lovely new pop-up window
                CrashReportStage crashStage = new CrashReportStage(e, springContext);

                // 2. We set the shutdown logic to run *after* the window is closed!
                crashStage.setOnHidden(event -> {
                    logger.info("Shutting down after crash report.");
                    Platform.exit();
                    System.exit(1); // Use a non-zero exit code to signal an error
                });

                // 3. Now, we can just *show* it, which is safe and won't be closed!
                crashStage.show();

            } catch (Throwable innerError) {
                // OH MY GODDESS, THE LIFEBOAT IS SINKING!
                // This means our CrashReportStage *itself* failed to even be created.
                // We can't show a GUI, so we just log this catastrophic failure
                // and shut down immediately.
                logger.error("--- CATASTROPHIC FAILURE IN EXCEPTION HANDLER ---");
                logger.error("The CrashReportStage failed to launch. This is a critical bug.", innerError);
                logger.error("The original exception that we *tried* to report was:", e);

                // We have no UI, just exit.
                Platform.exit();
                System.exit(1);
            }
        });
    }
}
