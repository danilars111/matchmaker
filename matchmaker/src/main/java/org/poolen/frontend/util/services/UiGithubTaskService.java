package org.poolen.frontend.util.services;

import org.poolen.web.github.GitHubUpdateChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
@Lazy
public class UiGithubTaskService {

    private final ApplicationScriptService scriptService;

    @Autowired
    public UiGithubTaskService(ApplicationScriptService scriptService) {
        this.scriptService = scriptService;
    }

    /**
     * A simple helper method that checks for a new version on GitHub.
     * Designed to be called from within a UiTaskExecutor.
     * @param progressUpdater The consumer to update the UI message.
     * @return The update information.
     * @throws Exception if the check fails.
     */
    public GitHubUpdateChecker.UpdateInfo checkForUpdate(Consumer<String> progressUpdater) throws Exception {
        progressUpdater.accept("Checking GitHub for new releases...");
        return GitHubUpdateChecker.checkForUpdate();
    }

    /**
     * A simple helper method that downloads a new version from the provided URL.
     * Designed to be called from within a UiTaskExecutor.
     * @param info The update information containing the download URL.
     * @param progressUpdater The consumer to update the UI message.
     * @return The downloaded File.
     * @throws IOException if the download fails.
     */
    public File downloadUpdate(GitHubUpdateChecker.UpdateInfo info, Consumer<String> progressUpdater) throws IOException {
        progressUpdater.accept("Contacting download server...");
        URL downloadUrl = new URL(info.assetDownloadUrl());
        File tempFile = File.createTempFile("matchmaker-update-", ".jar");
        try (InputStream in = downloadUrl.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            progressUpdater.accept("Downloading version " + info.latestVersion() + "...");
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
        // The logic is now beautifully simple, just pass it along!
        scriptService.applyUpdateAndRestart(newJarFile, onIdeError, onError);
    }
}

