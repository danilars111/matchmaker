package org.poolen.frontend.exceptions;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.frontend.util.interfaces.ProgressAwareTask;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.util.AppDataHandler;
import org.poolen.web.email.EmailService;
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
import java.util.function.Consumer;

public class CrashReportWindow extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(CrashReportWindow.class);
    private final Throwable exception;
    private final ConfigurableApplicationContext springContext;
    private final Button sendButton;
    private final Button closeButton;
    private final Label statusLabel;
    private final TextArea stackTraceArea;
    private final TextArea userInfoArea;
    private final String toEmail = "daniellarsson054@gmail.com";
    private final UiTaskExecutor uiTaskExecutor;

    public CrashReportWindow(Throwable e, ConfigurableApplicationContext context) {
        this.exception = e;
        this.springContext = context;
        this.uiTaskExecutor = context.getBean(UiTaskExecutor.class);

        // --- Build the GUI ---
        setTitle("Crash Report!");
        initModality(Modality.APPLICATION_MODAL); // This blocks the main app

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // 1. The main message
        Label headerLabel = new Label("The Matchmaker app has run into a problem!");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #D32F2F;"); // A nice red!
        Label infoLabel = new Label("You can help fix this by sending a crash report. " +
                "This will send the error details and your app log file.");

        // --- NEW! The optional user comments box ---
        Label userPromptLabel = new Label("Optional: Please describe what you were doing when this happened:");
        userInfoArea = new TextArea();
        userInfoArea.setPromptText("e.g., 'I was trying to add a new player and clicked...'");
        userInfoArea.setWrapText(true);
        userInfoArea.setPrefHeight(80); // Give it a bit of space
        // ------------------------------------------

        // 2. The hidden stack trace for the curious!
        stackTraceArea = new TextArea(getStackTraceAsString(e));
        stackTraceArea.setEditable(false);
        stackTraceArea.setWrapText(true);
        TitledPane stackTracePane = new TitledPane("Error Details", stackTraceArea);
        stackTracePane.setExpanded(false); // Keep it tidy and hidden!

        // We add our new bits right into the VBox!
        VBox topBox = new VBox(10, headerLabel, infoLabel, userPromptLabel, userInfoArea, stackTracePane);
        root.setCenter(topBox);

        // 3. The lovely buttons at the bottom
        statusLabel = new Label(); // For "Sending..."
        statusLabel.setPadding(new Insets(5, 0, 0, 0));
        sendButton = new Button("Send Report");
        closeButton = new Button("Close");
        sendButton.setOnAction(evt -> handleSendReport());
        closeButton.setOnAction(evt -> close());

        HBox buttonBox = new HBox(10, statusLabel, sendButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        root.setBottom(buttonBox);

        // Let's make the window a bit taller to fit our new box!
        Scene scene = new Scene(root, 500, 400);
        setScene(scene);
    }
    private void handleSendReport() {
        sendButton.setDisable(true);
        closeButton.setDisable(true);
        statusLabel.setText(""); // The overlay will handle the status now

        // --- IMPORTANT ---
        // Read all data from the UI *before* starting the background thread!
        final String userComments = userInfoArea.getText();
        final String stackTrace = getStackTraceAsString(exception);

        Consumer<Void> onSuccess = (result) -> {
            statusLabel.setText("Report sent! Thank you!");
            closeButton.setDisable(false);
        };

        Consumer<Throwable> onError = (error) -> {
            logger.error("Oh, the irony! The crash reporter... crashed!", error);
            statusLabel.setText("Could not send report. " + error.getMessage());
            // Re-enable buttons so the user can try again
            closeButton.setDisable(false);
            sendButton.setDisable(false);
        };

        uiTaskExecutor.execute(
                this, // The 'owner' window (this CrashReportWindow is a Stage, which is a Window)
                "Sending report, please wait...",
                "Report sent! Thank you!",
                (updater) -> {
                    EmailService emailService = springContext.getBean(EmailService.class);

                    Path logDir = AppDataHandler.getAppDataDirectory().resolve("logs");
                    String logFilePath = null; // We'll set this if we find one

                    if (Files.exists(logDir) && Files.isDirectory(logDir)) {
                        try (var stream = Files.list(logDir)) {
                            Optional<Path> latestLogFile = stream
                                    .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".log"))
                                    .max(Comparator.comparingLong(p -> {
                                        try {
                                            return Files.getLastModifiedTime(p).toMillis();
                                        } catch (IOException e) {
                                            return 0L; // Couldn't read time, sort it to the beginning
                                        }
                                    }));

                            if (latestLogFile.isPresent()) {
                                logFilePath = latestLogFile.get().toString();
                                logger.info("Found latest log file to attach: {}", logFilePath);
                            } else {
                                logger.warn("No .log files found in {}. Sending report without log.", logDir);
                            }
                        } catch (IOException e) {
                            logger.error("Could not read log directory: {}", logDir, e);
                            // Can't find the log, but we can still send the report!
                        }
                    } else {
                        logger.warn("Log directory does not exist: {}", logDir);
                    }

                    // --- Set up the email (using the final variables from the FX thread) ---
                    String subject = "Crash Report - Matchmaker App";

                    String body = "A user has submitted a crash report.\n\n";
                    if (userComments != null && !userComments.isBlank()) {
                        body += "--- USER COMMENTS ---\n" + userComments + "\n\n";
                    } else {
                        body += "--- USER COMMENTS ---\n(User did not provide any comments.)\n\n";
                    }
                    body += "--- STACK TRACE ---\n" + stackTrace;

                    // --- Send the email (Network I/O, perfect for background) ---
                    if (logFilePath != null) {
                        emailService.sendMessageWithAttachment(toEmail, subject, body, logFilePath);
                    } else {
                        body += "\n\n(No log file found to attach.)";
                        emailService.sendSimpleMessage(toEmail, subject, body);
                    }
                    return null; // We don't need to return anything
                },
                onSuccess,
                onError
        );
    }

    /**
     * A tidy little helper to get the stack trace as a string.
     */
    private String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
