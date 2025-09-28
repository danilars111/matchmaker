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
        signInButton.setOnAction(e -> connectAndLoadData());

        exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14px; -fx-background-color: #6c757d; -fx-text-fill: white;");
        exitButton.setVisible(false);
        exitButton.setOnAction(e -> Platform.exit());

        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, exitButton);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        checkForUpdates();
    }

    private void checkForUpdates() {
        uiGithubTaskService.checkForUpdate(
                primaryStage,
                (updateInfo) -> {
                    if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                        downloadAndApplyUpdate(updateInfo);
                    } else {
                        initialCredentialCheck();
                    }
                },
                (error) -> {
                    System.err.println("Update check failed: " + error.getMessage());
                    initialCredentialCheck();
                }
        );
    }

    private void downloadAndApplyUpdate(GitHubUpdateChecker.UpdateInfo info) {
        uiGithubTaskService.downloadUpdate(
                primaryStage,
                info,
                (newJarFile) -> {
                    // On successful download, ask the service to apply the update.
                    uiGithubTaskService.applyUpdateAndRestart(
                            newJarFile,
                            () -> {
                                new ErrorDialog("Cannot update while running in an IDE, darling!", root).showAndWait();
                                initialCredentialCheck();
                            },
                            (error) -> {
                                new ErrorDialog("Update failed: " + error.getMessage(), root).showAndWait();
                                initialCredentialCheck();
                            }
                    );
                },
                (error) -> { // This runs if the download fails
                    new ErrorDialog("Failed to download update: " + error.getMessage(), root).showAndWait();
                    initialCredentialCheck();
                }
        );
    }

    private void initialCredentialCheck() {
        uiTaskExecutor.execute(
                primaryStage,
                "Checking for existing session...",
                (progressUpdater) -> GoogleAuthManager.hasStoredCredentials(),
                (hasCredentials) -> {
                    if (hasCredentials) {
                        connectAndLoadData();
                    } else {
                        showLoginUI("Please sign in to continue.");
                    }
                },
                (error) -> {
                    showLoginUI("An error occurred. Please try signing in.");
                    new ErrorDialog("Failed to check for credentials: " + error.getMessage(), root).showAndWait();
                }
        );
    }

    private void connectAndLoadData() {
        uiTaskExecutor.execute(
                primaryStage,
                "Connecting...",
                (progressUpdater) -> {
                    uiGoogleTaskService.connectToGoogle(progressUpdater);
                    uiPersistenceService.findAllWithProgress(progressUpdater);
                    return "Successfully connected and loaded data!";
                },
                (successMessage) -> {
                    System.out.println(successMessage);
                    showManagementStage();
                },
                (error) -> {
                    if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
                        hasAttemptedBindExceptionRetry = true;
                        Platform.runLater(() -> {
                            GoogleAuthManager.logout();
                            showLoginUI("Port is busy. Please try signing in again.");
                        });
                        return;
                    }
                    error.printStackTrace();
                    new ErrorDialog("Failed to connect or load data: " + error.getMessage(), root).showAndWait();
                    showLoginUI("Please sign in to continue.");
                }
        );
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

