package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.application.Platform;
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
import org.poolen.MatchmakerApplication;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.util.services.UiGithubTaskService;
import org.poolen.frontend.util.services.UiGoogleTaskService;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.BindException;

/**
 * The main entry point for the application. This class orchestrates the startup
 * sequence by delegating to various UI services for async operations.
 */
public class LoginApplication extends Application {
    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Button exitButton;
    private ProgressIndicator loadingIndicator;

    private boolean hasAttemptedBindExceptionRetry = false;

    // Our beautifully separated services!
    private UiTaskExecutor uiTaskExecutor;
    private UiPersistenceService uiPersistenceService;
    private UiGoogleTaskService uiGoogleTaskService;
    private UiGithubTaskService uiGithubTaskService;

    // A helper class to pass results from the background thread to the UI thread.
    private static class StartupResult {
        enum Status { UPDATE_READY, LOGIN_SUCCESSFUL, SHOW_LOGIN_UI }
        private final Status status;
        private final Object data;

        public StartupResult(Status status, Object data) {
            this.status = status;
            this.data = data;
        }

        public StartupResult(Status status) {
            this(status, null);
        }

        public Status getStatus() { return status; }
        public Object getData() { return data; }
    }


    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(MatchmakerApplication.class).run();
        // Get the beans for our services after the context is initialized.
        uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);
        uiPersistenceService = springContext.getBean(UiPersistenceService.class);
        uiGoogleTaskService = springContext.getBean(UiGoogleTaskService.class);
        uiGithubTaskService = springContext.getBean(UiGithubTaskService.class);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe - Sign In");
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
        signInButton.setOnAction(e -> handleUserLogin());

        exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14px; -fx-background-color: #6c757d; -fx-text-fill: white;");
        exitButton.setVisible(false);
        exitButton.setOnAction(e -> Platform.exit());

        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, exitButton);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        beginStartupSequence();
    }

    /**
     * This is the main entry point for the application's automatic startup logic.
     * It runs all startup tasks sequentially in a single background thread and
     * shows a single success animation at the very end.
     */
    private void beginStartupSequence() {
        uiTaskExecutor.execute(
                primaryStage,
                "Starting application...",
                "Ready!", // The final success message for the entire sequence!
                (progressUpdater) -> {
                    // --- 1. UPDATE CHECK ---
                    try {
                        progressUpdater.accept("Checking for updates...");
                        GitHubUpdateChecker.UpdateInfo updateInfo = uiGithubTaskService.checkForUpdate(progressUpdater);
                        if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                            progressUpdater.accept("Downloading new version...");
                            File newJarFile = uiGithubTaskService.downloadUpdate(updateInfo, progressUpdater);
                            // Return the downloaded file to the UI thread to handle the restart logic.
                            return new StartupResult(StartupResult.Status.UPDATE_READY, newJarFile);
                        }
                    } catch (Exception e) {
                        System.err.println("Update process failed, continuing startup: " + e.getMessage());
                        // Don't stop, just log the error and continue to the next step.
                    }

                    // --- 2. CREDENTIAL CHECK & AUTO-LOGIN ---
                    progressUpdater.accept("Checking for existing session...");
                    if (GoogleAuthManager.hasStoredCredentials()) {
                        uiGoogleTaskService.connectToGoogle(progressUpdater);
                        uiPersistenceService.findAllWithProgress(progressUpdater);
                        return new StartupResult(StartupResult.Status.LOGIN_SUCCESSFUL);
                    } else {
                        return new StartupResult(StartupResult.Status.SHOW_LOGIN_UI);
                    }
                },
                (result) -> { // onSuccess on UI Thread
                    switch (result.getStatus()) {
                        case UPDATE_READY:
                            // The background task is done, now we can interact with the UI to apply the update.
                            applyUpdate((File) result.getData());
                            break;
                        case LOGIN_SUCCESSFUL:
                            showManagementStage();
                            break;
                        case SHOW_LOGIN_UI:
                            showLoginUI("Please sign in to continue.");
                            break;
                    }
                },
                this::handleStartupError // onError on UI Thread
        );
    }

    /**
     * This is called when the user explicitly clicks the "Sign In" button.
     * It skips the update and credential checks.
     */
    private void handleUserLogin() {
        uiTaskExecutor.execute(
                primaryStage,
                "Connecting...",
                "Successfully connected!",
                (progressUpdater) -> {
                    uiGoogleTaskService.connectToGoogle(progressUpdater);
                    uiPersistenceService.findAllWithProgress(progressUpdater);
                    return "LOGIN_SUCCESSFUL";
                },
                (result) -> {
                    if ("LOGIN_SUCCESSFUL".equals(result)) {
                        showManagementStage();
                    }
                },
                this::handleStartupError
        );
    }

    /**
     * Attempts to apply a new update. If successful, the app restarts.
     * If it fails for any reason, it proceeds to the next startup step.
     */
    private void applyUpdate(File newJarFile) {
        // This call will either exit the app or call one of the lambdas below.
        uiGithubTaskService.applyUpdateAndRestart(
                newJarFile,
                () -> {
                    new ErrorDialog("Cannot update while running in an IDE, darling!", root).showAndWait();
                    showLoginUI("Please sign in to continue.");
                },
                (error) -> {
                    new ErrorDialog("Update failed: " + error.getMessage(), root).showAndWait();
                    showLoginUI("Please sign in to continue.");
                }
        );
    }

    /**
     * A centralized error handler for any failure during the startup or login process.
     */
    private void handleStartupError(Throwable error) {
        if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
            hasAttemptedBindExceptionRetry = true;
            Platform.runLater(() -> {
                GoogleAuthManager.logout();
                showLoginUI("A service is already running on the required port. Please close other instances and try signing in again.");
            });
            return;
        }

        GoogleAuthManager.logout();
        new ErrorDialog("An error occurred: " + error.getMessage(), root).showAndWait();
        error.printStackTrace();
        showLoginUI("Something went wrong. Please try signing in again.");
    }


    private void showLoginUI(String message) {
        statusLabel.setText(message);
        loadingIndicator.setVisible(false);
        signInButton.setVisible(true);
        exitButton.setVisible(true);
    }

    private void showManagementStage() {
        primaryStage.close();
        ManagementStage managementStage = springContext.getBean(ManagementStage.class);
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

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}

