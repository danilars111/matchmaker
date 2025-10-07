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
import org.poolen.ApplicationLauncher;
import org.poolen.MatchmakerApplication;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.util.services.ApplicationScriptService;
import org.poolen.frontend.util.services.UiGithubTaskService;
import org.poolen.frontend.util.services.UiGoogleTaskService;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.util.PropertiesManager;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.BindException;

public class LoginApplication extends Application {
    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Button exitButton;
    private ProgressIndicator loadingIndicator;

    private boolean hasAttemptedBindExceptionRetry = false;
    private Exception springStartupError = null;

    private UiTaskExecutor uiTaskExecutor;
    private UiPersistenceService uiPersistenceService;
    private UiGoogleTaskService uiGoogleTaskService;
    private UiGithubTaskService uiGithubTaskService;
    private ApplicationScriptService scriptService;

    private static class StartupResult {
        enum Status { UPDATE_READY, LOGIN_SUCCESSFUL, SHOW_LOGIN_UI }
        final Status status;
        final Object data;
        public StartupResult(Status status, Object data) { this.status = status; this.data = data; }
        public StartupResult(Status status) { this(status, null); }
    }

    @Override
    public void init() {
        boolean isH2Mode = getParameters().getRaw().stream().anyMatch("--h2"::equalsIgnoreCase);
        if (PropertiesManager.propertiesFileExists() || isH2Mode) {
            try {
                springContext = new SpringApplicationBuilder(MatchmakerApplication.class)
                        .properties(ApplicationLauncher.getConfigLocation())
                        .run(getParameters().getRaw().toArray(new String[0]));
                uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);
                uiPersistenceService = springContext.getBean(UiPersistenceService.class);
                uiGoogleTaskService = springContext.getBean(UiGoogleTaskService.class);
                uiGithubTaskService = springContext.getBean(UiGithubTaskService.class);
                scriptService = springContext.getBean(ApplicationScriptService.class);
            } catch (Exception e) {
                springStartupError = e;
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        boolean isH2Mode = getParameters().getRaw().stream().anyMatch("--h2"::equalsIgnoreCase);
        if (!isH2Mode && !PropertiesManager.propertiesFileExists()) {
            SetupStage setupStage = new SetupStage(new ApplicationScriptService(), null);
            setupStage.show();
            return;
        } else if (springStartupError != null) {
            String errorMessage = "The application couldn't start with the current settings.\n" +
                    "Please check your database connection details.";
            SetupStage setupStage = new SetupStage(new ApplicationScriptService(), errorMessage);
            setupStage.show();
            return;
        }

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

        Scene scene = new Scene(root, 450, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        beginStartupSequence();
    }

    private void beginStartupSequence() {
        uiTaskExecutor.execute(
                primaryStage,
                "Starting application...",
                "Ready!",
                (updater) -> {
                    try {
                        GitHubUpdateChecker.UpdateInfo updateInfo = uiGithubTaskService.checkForUpdate(updater);
                        if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                            File newJarFile = uiGithubTaskService.downloadUpdate(updateInfo, updater);
                            return new StartupResult(StartupResult.Status.UPDATE_READY, newJarFile);
                        }
                    } catch (Exception e) {
                        System.err.println("Update process failed, continuing startup: " + e.getMessage());
                    }

                    updater.updateStatus("Checking for existing session...");
                    if (GoogleAuthManager.hasStoredCredentials()) {
                        uiGoogleTaskService.connectToGoogle(updater);
                        uiPersistenceService.findAllWithProgress(updater);
                        return new StartupResult(StartupResult.Status.LOGIN_SUCCESSFUL);
                    } else {
                        return new StartupResult(StartupResult.Status.SHOW_LOGIN_UI);
                    }
                },
                (result) -> {
                    switch (result.status) {
                        case UPDATE_READY -> applyUpdate((File) result.data);
                        case LOGIN_SUCCESSFUL -> showManagementStage();
                        case SHOW_LOGIN_UI -> showLoginUI("Please sign in to continue.");
                    }
                },
                this::handleStartupError
        );
    }

    private void handleUserLogin() {
        // We no longer need to manage the overlay directly from here!
        uiTaskExecutor.execute(
                primaryStage, "Connecting...", "Successfully connected!",
                (updater) -> { // Our beautiful new updater!
                    uiGoogleTaskService.connectToGoogle(updater);
                    uiPersistenceService.findAllWithProgress(updater);
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

    private void applyUpdate(File newJarFile) {
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
        while (cause != null) {
            if (cause instanceof BindException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}

