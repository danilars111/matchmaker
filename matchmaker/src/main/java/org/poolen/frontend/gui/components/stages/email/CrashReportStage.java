package org.poolen.frontend.gui.components.stages.email;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.poolen.util.AppDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * A specific implementation of {@link BaseEmailFormStage} for sending crash reports.
 * It automatically includes a stack trace and attempts to find and attach the latest log file.
 */
public class CrashReportStage extends BaseEmailFormStage {

    private static final Logger logger = LoggerFactory.getLogger(CrashReportStage.class);

    private final Throwable exception;
    private TextArea userInfoArea;
    private TextArea stackTraceArea;

    public CrashReportStage(Throwable e, ConfigurableApplicationContext context) {
        // Pass the context to the parent constructor
        super(context); // This will call createFormContent()

        // --- OUR FIX, MY LOVE! ---
        // The super() constructor has run, and createFormContent() has
        // created our stackTraceArea, but it's empty.
        // NOW, we can safely set our field and update the text area!
        this.exception = e;
        if (stackTraceArea != null) {
            stackTraceArea.setText(getStackTraceAsString(this.exception));
        } else {
            // This should never happen, but it's a good safeguard!
            logger.error("stackTraceArea was null after super() constructor!");
        }
    }

    @Override
    protected String getWindowTitle() {
        return "Crash Report";
    }

    @Override
    protected double[] getWindowSize() {
        return new double[]{500, 400}; // width, height
    }

    @Override
    protected String getTaskTitle() {
        return "Sending crash report...";
    }

    @Override
    protected String getSuccessMessage() {
        return "Report sent! Thank you!";
    }

    /**
     * Creates the specific UI for the crash report window.
     */
    @Override
    protected Node createFormContent() {
        Label headerLabel = new Label("The Matchmaker app has run into a problem!");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #D32F2F;");
        Label infoLabel = new Label("You can help fix this by sending a crash report. " +
                "This will send the error details and your app log file.");

        Label userPromptLabel = new Label("Optional: Please describe what you were doing:");
        userInfoArea = new TextArea();
        userInfoArea.setPromptText("e.g., 'I was trying to add a new player...'");
        userInfoArea.setWrapText(true);
        userInfoArea.setPrefHeight(80);

        // --- OUR FIX, MY LOVE! ---
        // We create the text area, but we *don't* set its text here!
        // The 'exception' field is still null at this point!
        stackTraceArea = new TextArea(); // No text set here!
        stackTraceArea.setEditable(false);
        stackTraceArea.setWrapText(true);
        TitledPane stackTracePane = new TitledPane("Error Details", stackTraceArea);
        stackTracePane.setExpanded(false);

        VBox contentBox = new VBox(10, headerLabel, infoLabel, userPromptLabel, userInfoArea, stackTracePane);
        contentBox.setPadding(new Insets(10, 0, 10, 0));
        return contentBox;
    }

    /**
     * Gathers the user comments, stack trace, and finds the log file.
     */
    @Override
    protected EmailDetails buildEmailDetails() {
        // This is called on the FX thread, so it's safe to read from UI components
        String userComments = userInfoArea.getText();
        String stackTrace = stackTraceArea.getText(); // We already have this

        // --- Find Log File ---
        // This is file I/O, but it's fast and simplifies the background task.
        // If this becomes slow, we could move it into the task lambda,
        // but then the background task isn't fully generic. This is a fine trade-off.
        String logFilePath = findLatestLogFile();

        // --- Build Email Content ---
        String subject = "Crash Report - Matchmaker App";
        String body = "A user has submitted a crash report.\n\n";

        if (userComments != null && !userComments.isBlank()) {
            body += "--- USER COMMENTS ---\n" + userComments + "\n\n";
        } else {
            body += "--- USER COMMENTS ---\n(User did not provide any comments.)\n\n";
        }
        body += "--- STACK TRACE ---\n" + stackTrace;

        if (logFilePath == null) {
            body += "\n\n(No log file found to attach.)";
        }

        return new EmailDetails(subject, body, logFilePath);
    }

    /**
     * Helper to find the most recent .log file in the logs directory.
     * @return Absolute path to the latest log file, or null if not found.
     */
    private String findLatestLogFile() {
        Path logDir = AppDataHandler.getAppDataDirectory().resolve("logs");
        if (Files.exists(logDir) && Files.isDirectory(logDir)) {
            try (var stream = Files.list(logDir)) {
                Optional<Path> latestLogFile = stream
                        .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".log"))
                        .max(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }));

                if (latestLogFile.isPresent()) {
                    logger.info("Found latest log file to attach: {}", latestLogFile.get());
                    return latestLogFile.get().toAbsolutePath().toString();
                } else {
                    logger.warn("No .log files found in {}. Sending report without log.", logDir);
                }
            } catch (IOException e) {
                logger.error("Could not read log directory: {}", logDir, e);
            }
        } else {
            logger.warn("Log directory does not exist: {}", logDir);
        }
        return null;
    }

    /**
     * A tidy little helper to get the stack trace as a string.
     */
    private String getStackTraceAsString(Throwable e) {
        // We can even add a little safety check, just in case!
        if (e == null) {
            logger.error("getStackTraceAsString called with a null exception!");
            return "Error: Exception was null.";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
