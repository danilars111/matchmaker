package org.poolen.frontend.util.services;

import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * Handles the application's startup sequence logic.
 * This service is responsible for checking for updates, test data generation,
 * and verifying existing user sessions to keep the main Application class clean.
 */
@Service
public class StartupService {

    private static final Logger logger = LoggerFactory.getLogger(StartupService.class);

    // --- Services (Dependencies) ---
    private final org.poolen.frontend.util.services.TestDataGenerator testDataGenerator;
    private final UiPersistenceService uiPersistenceService;
    private final UiGithubTaskService uiGithubTaskService;
    private final UiGoogleTaskService uiGoogleTaskService;
    private final GoogleAuthManager authManager;

    /**
     * Constructs the service with its required dependencies.
     * Spring will automagically inject these for us via the constructor, darling!
     * @param testDataGenerator Service to generate test data in H2 mode.
     * @param uiPersistenceService Service to handle database operations with UI feedback.
     * @param uiGithubTaskService Service to manage GitHub update checks and downloads.
     * @param uiGoogleTaskService Service for Google authentication and tasks.
     */
    public StartupService(org.poolen.frontend.util.services.TestDataGenerator testDataGenerator,
                          UiPersistenceService uiPersistenceService,
                          UiGithubTaskService uiGithubTaskService,
                          UiGoogleTaskService uiGoogleTaskService,
                          GoogleAuthManager authManager) {
        this.testDataGenerator = testDataGenerator;
        this.uiPersistenceService = uiPersistenceService;
        this.uiGithubTaskService = uiGithubTaskService;
        this.uiGoogleTaskService = uiGoogleTaskService;
        this.authManager = authManager;
        logger.info("StartupService initialised.");
    }

    /**
     * Represents the possible outcomes of the startup sequence.
     * This helps in decoupling the startup logic from the UI updates.
     */
    public static class StartupResult {
        public enum Status { UPDATE_READY, LOGIN_SUCCESSFUL, SHOW_LOGIN_UI }
        public final Status status;
        public final Object data;

        public StartupResult(Status status, Object data) { this.status = status; this.data = data; }
        public StartupResult(Status status) { this(status, null); }
    }

    /**
     * Executes the main startup sequence.
     * This method contains the core logic that was previously in the LoginApplication.
     *
     * @param testDataPlayerCount The number of test players to generate (0 if none).
     * @param updater An interface to post status updates back to the UI.
     * @return A {@link StartupResult} indicating the outcome.
     */
    public StartupResult performStartupSequence(int testDataPlayerCount, UiUpdater updater) throws Exception {
        logger.info("Performing startup sequence...");
        // 1. Generate Test Data if requested
        // This is only for our special H2 mode, of course.
        if (testDataPlayerCount > 0) {
            logger.info("Test data generation requested for {} players.", testDataPlayerCount);
            updater.updateStatus("Generating " + testDataPlayerCount + " players...");
            testDataGenerator.generate(testDataPlayerCount);
            uiPersistenceService.saveAllWithProgress(updater);
            logger.info("Test data generation complete.");
        }

        // 2. Check for updates, because we always want the latest and greatest!
        try {
            logger.info("Checking for application updates...");
            GitHubUpdateChecker.UpdateInfo updateInfo = uiGithubTaskService.checkForUpdate(updater);
            if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                logger.info("New version available: {}. Downloading update.", updateInfo.latestVersion());
                File newJarFile = uiGithubTaskService.downloadUpdate(updateInfo, updater);
                logger.info("Update downloaded successfully. Returning UPDATE_READY status.");
                return new StartupResult(StartupResult.Status.UPDATE_READY, newJarFile);
            }
            logger.info("Application is up to date.");
        } catch (Exception e) {
            logger.warn("Update check process failed, continuing startup.", e);
            // An update check failing shouldn't stop the whole show.
        }

        // 3. Check for an existing session to welcome the user back.
        updater.updateStatus("Checking for existing session...");
        logger.info("Checking for stored Google credentials...");
        try {
            // We just try to connect. This will throw an exception if it fails.
            uiGoogleTaskService.connectWithStoredCredentials(updater);

            logger.info("Returning LOGIN_SUCCESSFUL status.");
            return new StartupResult(StartupResult.Status.LOGIN_SUCCESSFUL);

        } catch (IOException e) {
            // This 'catch' block is now our "if (false)"
            // It means no valid credentials were found, or the refresh failed.
            logger.info("No valid stored credentials found (or connection failed). Proceeding to login screen.");
            // We just let the method continue to the 'logged out' state...

        } catch (Exception e) {
            // It's also good to catch other unexpected errors!
            logger.error("An unexpected error occurred during automatic login.", e);
            // And still fall through to the 'logged out' state...
        }

        // 4. If nothing else, it's time to show the login screen.
        logger.info("No stored credentials found. Returning SHOW_LOGIN_UI status.");
        return new StartupResult(StartupResult.Status.SHOW_LOGIN_UI);
    }
}

