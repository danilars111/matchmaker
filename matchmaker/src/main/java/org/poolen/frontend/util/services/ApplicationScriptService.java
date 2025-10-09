package org.poolen.frontend.util.services;

import jakarta.annotation.PostConstruct;
import org.poolen.frontend.gui.LoginApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

@Service
public class ApplicationScriptService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationScriptService.class);

    @PostConstruct
    public void cleanupOldScripts() {
        logger.info("Housekeeping: Cleaning up old application script files...");
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            logger.warn("Temporary directory not found or is not a directory. Skipping script cleanup.");
            return;
        }

        File[] oldScripts = tempDir.listFiles((dir, name) ->
                name.startsWith("app-script-") || name.startsWith("silent-launcher-")
        );

        if (oldScripts != null) {
            logger.debug("Found {} old script files to delete.", oldScripts.length);
            for (File script : oldScripts) {
                try {
                    Files.delete(script.toPath());
                    logger.info(" > Deleted old script: {}", script.getName());
                } catch (IOException e) {
                    // This is not a critical error, so we just log it and continue.
                    logger.warn(" > Could not delete old script {}: {}", script.getName(), e.getMessage());
                }
            }
        } else {
            logger.debug("No old script files found to clean up.");
        }
    }


    /**
     * Triggers a simple restart of the application.
     * @param onIdeError A callback to run if the application detects it's not running from a JAR.
     * @param onError    A callback to run if any exception occurs during the process.
     */
    public void restart(Runnable onIdeError, Consumer<Exception> onError) {
        logger.info("Restart requested without arguments.");
        runRestartScript(onIdeError, onError, "");
    }

    /**
     * Triggers a restart of the application with the --h2 flag.
     * @param onIdeError A callback to run if the application detects it's not running from a JAR.
     * @param onError    A callback to run if any exception occurs during the process.
     */
    public void restartWithH2(Runnable onIdeError, Consumer<Exception> onError) {
        logger.info("Restart requested with --h2 argument.");
        runRestartScript(onIdeError, onError, "--h2");
    }

    private void runRestartScript(Runnable onIdeError, Consumer<Exception> onError, String args) {
        logger.debug("Attempting to run restart script with args: '{}'", args);
        try {
            File currentJar = getCurrentJar();
            if (currentJar == null) {
                logger.warn("Application is not running from a JAR. Aborting restart and calling onIdeError handler.");
                onIdeError.run();
                return;
            }

            String scriptName = isWindows() ? "win_restart.bat" : "nix_restart.sh";
            logger.debug("Using script '{}' for restart.", scriptName);
            String scriptContent = readResourceScript(scriptName)
                    .replace("{JAR_PATH}", "\"" + currentJar.getAbsolutePath() + "\"") // Always quote the JAR path
                    .replace("{ARGS}", args);

            if (scriptContent.contains("{JAR_PATH}") || scriptContent.contains("{ARGS}")) {
                throw new IOException("Failed to replace placeholder(s) in restart script.");
            }

            File scriptFile = createTempScript(scriptContent, isWindows() ? ".bat" : ".sh");
            launchScript(scriptFile);

        } catch (Exception e) {
            logger.error("Failed to execute restart script.", e);
            onError.accept(e);
        }
    }


    /**
     * Tries to apply the downloaded update and restart the application.
     *
     * @param newJar The newly downloaded JAR file.
     * @param onIdeError A callback to run if the application detects it's not running from a JAR.
     * @param onError    A callback to run if any exception occurs during the process.
     */
    public void applyUpdateAndRestart(File newJar, Runnable onIdeError, Consumer<Exception> onError) {
        logger.info("Attempting to apply update from '{}' and restart.", newJar.getAbsolutePath());
        try {
            File currentJar = getCurrentJar();
            if (currentJar == null) {
                logger.warn("Application is not running from a JAR. Aborting update and calling onIdeError handler.");
                onIdeError.run();
                return;
            }

            String scriptName = isWindows() ? "win_update.bat" : "nix_update.sh";
            logger.debug("Using script '{}' for update.", scriptName);
            String scriptContent = readResourceScript(scriptName)
                    .replace("{CURRENT_JAR_PATH}", "\"" + currentJar.getAbsolutePath() + "\"")
                    .replace("{NEW_JAR_PATH}", "\"" + newJar.getAbsolutePath() + "\"");

            if (scriptContent.contains("{CURRENT_JAR_PATH}") || scriptContent.contains("{NEW_JAR_PATH}")) {
                throw new IOException("Failed to replace placeholder(s) in update script.");
            }

            File scriptFile = createTempScript(scriptContent, isWindows() ? ".bat" : ".sh");
            launchScript(scriptFile);

        } catch (Exception e) {
            logger.error("Failed to apply update and restart.", e);
            onError.accept(e);
        }
    }

    private File getCurrentJar() {
        logger.trace("Attempting to locate the current running JAR file.");
        try {
            File jar = new File(LoginApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile() && jar.getName().endsWith(".jar")) {
                logger.trace("Found JAR file: {}", jar.getAbsolutePath());
                return jar;
            }
        } catch (URISyntaxException e) {
            // Ignore, this means we're not in a JAR.
            logger.trace("Not running from a JAR file (or URI syntax error).");
        }
        return null;
    }

    private String readResourceScript(String scriptName) throws IOException {
        logger.trace("Reading resource script: /scripts/{}", scriptName);
        try (InputStream is = getClass().getResourceAsStream("/scripts/" + scriptName)) {
            if (is == null) {
                throw new IOException("Script not found in resources: " + scriptName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private File createTempScript(String content, String suffix) throws IOException {
        logger.trace("Creating temporary script file with suffix '{}'.", suffix);
        File tempFile = File.createTempFile("app-script-", suffix);
        // We remove deleteOnExit() to prevent a race condition where the file is deleted
        // before the script host has a chance to run it. Our new cleanupOldScripts() method
        // will handle this responsibly on the next startup.
        Files.writeString(tempFile.toPath(), content);
        logger.debug("Created temporary script at: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    private void launchScript(File scriptFile) throws IOException {
        logger.info("Launching script: {}", scriptFile.getAbsolutePath());
        if (isWindows()) {
            logger.debug("Using Windows VBS launcher for silent execution.");
            // This is the classic, fabulous trick to run a batch file completely invisibly.
            String vbsContent = "Set WshShell = CreateObject(\"WScript.Shell\") \n"
                    + "WshShell.Run \"\"\"" + scriptFile.getAbsolutePath() + "\"\"\", 0, False";
            File vbsFile = File.createTempFile("silent-launcher-", ".vbs");
            // We also don't want to delete this file on exit for the same reason.
            Files.writeString(vbsFile.toPath(), vbsContent);
            Runtime.getRuntime().exec("wscript.exe \"" + vbsFile.getAbsolutePath() + "\"");
        } else {
            logger.debug("Using nix ProcessBuilder for script execution.");
            scriptFile.setExecutable(true);
            new ProcessBuilder("sh", scriptFile.getAbsolutePath()).start();
        }
        logger.info("Script launched successfully. Exiting current application instance.");
        System.exit(0);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
