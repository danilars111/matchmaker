package org.poolen.frontend.util.services;

import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Handles the application's startup sequence logic.
 * This service is responsible for checking for updates, test data generation,
 * and verifying existing user sessions to keep the main Application class clean.
 */
@Service
public class StartupService {

    // --- Services (Dependencies) ---
    private final org.poolen.frontend.util.services.TestDataGenerator testDataGenerator;
    private final UiPersistenceService uiPersistenceService;
    private final UiGithubTaskService uiGithubTaskService;
    private final UiGoogleTaskService uiGoogleTaskService;

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
                          UiGoogleTaskService uiGoogleTaskService) {
        this.testDataGenerator = testDataGenerator;
        this.uiPersistenceService = uiPersistenceService;
        this.uiGithubTaskService = uiGithubTaskService;
        this.uiGoogleTaskService = uiGoogleTaskService;
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
        // 1. Generate Test Data if requested
        // This is only for our special H2 mode, of course.
        if (testDataPlayerCount > 0) {
            updater.updateStatus("Generating " + testDataPlayerCount + " players...");
            testDataGenerator.generate(testDataPlayerCount);
            uiPersistenceService.saveAllWithProgress(updater);
        }

        // 2. Check for updates, because we always want the latest and greatest!
        try {
            GitHubUpdateChecker.UpdateInfo updateInfo = uiGithubTaskService.checkForUpdate(updater);
            if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                File newJarFile = uiGithubTaskService.downloadUpdate(updateInfo, updater);
                return new StartupResult(StartupResult.Status.UPDATE_READY, newJarFile);
            }
        } catch (Exception e) {
            System.err.println("Update process failed, continuing startup: " + e.getMessage());
            // An update check failing shouldn't stop the whole show.
        }

        // 3. Check for an existing session to welcome the user back.
        updater.updateStatus("Checking for existing session...");
        if (GoogleAuthManager.hasStoredCredentials()) {
            uiGoogleTaskService.connectWithStoredCredentials(updater);
            uiPersistenceService.findAllWithProgress(updater);
            return new StartupResult(StartupResult.Status.LOGIN_SUCCESSFUL);
        }

        // 4. If nothing else, it's time to show the login screen.
        return new StartupResult(StartupResult.Status.SHOW_LOGIN_UI);
    }
}

