package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main entry point for the application. This class handles the initial
 * update check, Google authentication, and data loading.
 */
public class LoginApplication extends Application {

    private static final String SPREADSHEET_ID = "1YDOjqklvoJOfdV1nvA8IqyPpjqGrCMbP24VCLfC_OrU";

    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Button exitButton;
    private Button cancelButton;
    private ProgressIndicator loadingIndicator;
    private Task<Exception> signInTask;
    private boolean hasAttemptedBindExceptionRetry = false;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe");
        primaryStage.setResizable(false);

        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        statusLabel = new Label("Starting up...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");
        statusLabel.setTextAlignment(TextAlignment.CENTER);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);

        signInButton = createGoogleSignInButton();
        signInButton.setVisible(false);
        signInButton.setOnAction(e -> attemptSignInAndLoad());

        exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14px; -fx-background-color: #6c757d; -fx-text-fill: white;");
        exitButton.setVisible(false);
        exitButton.setOnAction(e -> Platform.exit());

        cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-font-size: 14px; -fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> {
            if (signInTask != null) {
                GoogleAuthManager.abortAuthorization();
                signInTask.cancel(true);
            }
        });

        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, cancelButton, exitButton);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // The new first step: check for updates!
        checkForUpdates();
    }

    private void checkForUpdates() {
        statusLabel.setText("Checking for updates...");
        loadingIndicator.setVisible(true);

        Task<GitHubUpdateChecker.UpdateInfo> updateTask = new Task<>() {
            @Override
            protected GitHubUpdateChecker.UpdateInfo call() throws Exception {
                return GitHubUpdateChecker.checkForUpdate();
            }

            @Override
            protected void succeeded() {
                GitHubUpdateChecker.UpdateInfo info = getValue();
                if (info.isNewVersionAvailable() && info.assetDownloadUrl() != null) {
                    downloadAndApplyUpdate(info);
                } else {
                    initialCheck();
                }
            }

            @Override
            protected void failed() {
                System.err.println("Update check failed: " + getException().getMessage());
                initialCheck();
            }
        };
        new Thread(updateTask).start();
    }

    private void downloadAndApplyUpdate(GitHubUpdateChecker.UpdateInfo info) {
        statusLabel.setText("Update found! Downloading version " + info.latestVersion() + "...");

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                URL downloadUrl = new URL(info.assetDownloadUrl());
                File tempFile = File.createTempFile("matchmaker-update-", ".jar");
                try (InputStream in = downloadUrl.openStream();
                     ReadableByteChannel rbc = Channels.newChannel(in);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                }
                return tempFile;
            }

            @Override
            protected void succeeded() {
                try {
                    File newJar = getValue();
                    File currentJar = new File(LoginApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());

                    if (currentJar.isFile() && currentJar.getName().endsWith(".jar")) {
                        createUpdaterAndRestart(currentJar, newJar);
                    } else {
                        new ErrorDialog("Cannot update while running in an IDE, darling!", root).showAndWait();
                        initialCheck();
                    }
                } catch (Exception e) {
                    new ErrorDialog("Update failed: " + e.getMessage(), root).showAndWait();
                    initialCheck();
                }
            }

            @Override
            protected void failed() {
                new ErrorDialog("Failed to download update: " + getException().getMessage(), root).showAndWait();
                initialCheck();
            }
        };
        new Thread(downloadTask).start();
    }

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
            File scriptFile = File.createTempFile("updater", ".bat");
            Files.writeString(scriptFile.toPath(), script);
            Runtime.getRuntime().exec("cmd /c start " + scriptFile.getAbsolutePath());
        } else {
            String script = "#!/bin/bash\n" +
                    "echo \"Updating application...\"\n" +
                    "sleep 2\n" +
                    "rm \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "mv \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"\n" +
                    "echo \"Update complete. Restarting...\"\n" +
                    "java -jar \"" + currentJar.getAbsolutePath() + "\" &\n" +
                    "rm -- \"$0\"";
            File scriptFile = File.createTempFile("updater", ".sh");
            Files.writeString(scriptFile.toPath(), script);
            new ProcessBuilder("sh", scriptFile.getAbsolutePath()).start();
        }

        System.exit(0);
    }

    private void initialCheck() {
        Task<Boolean> checkTask = new Task<>() {
            @Override
            protected Boolean call() {
                return GoogleAuthManager.hasStoredCredentials();
            }

            @Override
            protected void succeeded() {
                if (getValue()) {
                    attemptSignInAndLoad();
                } else {
                    statusLabel.setText("Please sign in to continue.");
                    loadingIndicator.setVisible(false);
                    signInButton.setVisible(true);
                    exitButton.setVisible(true);
                }
            }

            @Override
            protected void failed() {
                statusLabel.setText("An error occurred. Please try signing in.");
                loadingIndicator.setVisible(false);
                signInButton.setVisible(true);
                exitButton.setVisible(true);
                new ErrorDialog("Failed to check for credentials: " + getException().getMessage(), root).showAndWait();
            }
        };
        new Thread(checkTask).start();
    }

    private void attemptSignInAndLoad() {
        statusLabel.setText("Connecting...");
        loadingIndicator.setVisible(true);
        signInButton.setVisible(false);
        exitButton.setVisible(false);
        cancelButton.setVisible(true);

        signInTask = new Task<>() {
            @Override
            protected Exception call() throws Exception {
                final AtomicReference<Exception> connectionException = new AtomicReference<>(null);
                final AtomicReference<Exception> loadDataException = new AtomicReference<>(null);

                final Thread worker = new Thread(() -> {
                    try {
                        updateMessage("Attempting to connect...\nPlease check your browser to sign in.");
                        SheetsServiceManager.connect();
                        Platform.runLater(() -> cancelButton.setVisible(false));
                        if (Thread.currentThread().isInterrupted()) return;
                        updateMessage("Sign in successful!\nLoading data...");
                        SheetsServiceManager.loadData(SPREADSHEET_ID);
                    } catch (Exception e) {
                        if (!(e instanceof InterruptedException || e.getCause() instanceof InterruptedException)) {
                            connectionException.set(e);
                        }
                    }
                });

                worker.start();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    if (isCancelled()) {
                        worker.interrupt();
                        return null;
                    }
                }

                if (connectionException.get() != null) throw connectionException.get();
                return loadDataException.get();
            }

            @Override
            protected void succeeded() {
                statusLabel.textProperty().unbind();
                cancelButton.setVisible(false);
                Exception loadDataError = getValue();

                if (loadDataError != null) {
                    new ErrorDialog("Sign-in was successful, but we couldn't load your data from the sheet, darling.", root).showAndWait();
                }
                showManagementStage();
            }

            @Override
            protected void cancelled() {
                statusLabel.textProperty().unbind();
                cancelButton.setVisible(false);
                GoogleAuthManager.logout();
                System.out.println("Sign-in cancelled by user.");
                statusLabel.setText("Please sign in to continue.");
                loadingIndicator.setVisible(false);
                signInButton.setVisible(true);
                exitButton.setVisible(true);
            }

            @Override
            protected void failed() {
                statusLabel.textProperty().unbind();
                cancelButton.setVisible(false);
                Throwable error = getException();
                if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
                    hasAttemptedBindExceptionRetry = true;
                    Platform.runLater(() -> {
                        statusLabel.setText("Port is busy. Attempting to reset sign-in...");
                        GoogleAuthManager.logout();
                        attemptSignInAndLoad();
                    });
                    return;
                }
                System.err.println("Failed to connect: " + error.getMessage());
                error.printStackTrace();
                new ErrorDialog("Failed to connect: " + error.getMessage(), root).showAndWait();
                statusLabel.setText("Please sign in to continue.");
                loadingIndicator.setVisible(false);
                signInButton.setVisible(true);
                exitButton.setVisible(true);
            }
        };

        statusLabel.textProperty().bind(signInTask.messageProperty());
        new Thread(signInTask).start();
    }

    private void showManagementStage() {
        primaryStage.close();
        ManagementStage managementStage = new ManagementStage();
        managementStage.show();
    }

    private Button createGoogleSignInButton() {
        Image signInImage = new Image(getClass().getResourceAsStream("/images/google_sign_in_button.png"));
        ImageView signInImageView = new ImageView(signInImage);
        signInImageView.setFitHeight(40);
        signInImageView.setPreserveRatio(true);
        Button googleButton = new Button();
        googleButton.setGraphic(signInImageView);
        googleButton.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        googleButton.setOnMouseEntered(e -> googleButton.setOpacity(0.9));
        googleButton.setOnMouseExited(e -> googleButton.setOpacity(1.0));
        return googleButton;
    }

    private boolean isRootCauseBindException(Throwable throwable) {
        if (throwable == null) return false;
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof BindException;
    }
}

