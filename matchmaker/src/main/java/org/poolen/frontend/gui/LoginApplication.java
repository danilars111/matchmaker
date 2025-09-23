package org.poolen.frontend.gui;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
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
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;

import java.net.BindException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main entry point for the application. This class handles the initial
 * Google authentication and data loading before showing the main application window.
 */
public class LoginApplication extends Application {

    private static final String SPREADSHEET_ID = "1YDOjqklvoJOfdV1nvA8IqyPpjqGrCMbP24VCLfC_OrU";

    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Button exitButton;
    private Button cancelButton; // Our new cancel button!
    private ProgressIndicator loadingIndicator;
    private Task<Exception> signInTask; // A reference to our running task
    private boolean hasAttemptedBindExceptionRetry = false; // Prevents infinite loops

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe - Sign In");
        primaryStage.setResizable(false); // Make the window non-resizable

        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        statusLabel = new Label("Checking for existing sign-in...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");
        statusLabel.setTextAlignment(TextAlignment.CENTER); // Center multiline text

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
                // First, tell the auth manager to stop its local server if it's running.
                GoogleAuthManager.abortAuthorization();
                // Then, cancel the task that is waiting for it.
                signInTask.cancel(true);
            }
        });

        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, cancelButton, exitButton);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        initialCheck();
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
        // Update UI for loading state
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

                        // Check if we were cancelled while connecting
                        if (Thread.currentThread().isInterrupted()) return;

                        updateMessage("Sign in successful!\nLoading data...");
                        SheetsServiceManager.loadData(SPREADSHEET_ID);
                    } catch (GoogleJsonResponseException e) {
                        System.out.println("Loading failed: " + e);
                    } catch (Exception e) {
                        // Don't store the exception if it was caused by our cancellation.
                        if (!(e instanceof InterruptedException || e.getCause() instanceof InterruptedException)) {
                            connectionException.set(e);
                        }
                    }
                });

                worker.start();

                try {
                    // This is the blocking call. It waits for the worker thread to finish.
                    worker.join();
                } catch (InterruptedException e) {
                    // This block executes if THIS thread (the Task's thread) is interrupted by task.cancel(true).
                    if (isCancelled()) {
                        worker.interrupt(); // Politely ask the worker thread to stop.
                        // We DO NOT throw an exception here. We just let the task finish,
                        // and because it was cancelled, the onCancelled() handler will be called.
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
                    new ErrorDialog(
                            "Sign-in was successful, but we couldn't load your data from the sheet, darling.",
                            root
                    ).showAndWait();
                }
                showManagementStage();
            }

            @Override
            protected void cancelled() {
                // This is the correct place to handle UI updates when the task is cancelled.
                statusLabel.textProperty().unbind();
                cancelButton.setVisible(false);
                GoogleAuthManager.logout();
                System.out.println("Sign-in cancelled by user.");

                // Reset UI to initial state
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

                // Our new logic to handle the BindException!
                if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
                    hasAttemptedBindExceptionRetry = true;
                    Platform.runLater(() -> {
                        statusLabel.setText("Port is busy. Attempting to reset sign-in...");
                        GoogleAuthManager.logout();
                        attemptSignInAndLoad(); // Try again!
                    });
                    return; // Skip the rest of the failure logic for now.
                }


                // We no longer need to check for CancellationException here,
                // as it will be handled by the onCancelled() method.
                System.err.println("Failed to connect: " + error.getMessage());
                error.printStackTrace();
                new ErrorDialog("Failed to connect: " + error.getMessage(), root).showAndWait();

                // Reset UI to initial state
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

    /**
     * A helper method to check if the root cause of an exception is a BindException.
     * @param throwable The exception to check.
     * @return True if the root cause is a BindException, false otherwise.
     */
    private boolean isRootCauseBindException(Throwable throwable) {
        if (throwable == null) return false;
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof BindException;
    }
}

