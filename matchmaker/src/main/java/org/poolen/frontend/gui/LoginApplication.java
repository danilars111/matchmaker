package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.poolen.ApplicationLauncher;
import org.poolen.MatchmakerApplication;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.*;
import org.poolen.frontend.util.services.TestDataGenerator;
import org.poolen.util.PropertiesManager;
import org.poolen.web.github.GitHubUpdateChecker;
import org.poolen.web.google.GoogleAuthManager;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.BindException;
import java.util.Optional;

/**
 * The main entry point for the JavaFX GUI.
 * This refactored version aims to separate concerns:
 * - UI setup and management remains here.
 * - Spring context and application logic is delegated where possible.
 * - The startup sequence is clearer and more modular.
 */
public class LoginApplication extends Application {

    // --- State and Context ---
    private ConfigurableApplicationContext springContext;
    private Exception springStartupError = null;
    private boolean hasAttemptedBindExceptionRetry = false;

    // --- Services (Dependencies) ---
    private UiTaskExecutor uiTaskExecutor;
    private UiPersistenceService uiPersistenceService;
    private UiGoogleTaskService uiGoogleTaskService;
    private UiGithubTaskService uiGithubTaskService;
    private TestDataGenerator testDataGenerator;
    private CoreProvider coreProvider;

    // --- UI Components ---
    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Label continueOfflineLabel;
    private Button exitButton;
    private ProgressIndicator loadingIndicator;

    /**
     * Represents the possible outcomes of the startup sequence.
     * This helps in decoupling the startup logic from the UI updates.
     */
    private static class StartupResult {
        enum Status { UPDATE_READY, LOGIN_SUCCESSFUL, SHOW_LOGIN_UI }
        final Status status;
        final Object data;

        public StartupResult(Status status, Object data) { this.status = status; this.data = data; }
        public StartupResult(Status status) { this(status, null); }
    }

    /**
     * Initialises the Spring context and retrieves necessary beans.
     * This phase happens before the UI is built.
     */
    @Override
    public void init() {
        boolean isH2Mode = isH2Mode();
        if (PropertiesManager.propertiesFileExists() || isH2Mode) {
            try {
                springContext = new SpringApplicationBuilder(MatchmakerApplication.class)
                        .properties(ApplicationLauncher.getConfigLocation())
                        .run(getParameters().getRaw().toArray(new String[0]));
                // All our lovely beans are now ready to be retrieved.
                initialiseServices();
            } catch (Exception e) {
                // We'll store this error and handle it gracefully in the start() method.
                springStartupError = e;
            }
        }
    }

    /**
     * The main entry point for the JavaFX application.
     * It sets up the stage, performs pre-flight checks, and kicks off the startup sequence.
     */
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe - Sign In");

        // First, perform checks that might prevent the app from starting at all.
        if (isFirstTimeSetup()) {
            return; // The setup stage will handle the rest.
        }

        // Now, create the main login UI.
        initialiseUI();

        // If H2 mode is active, ask the user about generating test data.
        Optional<Integer> testDataCount = askForTestDataGenerationIfInH2Mode();

