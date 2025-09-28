package org.poolen.frontend.util.services;

import javafx.stage.Window;
import org.poolen.frontend.gui.LoginApplication;
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
import java.nio.file.Files;
import java.util.function.Consumer;

@Service
@Lazy
public class UiGithubTaskService {
    private final UiTaskExecutor uiTaskExecutor;

    @Autowired
    public UiGithubTaskService(UiTaskExecutor uiTaskExecutor) {
        this.uiTaskExecutor = uiTaskExecutor;
    }

    /**
     * Checks for a new version on GitHub and executes a callback with the result.
     *
     * @param owner    The owner window for the loading overlay.
     * @param onResult A consumer for the UpdateInfo if the check succeeds.
     * @param onError  A consumer for the exception if the check fails.
     */
    public void checkForUpdate(Window owner, Consumer<GitHubUpdateChecker.UpdateInfo> onResult, Consumer<Throwable> onError) {
        uiTaskExecutor.execute(
                owner,
                "Checking for updates...",
                (progressUpdater) ->  {
                    progressUpdater.accept("Checking for updates...");
                    return GitHubUpdateChecker.checkForUpdate();
                },

                onResult,
                onError
        );
    }

    /**
     * Downloads a new version from the provided URL.
     *
     * @param owner    The owner window for the loading overlay.
     * @param info     The update information containing the download URL.
     * @param onResult A consumer for the downloaded File if successful.
     * @param onError  A consumer for the exception if the download fails.
     */
    public void downloadUpdate(Window owner, GitHubUpdateChecker.UpdateInfo info, Consumer<File> onResult, Consumer<Throwable> onError) {
        uiTaskExecutor.execute(
                owner,
                "Update found! Downloading version " + info.latestVersion() + "...",
                (progressUpdater) -> {
                    progressUpdater.accept("Update found! Downloading version ");
                    URL downloadUrl = new URL(info.assetDownloadUrl());
                    File tempFile = File.createTempFile("matchmaker-update-", ".jar");
                    try (InputStream in = downloadUrl.openStream();
                         ReadableByteChannel rbc = Channels.newChannel(in);
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    }
                    return tempFile;
                },
                onResult,
                onError
        );
    }

    /**
     * Tries to apply the downloaded update and restart the application.
     * It handles the logic for creating an updater script.
     *
     * @param newJarFile The newly downloaded JAR file.
     * @param onIdeError A callback to run if the application detects it's not running from a JAR.
     * @param onError    A callback to run if any exception occurs during the process.
     */
    public void applyUpdateAndRestart(File newJarFile, Runnable onIdeError, Consumer<Exception> onError) {
        try {
            File currentJar = new File(LoginApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (currentJar.isFile() && currentJar.getName().endsWith(".jar")) {
                createUpdaterAndRestart(currentJar, newJarFile);
            } else {
                onIdeError.run(); // It's not running from a JAR, so we can't update it.
            }
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /**
     * Creates an OS-specific script to replace the current JAR with the new one and restart the application.
     *
     * @param currentJar The file representing the currently running JAR.
     * @param newJar     The file representing the downloaded update.
     * @throws IOException If there's an error creating the script files.
     */
    private void createUpdaterAndRestart(File currentJar, File newJar) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String script = "@echo off\n" +
                    "echo Updating application...\n" +
                    "timeout /t 2 /nobreak > NUL\n" +
                    "del \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "move \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "echo Update complete. Restarting...\n" +
                    "start javaw -jar \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "del \"%~f0\"";
            File batFile = File.createTempFile("updater", ".bat");
            Files.writeString(batFile.toPath(), script);
            String vbsScript = "Set WshShell = CreateObject(\"WScript.Shell\") \n"
                    + "WshShell.Run \"\"\"" + batFile.getAbsolutePath() + "\"\"\", 0, False";
            File vbsFile = File.createTempFile("launcher", ".vbs");
            Files.writeString(vbsFile.toPath(), vbsScript);
            Runtime.getRuntime().exec("wscript.exe \"" + vbsFile.getAbsolutePath() + "\"");
        } else {
            File oldJar = new File(currentJar.getParentFile(), currentJar.getName() + ".old");
            String script = "#!/bin/bash\n" +
                    "echo \"Updating application...\"\n" +
                    "sleep 3\n" +
                    "mv \"" + currentJar.getAbsolutePath() + "\" \"" + oldJar.getAbsolutePath() + "\"\n" +
                    "mv \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "echo \"Update complete. Restarting...\"\n" +
                    "java -jar \"" + currentJar.getAbsolutePath() + "\" &\n" +
                    "sleep 3\n" +
                    "rm \"" + oldJar.getAbsolutePath() + "\"\n" +
                    "rm -- \"$0\"";
            File scriptFile = File.createTempFile("updater", ".sh");
            Files.writeString(scriptFile.toPath(), script);
            new ProcessBuilder("sh", scriptFile.getAbsolutePath()).start();
        }
        System.exit(0);
    }
}
