package org.poolen.frontend.util.services;

import javafx.stage.Window;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * A service that contains the business logic for connecting to Google.
 * This can be used as a standalone task or as part of a larger task sequence.
 */
@Service
public class UiGoogleTaskService {

    private static final Logger logger = LoggerFactory.getLogger(UiGoogleTaskService.class);

    private final SheetsServiceManager sheetsServiceManager;
    private final UiTaskExecutor uiTaskExecutor;
    private final GoogleAuthManager authManager;

    @Autowired
    public UiGoogleTaskService(SheetsServiceManager sheetsServiceManager, GoogleAuthManager authManager,
                               UiTaskExecutor uiTaskExecutor) {
        this.sheetsServiceManager = sheetsServiceManager;
        this.uiTaskExecutor = uiTaskExecutor;
        this.authManager = authManager;
        logger.info("UiGoogleTaskService initialised.");
    }

    /**
     * The core logic for connecting to Google. This is the "work" that gets
     * done in the background. It is designed to be called from within a UiTaskExecutor.
     * @param updater The updater to update the UI message and show the login URL.
     * @throws Exception if the connection fails.
     */
    public void connectToGoogle(UiUpdater updater) throws Exception {
        logger.info("Starting Google connection process...");
        updater.updateStatus("Attempting to connect...\nPlease check your browser to sign in.");

        // We pass a lovely little lambda to the connect method. This is the callback
        // that will be executed when the auth URL is ready.
        // It uses our updater to show the details in the overlay.
        sheetsServiceManager.connect(url -> {
            logger.debug("Received authorization URL. Displaying to user.");
            updater.showDetails("If your browser doesn't open, please use this link:", url,
                    authManager::abortAuthorization);
        });
        logger.info("Connection process complete.");
    }

    public void disconnectFromGoogle(UiUpdater updater) {
        logger.info("Starting Google disconnect process...");
        updater.updateStatus("Attempting to disconnect from Google");

        authManager.logout();
        sheetsServiceManager.disconnectSheetService();

        logger.info("Disconnection process complete.");
    }

    /**
     * Our fabulous new express path for auto-login!
     */
    public void connectWithStoredCredentials(UiUpdater updater) throws Exception {
        logger.info("Attempting to connect with stored credentials...");
        updater.updateStatus("Logging in with stored session...");
        sheetsServiceManager.connectWithStoredCredentials();
        logger.info("Successfully connected with stored credentials.");
    }

    /**
     * A high-level method that wraps the connection logic in a UiTaskExecutor.
     * This provides a simple way to trigger the full sign-in process from the UI
     * with its own loading overlay.
     * @param owner The parent window for the loading overlay.
     * @param onSuccess A callback to run on successful connection.
     * @param onError A callback to run if the connection fails.
     */
    public void connect(Window owner, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        logger.info("Executing Google connect task via UiTaskExecutor.");
        uiTaskExecutor.execute(
                owner,
                "Connecting to Google...",
                "Successfully connected!",
                (updater) -> { // This is our ProgressAwareTask
                    logger.debug("Executing connectToGoogle task within executor.");
                    connectToGoogle(updater);
                    return "LOGIN_SUCCESSFUL";
                },
                onSuccess,
                onError
        );
    }
}
