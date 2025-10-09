package org.poolen.frontend.util.services;

import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.web.github.GitHubUpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

@Service
public class UiGithubTaskService {

    private static final Logger logger = LoggerFactory.getLogger(UiGithubTaskService.class);

    private final ApplicationScriptService scriptService;

    @Autowired
    public UiGithubTaskService(ApplicationScriptService scriptService) {
        this.scriptService = scriptService;
        logger.info("UiGithubTaskService initialised.");
    }

    /**
     * A helper method that checks for a new version on GitHub.
     * @param updater The updater to update the UI message.
     * @return The update information.
     * @throws Exception if the check fails.
     */
    public GitHubUpdateChecker.UpdateInfo checkForUpdate(UiUpdater updater) throws Exception {
        logger.info("Checking for updates on GitHub...");
        updater.updateStatus("Checking GitHub for new releases...");
        GitHubUpdateChecker.UpdateInfo updateInfo = GitHubUpdateChecker.checkForUpdate();
        logger.info("Update check complete. New version available: {}", updateInfo.isNewVersionAvailable());
        return updateInfo;
    }

    /**
     * A helper method that downloads a new version from the provided URL.
     * @param info The update information containing the download URL.
     * @param updater The updater to update the UI message.
     * @return The downloaded File.
     * @throws IOException if the download fails.
     */
    public File downloadUpdate(GitHubUpdateChecker.UpdateInfo info, UiUpdater updater) throws IOException {
        logger.info("Starting download for version {} from URL: {}", info.latestVersion(), info.assetDownloadUrl());
        updater.updateStatus("Contacting download server...");
        URL downloadUrl = new URL(info.assetDownloadUrl());
        File tempFile = File.createTempFile("matchmaker-update-", ".jar");
        logger.debug("Created temporary file for update at: {}", tempFile.getAbsolutePath());
        try (InputStream in = downloadUrl.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            updater.updateStatus("Downloading version " + info.latestVersion() + "...");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        logger.info("Successfully downloaded update to temporary file.");
        return tempFile;
    }

    /**
     * Tries to apply the downloaded update and restart the application by delegating to the ApplicationScriptService.
     *
     * @param newJarFile The newly downloaded JAR file.
     * @param onIdeError A callback to run if the application detects it's not running from a JAR.
     * @param onError    A callback to run if any exception occurs during the process.
     */
    public void applyUpdateAndRestart(File newJarFile, Runnable onIdeError, Consumer<Exception> onError) {
        logger.info("Delegating update and restart action to ApplicationScriptService for file: {}", newJarFile.getAbsolutePath());
        scriptService.applyUpdateAndRestart(newJarFile, onIdeError, onError);
    }
}
