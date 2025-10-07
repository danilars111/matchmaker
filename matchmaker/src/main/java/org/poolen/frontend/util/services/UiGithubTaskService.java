package org.poolen.frontend.util.services;

import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.web.github.GitHubUpdateChecker;
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

    private final ApplicationScriptService scriptService;

    @Autowired
    public UiGithubTaskService(ApplicationScriptService scriptService) {
        this.scriptService = scriptService;
    }

    /**
     * A helper method that checks for a new version on GitHub.
     * @param updater The updater to update the UI message.
     * @return The update information.
     * @throws Exception if the check fails.
     */
    public GitHubUpdateChecker.UpdateInfo checkForUpdate(UiUpdater updater) throws Exception {
        updater.updateStatus("Checking GitHub for new releases...");
        return GitHubUpdateChecker.checkForUpdate();
    }

    /**
     * A helper method that downloads a new version from the provided URL.
     * @param info The update information containing the download URL.
     * @param updater The updater to update the UI message.
     * @return The downloaded File.
     * @throws IOException if the download fails.
     */
    public File downloadUpdate(GitHubUpdateChecker.UpdateInfo info, UiUpdater updater) throws IOException {
        updater.updateStatus("Contacting download server...");
        URL downloadUrl = new URL(info.assetDownloadUrl());
        File tempFile = File.createTempFile("matchmaker-update-", ".jar");
        try (InputStream in = downloadUrl.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            updater.updateStatus("Downloading version " + info.latestVersion() + "...");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
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
        scriptService.applyUpdateAndRestart(newJarFile, onIdeError, onError);
    }
}

