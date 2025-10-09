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
import org.poolen.MatchmakerApplication;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.*;
import org.poolen.util.PropertiesManager;
import org.poolen.util.SpringManager;
import org.poolen.web.google.GoogleAuthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.BindException;
import java.util.Optional;
import org.poolen.frontend.util.services.StartupService.StartupResult;


/**
 * The main entry point for the JavaFX GUI.
 * This refactored version delegates the startup sequence to a dedicated StartupService,
 * keeping this class focused on UI setup and user interaction.
 */
public class LoginApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(LoginApplication.class);

    // --- State and Context ---
    private ConfigurableApplicationContext springContext;
    private Exception springStartupError = null;
    private boolean hasAttemptedBindExceptionRetry = false;

    // --- Services (Dependencies) ---
    private UiTaskExecutor uiTaskExecutor;
    private UiPersistenceService uiPersistenceService;
    private UiGoogleTaskService uiGoogleTaskService;
    private UiGithubTaskService uiGithubTaskService;
    private CoreProvider coreProvider;
    private StartupService startupService;

    // --- UI Components ---
    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button signInButton;
    private Label continueOfflineLabel;
    private Button exitButton;
    private ProgressIndicator loadingIndicator;


    /**
     * Initialises the Spring context and retrieves necessary beans.
     * This phase happens before the UI is built.
     */
    @Override
    public void init() {
        logger.info("Application initialisation phase started.");
        boolean isH2Mode = isH2Mode();
        boolean propertiesExist = PropertiesManager.propertiesFileExists();
        logger.debug("H2 mode: {}, Properties file exists: {}", isH2Mode, propertiesExist);

        if (propertiesExist || isH2Mode) {
            try {
                logger.info("Properties file found or H2 mode enabled. Initialising Spring context...");
                springContext = new SpringApplicationBuilder(MatchmakerApplication.class)
                        .properties(SpringManager.getConfigLocation())
                        .run(getParameters().getRaw().toArray(new String[0]));
                logger.info("Spring context successfully initialised.");
                initialiseServices();
            } catch (Exception e) {
                logger.error("A critical error occurred during Spring context initialisation.", e);
                springStartupError = e;
            }
        } else {
            logger.warn("No properties file found and not in H2 mode. Skipping Spring context initialisation.");
        }
    }

    /**
     * The main entry point for the JavaFX application.
     * It sets up the stage, performs pre-flight checks, and kicks off the startup sequence.
     */
    @Override
    public void start(Stage primaryStage) {
        logger.info("JavaFX start phase initiated. Setting up the primary stage.");
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe - Sign In");

        logger.debug("Performing first-time setup checks...");
        if (isFirstTimeSetup()) {
            logger.info("First-time setup detected. Aborting normal startup to show SetupStage.");
            return; // The setup stage will handle the rest.
        }

        logger.info("Initialising main login UI components.");
        initialiseUI();

        logger.debug("Checking for H2 mode to determine if test data generation is needed.");
        Optional<Integer> testDataCount = askForTestDataGenerationIfInH2Mode();

        logger.info("Beginning main startup sequence in a background thread.");
        beginStartupSequence(testDataCount.orElse(0));
    }

    /**
     * Handles the clean shutdown of the application.
     */
    @Override
    public void stop() {
        logger.info("Application shutdown sequence initiated.");
        if (springContext != null) {
            logger.debug("Closing Spring context.");
            springContext.close();
        }
        logger.info("Exiting JavaFX platform!");
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

        VBox.setMargin(continueOfflineLabel, new Insets(-10, 0, 0, 0));
        root.getChildren().addAll(statusLabel, loadingIndicator, signInButton, continueOfflineLabel, exitButton);

        Scene scene = new Scene(root, 450, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
        logger.debug("Login UI components created and scene is now visible.");
    }

    /**
     * Updates the UI to show the login prompt.
     * @param message The message to display to the user.
     */
    private void showLoginUI(String message) {
        logger.info("Updating UI to show login options with message: '{}'", message);
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
        logger.info("Login successful or continuing offline. Closing login stage and showing ManagementStage.");
        primaryStage.close();
        ManagementStage managementStage = springContext.getBean(ComponentFactoryService.class).getManagementStage();
        managementStage.show();
    }

    /**
     * Creates a custom button with the Google Sign-In graphic.
     */
    private Button createGoogleSignInButton() {
        logger.debug("Creating Google Sign-In button.");
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
            logger.warn("Application properties are missing. Launching SetupStage for first-time configuration.");
            new SetupStage(new ApplicationScriptService(), null).show();
            return true;
        }

        if (springStartupError != null) {
            logger.warn("A Spring initialisation error was detected. Launching SetupStage to allow user to correct settings.");
            String errorMessage = "The application couldn't start with the current settings.\n" +
                    "Please check your database connection details.";
            new SetupStage(new ApplicationScriptService(), errorMessage).show();
            return true;
        }
        logger.debug("First-time setup checks passed successfully.");
        return false;
    }

    /**
     * Retrieves all required service beans from the Spring context.
     */
    private void initialiseServices() {
        logger.debug("Retrieving required service beans from the Spring context.");
        uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);
        uiPersistenceService = springContext.getBean(UiPersistenceService.class);
        uiGoogleTaskService = springContext.getBean(UiGoogleTaskService.class);
        uiGithubTaskService = springContext.getBean(UiGithubTaskService.class);
        coreProvider = springContext.getBean(ComponentFactoryService.class);
        startupService = springContext.getBean(StartupService.class);
        logger.info("All essential services have been initialised.");
    }

    /**
     * Executes the main startup sequence in a background thread by delegating to the StartupService.
     *
     * @param testDataPlayerCount The number of test players to generate (0 if none).
     */
    private void beginStartupSequence(int testDataPlayerCount) {
        logger.info("Executing startup sequence via UiTaskExecutor. Test data players to generate: {}", testDataPlayerCount);
        uiTaskExecutor.execute(
                primaryStage,
                "Starting application...",
                "Ready!",
                (updater) -> startupService.performStartupSequence(testDataPlayerCount, updater),
                this::handleStartupResult,
                this::handleStartupError
        );
    }

    /**
     * Called when a user clicks the "Sign In" button.
     */
    private void handleUserLogin() {
        logger.info("User initiated Google Sign-In process.");
        uiTaskExecutor.execute(
                primaryStage, "Connecting...", "Successfully connected!",
                (updater) -> {
                    logger.debug("Executing Google connection and data fetch tasks.");
                    uiGoogleTaskService.connectToGoogle(updater);
                    uiPersistenceService.findAllWithProgress(updater);
                    return "unused";
                },
                (result) -> {
                    logger.info("Google sign-in and data fetch successful.");
                    showManagementStage();
                },
                this::handleStartupError // Reuse the same error handler
        );
    }

    /**
     * Handles the result of the startup sequence and updates the UI accordingly.
     * @param result The result from the background startup task.
     */
    private void handleStartupResult(StartupResult result) {
        logger.info("Startup sequence completed with result status: {}", result.status);
        switch (result.status) {
            case UPDATE_READY -> {
                logger.info("An update is ready to be applied.");
                applyUpdate((File) result.data);
            }
            case LOGIN_SUCCESSFUL -> {
                logger.info("Startup successful, previous session was valid.");
                showManagementStage();
            }
            case SHOW_LOGIN_UI -> {
                logger.info("No valid session found. Showing login UI.");
                showLoginUI("Please sign in to continue.");
            }
            default -> logger.warn("Unhandled startup result status: {}", result.status);
        }
    }

    /**
     * Handles errors that occur during the startup or login process.
     * @param error The throwable error that occurred.
     */
    private void handleStartupError(Throwable error) {
        logger.error("An error occurred during the startup or login process.", error);

        if (!hasAttemptedBindExceptionRetry && isRootCauseBindException(error)) {
            hasAttemptedBindExceptionRetry = true;
            logger.warn("A BindException was detected, likely a port conflict. Attempting a graceful recovery.");
            Platform.runLater(() -> {
                GoogleAuthManager.logout();
                showLoginUI("A service is already running on the required port.\nPlease close other instances and try again.");
            });
            return;
        }

        logger.debug("Performing generic error handling: logging out user and showing an error dialog.");
        GoogleAuthManager.logout();
        coreProvider.createDialog(DialogType.ERROR,"An error occurred: " + error.getMessage(), root).showAndWait();
        showLoginUI("Something went wrong. Please try signing in again.");
    }


    // --- Helper & Utility Methods ---

    /**
     * Checks if the application was launched with the '--h2' flag.
     */
    private boolean isH2Mode() {
        boolean h2Mode = getParameters().getRaw().stream().anyMatch("--h2"::equalsIgnoreCase);
        logger.debug("Checking for H2 mode. Result: {}", h2Mode);
        return h2Mode;
    }

    /**
     * If in H2 mode, prompts the user to generate test data.
     * @return An Optional containing the number of players to create, or empty if none.
     */
    private Optional<Integer> askForTestDataGenerationIfInH2Mode() {
        if (!isH2Mode()) {
            return Optional.empty();
        }

        logger.info("H2 mode active. Prompting user for test data generation.");
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "Would you like to populate the database with dummy data?", ButtonType.YES, ButtonType.NO);
        confirmDialog.setTitle("Test Data");
        confirmDialog.setHeaderText("H2 Test Mode Detected");

        if (confirmDialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            logger.debug("User chose to generate test data.");
            TextInputDialog countDialog = new TextInputDialog("20");
            countDialog.setTitle("Generate Test Data");
            countDialog.setHeaderText("How many players should we create?");
            countDialog.setContentText("Enter a number:");
            return countDialog.showAndWait().map(countStr -> {
                try {
                    int count = Integer.parseInt(countStr);
                    logger.info("User requested to generate {} test players.", count);
                    return count;
                } catch (NumberFormatException e) {
                    logger.warn("Invalid number format '{}' entered for test data count. Defaulting to 0.", countStr);
                    return 0;
                }
            }).filter(count -> count > 0);
        }
        logger.debug("User chose not to generate test data.");
        return Optional.empty();
    }

    /**
     * Applies a downloaded update and restarts the application.
     * @param newJarFile The newly downloaded application JAR file.
     */
    private void applyUpdate(File newJarFile) {
        logger.info("Applying update from file: {}", newJarFile.getAbsolutePath());
        uiGithubTaskService.applyUpdateAndRestart(
                newJarFile,
                () -> {
                    logger.warn("Update cannot be applied in an IDE environment. Showing info dialog to user.");
                    coreProvider.createDialog(DialogType.ERROR, "Cannot update while running in an IDE, darling!", root).showAndWait();
                },
                (error) -> {
                    logger.error("Failed to apply update.", error);
                    coreProvider.createDialog(DialogType.ERROR, "Update failed: " + error.getMessage(), root).showAndWait();
                }
        );
    }

    /**
     * Traverses the cause chain of an exception to find a BindException.
     * This is useful for detecting port conflicts.
     */
    private boolean isRootCauseBindException(Throwable throwable) {
        logger.debug("Checking exception chain for a root cause of BindException.");
        Throwable cause = throwable;
        int depth = 0;
        while (cause != null) {
            if (cause instanceof BindException) {
                logger.warn("Found BindException at depth {} in the cause chain.", depth);
                return true;
            }
            cause = cause.getCause();
            depth++;
        }
        logger.debug("No BindException found in the exception chain.");
        return false;
    }
}