        // With the UI ready, begin the main startup sequence in the background.
        beginStartupSequence(testDataCount.orElse(0));
    }

    /**
     * Handles the clean shutdown of the application.
     */
    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }


    // --- UI Initialisation and Management ---

    /**
     * Creates and configures all the UI components for the login window.
     */
    private void initialiseUI() {
        primaryStage.setResizable(false);

        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        // Using an external stylesheet would be even better for tidiness!
        root.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        statusLabel = new Label("Starting up...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");
        statusLabel.setTextAlignment(TextAlignment.CENTER);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);

        signInButton = createGoogleSignInButton();
        signInButton.setVisible(false);
        signInButton.setOnAction(e -> handleUserLogin());

        continueOfflineLabel = new Label("Continue without signing in");
        continueOfflineLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0000EE; -fx-underline: true;");
        continueOfflineLabel.setCursor(Cursor.HAND);
        continueOfflineLabel.setOnMouseClicked(e -> showManagementStage());
        continueOfflineLabel.setVisible(false);

        exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14px; -fx-background-color: #6c757d; -fx-text-fill: white;");
        exitButton.setVisible(false);
        exitButton.setOnAction(e -> Platform.exit());

        // Bring the "continue" link a bit closer to the button above it.
        VBox.setMargin(continueOfflineLabel, new Insets(-10, 0, 0, 0));
        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, continueOfflineLabel, exitButton);

        Scene scene = new Scene(root, 450, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Updates the UI to show the login prompt.
     * @param message The message to display to the user.
     */
    private void showLoginUI(String message) {
        statusLabel.setText(message);
        loadingIndicator.setVisible(false);
        signInButton.setVisible(true);
        continueOfflineLabel.setVisible(true);
        exitButton.setVisible(true);
    }

    /**
     * Closes the login window and opens the main application management window.
     */
    private void showManagementStage() {
        primaryStage.close();
        ManagementStage managementStage = springContext.getBean(ComponentFactoryService.class).getManagementStage();
        managementStage.show();
    }

    /**
     * Creates a custom button with the Google Sign-In graphic.
     */
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


    // --- Application Startup and Logic ---

    /**
     * Performs initial checks before the main UI is shown. If a check fails
     * and a different stage is shown (like the SetupStage), this method returns true.
     *
     * @return true if the startup process should be aborted, false otherwise.
     */
    private boolean isFirstTimeSetup() {
        if (!isH2Mode() && !PropertiesManager.propertiesFileExists()) {
            new SetupStage(new ApplicationScriptService(), null).show();
            return true;
        }

        if (springStartupError != null) {
            String errorMessage = "The application couldn't start with the current settings.\n" +
                    "Please check your database connection details.";
            new SetupStage(new ApplicationScriptService(), errorMessage).show();
            return true;
        }
        return false;
    }

    /**
     * Retrieves all required service beans from the Spring context.
     */
    private void initialiseServices() {
        uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);
        uiPersistenceService = springContext.getBean(UiPersistenceService.class);
        uiGoogleTaskService = springContext.getBean(UiGoogleTaskService.class);
        uiGithubTaskService = springContext.getBean(UiGithubTaskService.class);
        coreProvider = springContext.getBean(ComponentFactoryService.class);
        if (isH2Mode()) {
            testDataGenerator = springContext.getBean(TestDataGenerator.class);
        }
    }

    /**
     * Executes the main startup sequence in a background thread to keep the UI responsive.
     *
     * @param testDataPlayerCount The number of test players to generate (0 if none).
     */
    private void beginStartupSequence(int testDataPlayerCount) {
        uiTaskExecutor.execute(
                primaryStage,
                "Starting application...",
                "Ready!",
                (updater) -> {
                    // This lambda now contains the core startup logic.
                    // It could even be moved to a dedicated 'StartupService' class.

                    // 1. Generate Test Data if requested
                    if (testDataPlayerCount > 0) {
                        updater.updateStatus("Generating " + testDataPlayerCount + " players...");
                        testDataGenerator.generate(testDataPlayerCount);
                        uiPersistenceService.saveAllWithProgress(updater);
                    }

                    // 2. Check for updates
                    try {
                        GitHubUpdateChecker.UpdateInfo updateInfo = uiGithubTaskService.checkForUpdate(updater);
                        if (updateInfo.isNewVersionAvailable() && updateInfo.assetDownloadUrl() != null) {
                            File newJarFile = uiGithubTaskService.downloadUpdate(updateInfo, updater);
                            return new StartupResult(StartupResult.Status.UPDATE_READY, newJarFile);
                        }
                    } catch (Exception e) {
                        System.err.println("Update process failed, continuing startup: " + e.getMessage());
                        // Don't halt startup for a failed update check.
                    }

                    // 3. Check for an existing session
                    updater.updateStatus("Checking for existing session...");
                    if (GoogleAuthManager.hasStoredCredentials()) {
                        uiGoogleTaskService.connectWithStoredCredentials(updater);
                        uiPersistenceService.findAllWithProgress(updater);
                        return new StartupResult(StartupResult.Status.LOGIN_SUCCESSFUL);
                    }

                    // 4. If no session, prepare to show the login UI
                    return new StartupResult(StartupResult.Status.SHOW_LOGIN_UI);
                },
                this::handleStartupResult,
                this::handleStartupError
        );
    }

    /**
     * Called when a user clicks the "Sign In" button.
     */
    private void handleUserLogin() {
        uiTaskExecutor.execute(
                primaryStage, "Connecting...", "Successfully connected!",
                (updater) -> {
                    uiGoogleTaskService.connectToGoogle(updater);
                    uiPersistenceService.findAllWithProgress(updater);
                    return "unused";
                },
                (result) -> showManagementStage(), // On success, show the main app
                this::handleStartupError // Reuse the same error handler
        );
    }

    /**
     * Handles the result of the startup sequence and updates the UI accordingly.
     * @param result The result from the background startup task.
     */
    private void handleStartupResult(StartupResult result) {
        switch (result.status) {
            case UPDATE_READY -> applyUpdate((File) result.data);
            case LOGIN_SUCCESSFUL -> showManagementStage();
            case SHOW_LOGIN_UI -> showLoginUI("Please sign in to continue.");
        }
    }

    /**
     * Handles errors that occur during the startup or login process.
     * @param error The throwable error that occurred.
     */
    private void handleStartupError(Throwable error) {
        // Special handling for a common port conflict issue.
        if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
            hasAttemptedBindExceptionRetry = true;
            Platform.runLater(() -> {
                GoogleAuthManager.logout();
                showLoginUI("A service is already running on the required port.\nPlease close other instances and try again.");
            });
            return;
        }

        // Generic error handling
        GoogleAuthManager.logout();
        coreProvider.createDialog(DialogType.ERROR,"An error occurred: " + error.getMessage(), root).showAndWait();
        error.printStackTrace();
        showLoginUI("Something went wrong. Please try signing in again.");
    }


    // --- Helper & Utility Methods ---

    /**
     * Checks if the application was launched with the '--h2' flag.
     */
    private boolean isH2Mode() {
        return getParameters().getRaw().stream().anyMatch("--h2"::equalsIgnoreCase);
    }

    /**
     * If in H2 mode, prompts the user to generate test data.
     * @return An Optional containing the number of players to create, or empty if none.
     */
    private Optional<Integer> askForTestDataGenerationIfInH2Mode() {
        if (!isH2Mode()) {
            return Optional.empty();
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "Would you like to populate the database with dummy data?", ButtonType.YES, ButtonType.NO);
        confirmDialog.setTitle("Test Data");
        confirmDialog.setHeaderText("H2 Test Mode Detected");

        if (confirmDialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            TextInputDialog countDialog = new TextInputDialog("20");
            countDialog.setTitle("Generate Test Data");
            countDialog.setHeaderText("How many players should we create?");
            countDialog.setContentText("Enter a number:");
            return countDialog.showAndWait().map(countStr -> {
                try {
                    return Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    return 0; // Default to 0 if input is invalid
                }
            }).filter(count -> count > 0);
        }
        return Optional.empty();
    }

    /**
     * Applies a downloaded update and restarts the application.
     * @param newJarFile The newly downloaded application JAR file.
     */
    private void applyUpdate(File newJarFile) {
        uiGithubTaskService.applyUpdateAndRestart(
                newJarFile,
                () -> coreProvider.createDialog(DialogType.ERROR, "Cannot update while running in an IDE, darling!", root).showAndWait(),
                (error) -> coreProvider.createDialog(DialogType.ERROR, "Update failed: " + error.getMessage(), root).showAndWait()
        );
    }

    /**
     * Traverses the cause chain of an exception to find a BindException.
     * This is useful for detecting port conflicts.
     */
    private boolean isRootCauseBindException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof BindException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

