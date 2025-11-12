package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.poolen.MatchmakerApplication;
import org.poolen.frontend.exceptions.GlobalExceptionHandler;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.gui.components.stages.email.AccessRequestStage;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.ApplicationScriptService;
import org.poolen.frontend.util.services.ComponentFactoryService;
import org.poolen.frontend.util.services.StartupService;
import org.poolen.frontend.util.services.StartupService.StartupResult;
import org.poolen.frontend.util.services.UiGithubTaskService;
import org.poolen.frontend.util.services.UiGoogleTaskService;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.util.PropertiesManager;
import org.poolen.util.SpringManager;
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.GoogleAuthManager.AuthorizationTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.net.BindException;
import java.util.Optional;


/**
 * The main entry point for the JavaFX GUI.
 * This refactored version delegates the startup sequence to a dedicated StartupService,
 * keeping this class focused on UI setup and user interaction.
 *
 * Now with a lovely splash screen!
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
    private GoogleAuthManager authManager;

    // --- UI Components ---
    private Stage primaryStage;
    private Scene loginScene; // We'll prepare this scene in the background
    private VBox loginRoot; // The root pane for the login scene

    // --- Splash Screen UI ---
    private VBox splashRoot;
    private Label splashStatusLabel;
    private ProgressIndicator splashLoadingIndicator;

    // --- Login Scene UI (we need to access these from multiple methods) ---
    private Label statusLabel;
    private Button signInButton;
    private Label continueOfflineLabel;
    private Button requestAccessButton;
    private Button exitButton;
    private ProgressIndicator loadingIndicator;


    /**
     * NO LONGER NEEDED!
     * The Spring context is now initialised *once* in ApplicationLauncher.
     * We will fetch it in the start() method.
     */
    @Override
    public void init() {
        // This is intentionally left blank.
        // We do not want to initialise Spring here anymore.
        logger.info("Application.init() called... skipping Spring initialisation as it's handled by ApplicationLauncher.");
    }

    /**
     * The main entry point for the JavaFX application.
     * It sets up the stage, performs pre-flight checks, and kicks off the startup sequence.
     */
    @Override
    public void start(Stage primaryStage) {
        logger.info("JavaFX start phase initiated.");

        // --- STEP 1: Set up the stage and show the splash screen IMMEDIATELY ---
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Rollspelspoolen Matchmaker");

        logger.info("Creating and showing splash screen...");
        createAndShowSplashScene(); // Shows the *splash* scene immediately

        // --- STEP 2: Start the Spring Boot-up in a background thread ---
        logger.info("Starting Spring Boot context in a background thread...");
        startSpringContextTask();
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
     * Creates and shows the *Splash Screen* scene.
     * This is the first thing the user sees.
     */
    private void createAndShowSplashScene() {
        primaryStage.setResizable(false);

        splashRoot = new VBox(20);
        splashRoot.setAlignment(Pos.CENTER);
        splashRoot.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        // Let's add a lovely image!
        // Make sure you have a "splash.png" in your /images/ folder.
        // If not, this will just show the text and spinner.
        /* We are skipping the image for now, just as you asked!
        try {
            Image splashImage = new Image(getClass().getResourceAsStream("/images/splash.png"));
            ImageView splashImageView = new ImageView(splashImage);
            splashImageView.setFitHeight(150);
            splashImageView.setPreserveRatio(true);
            splashRoot.getChildren().add(splashImageView);
        } catch (Exception e) {
            logger.warn("Could not load /images/splash.png. Splash screen will show text and spinner only.");
        }
        */

        splashStatusLabel = new Label("Starting up...");
        splashStatusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");
        splashStatusLabel.setTextAlignment(TextAlignment.CENTER);

        splashLoadingIndicator = new ProgressIndicator();
        splashLoadingIndicator.setPrefSize(50, 50);

        splashRoot.getChildren().addAll(splashStatusLabel, splashLoadingIndicator);

        Scene scene = new Scene(splashRoot, 450, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
        logger.debug("Splash screen is now visible.");
    }


    /**
     * Creates and configures all the UI components for the *Login Scene*.
     * This is NOT shown immediately, but prepared in memory.
     */
    private void initialiseLoginScene() {
        loginRoot = new VBox(20);
        loginRoot.setAlignment(Pos.CENTER);
        loginRoot.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        statusLabel = new Label("Starting up..."); // This will be updated later
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");
        statusLabel.setTextAlignment(TextAlignment.CENTER);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);

        signInButton = createGoogleSignInButton();
        signInButton.setVisible(false);
        signInButton.setOnAction(e -> handleUserLogin());

        requestAccessButton = new Button("Request Access");
        requestAccessButton.setVisible(false); // We hide this until we know we need it
        requestAccessButton.setOnAction(e -> handleRequestAccess());

        continueOfflineLabel = new Label("Continue without signing in");
        continueOfflineLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0000EE; -fx-underline: true;");
        continueOfflineLabel.setCursor(Cursor.HAND);
        continueOfflineLabel.setOnMouseClicked(e ->  showManagementStage());
        continueOfflineLabel.setVisible(false);

        exitButton = new Button("Exit");
        exitButton.setVisible(true); // Always visible!
        exitButton.setOnAction(e -> Platform.exit());

        HBox buttonRow = new HBox(10, requestAccessButton, exitButton);
        buttonRow.setAlignment(Pos.CENTER);

        VBox.setMargin(continueOfflineLabel, new Insets(-10, 0, 0, 0));
        loginRoot.getChildren().addAll(statusLabel, loadingIndicator, signInButton, continueOfflineLabel, buttonRow);

        // Create the scene and store it in our field
        loginScene = new Scene(loginRoot, 450, 300);
        logger.debug("Login scene has been created and is ready.");
    }

    /**
     * Updates the UI to show the login prompt.
     * This now *swaps the scene* from splash to login.
     *
     * @param message The message to display to the user.
     */
    private void showLoginUI(String message) {
        logger.info("Switching from splash screen to login scene.");

        // --- THIS IS THE MAGIC ---
        // We now set the scene to the one we prepared earlier.
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("D&D Matchmaker Deluxe - Sign In");

        logger.info("Updating UI to show login options with message: '{}'", message);
        statusLabel.setText(message);
        loadingIndicator.setVisible(false);
        signInButton.setVisible(true);
        continueOfflineLabel.setVisible(true);
        requestAccessButton.setVisible(true);
        exitButton.setVisible(true);
    }

    /**
     * Closes the login window and opens the main application management window.
     */
    private void showManagementStage() {
        uiTaskExecutor.execute(
                primaryStage, "Connecting...", "Successfully connected!",
                (updater) -> {
                    logger.debug("Fetching data from DB.");
                    uiPersistenceService.findAllWithProgress(updater);
                    return "unused";
                },
                (result) -> {
                    logger.info("Data fetch successful.");
                    logger.info("Login successful or continuing offline. Closing login stage and showing ManagementStage.");
                    primaryStage.close();
                    ManagementStage managementStage = springContext.getBean(ComponentFactoryService.class).getManagementStage();
                    managementStage.show();
                },
                this::handleStartupError // Reuse the same error handler
        );



    }

    /**
     * This is our new handler, just for that button!
     */
    private void handleRequestAccess() {
        logger.info("User clicked 'Request Access'. Opening AccessRequestStage.");
        AccessRequestStage accessStage = new AccessRequestStage(springContext);
        accessStage.show();
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
     * Creates and starts a background Task to initialise the Spring Context.
     * This is the heavy lifting, done *after* the splash screen is visible.
     */
    private void startSpringContextTask() {
        // This task will run on a background thread
        Task<ConfigurableApplicationContext> springTask = new Task<>() {
            @Override
            protected ConfigurableApplicationContext call() throws Exception {
                updateMessage("Starting application core...");
                logger.debug("Background task started: Initialising Spring context.");
                try {
                    return new SpringApplicationBuilder(MatchmakerApplication.class)
                            .properties(SpringManager.getConfigLocation())
                            .run(getParameters().getRaw().toArray(new String[0]));
                } catch (Exception e) {
                    logger.error("A critical error occurred during Spring context initialisation.", e);
                    throw e; // Re-throw to trigger the onFailed handler
                }
            }
        };

        // Bind the splash screen's label to the task's message property
        splashStatusLabel.textProperty().bind(springTask.messageProperty());
        splashLoadingIndicator.progressProperty().bind(springTask.progressProperty()); // Why not!

        // --- What to do when Spring successfully boots up ---
        springTask.setOnSucceeded(e -> {
            logger.info("Spring context successfully initialised in background.");
            splashStatusLabel.textProperty().unbind(); // We're done with this binding
            splashLoadingIndicator.progressProperty().unbind();

            this.springContext = springTask.getValue(); // Get our new context!

            // --- STEP 3: Initialise services (now that we have the context) ---
            try {
                logger.debug("Spring context found. Initialising services...");
                // Now we can set up our lovely global nanny
                GlobalExceptionHandler.setInstance(springContext);
                Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler.getInstance());

                initialiseServices();
                logger.info("All essential services have been initialised.");
            } catch (Exception ex) {
                logger.error("A critical error occurred while initialising services from Spring context.", ex);
                this.springStartupError = ex;
                // This error will be handled by the isFirstTimeSetup() check below.
            }

            // --- STEP 4: Perform pre-flight checks ---
            logger.debug("Performing first-time setup checks...");
            if (isFirstTimeSetup()) {
                logger.info("First-time setup detected. Aborting normal startup to show SetupStage.");
                // The SetupStage will be shown, so we stop here.
                return;
            }

            // --- STEP 5: Prepare the UIs and Start Background Tasks ---
            logger.info("Initialising main login scene (in memory)...");
            initialiseLoginScene(); // Prepares the login scene

            logger.debug("Checking for H2 mode to determine if test data generation is needed.");
            Optional<Integer> testDataCount = askForTestDataGenerationIfInH2Mode();

            logger.info("Beginning main startup sequence in a background thread.");
            beginStartupSequence(testDataCount.orElse(0));
        });

        // --- What to do if Spring boot-up fails horribly ---
        springTask.setOnFailed(e -> {
            logger.error("FATAL: Spring context failed to start.", springTask.getException());
            splashStatusLabel.textProperty().unbind();
            splashLoadingIndicator.progressProperty().unbind();
            splashStatusLabel.setText("Error: Application failed to start.");

            this.springStartupError = (Exception) springTask.getException();

            // This will now see the error and launch the SetupStage
            isFirstTimeSetup();
        });

        // And... action!
        new Thread(springTask).start();
    }

    /**
     * Performs initial checks before the main UI is shown. If a check fails
     * and a different stage is shown (like the SetupStage), this method returns true.
     *
     * @return true if the startup process should be aborted, false otherwise.
     */
    private boolean isFirstTimeSetup() {
        // Check for missing properties *before* checking for Spring errors
        if (!isH2Mode() && !PropertiesManager.propertiesFileExists()) {
            logger.warn("Application properties are missing. Launching SetupStage for first-time configuration.");
            new SetupStage(new ApplicationScriptService(), null).show();
            return true;
        }

        // Now check for Spring errors (which we might have from start())
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
     * This is now called from start() AFTER the context is retrieved.
     */
    private void initialiseServices() {
        // This method is now called *after* this.springContext is set.
        uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);
        uiPersistenceService = springContext.getBean(UiPersistenceService.class);
        uiGoogleTaskService = springContext.getBean(UiGoogleTaskService.class);
        uiGithubTaskService = springContext.getBean(UiGithubTaskService.class);
        coreProvider = springContext.getBean(ComponentFactoryService.class);
        startupService = springContext.getBean(StartupService.class);
        authManager = springContext.getBean(GoogleAuthManager.class);
    }

    /**
     * Executes the main startup sequence in a background thread by delegating to the StartupService.
     *
     * @param testDataPlayerCount The number of test players to generate (0 if none).
     */
    private void beginStartupSequence(int testDataPlayerCount) {
        logger.info("Executing startup sequence via new Task. Test data players to generate: {}", testDataPlayerCount);

        // --- This is our new, correct way! ---
        // We create a simple Task, just like for the Spring context.
        // The UiTaskExecutor is NOT used here, because it's for overlays.
        Task<StartupResult> startupTask = new Task<>() {
            @Override
            protected StartupResult call() throws Exception {
                logger.debug("Background task started: Performing startup sequence.");

                // --- This is our lovely new adapter! ---
                // We create a UiUpdater that pipes its status to our task's message
                final UiUpdater splashUpdater = new UiUpdater() {
                    @Override
                    public void updateStatus(String message) {
                        // 'this' refers to the Task, so we can call its updateMessage
                        updateMessage(message);
                    }

                    @Override
                    public void showDetails(String label, String details, Runnable onCancelAction) {
                        // The splash screen can't show details, so we'll just update the
                        // status label with the 'label' text. It's the best we can do!
                        updateMessage(label);
                        logger.warn("SplashUpdater: showDetails called (label: {}), but splash screen cannot show details. Updating status instead.", label);

                        // We can't block, but if there's a cancel action, we should probably
                        // log that we're ignoring it, or just... ignore it.
                        if (onCancelAction != null) {
                            logger.warn("SplashUpdater: Ignoring onCancelAction.");
                        }
                    }
                };

                // Now we call the service with our new, correct updater!
                return startupService.performStartupSequence(testDataPlayerCount, splashUpdater);
            }
        };

        // Bind the splash screen's label to this new task's message
        splashStatusLabel.textProperty().bind(startupTask.messageProperty());
        splashLoadingIndicator.progressProperty().bind(startupTask.progressProperty());

        // --- What to do when the startup sequence succeeds ---
        startupTask.setOnSucceeded(e -> {
            logger.info("Startup sequence task completed successfully.");
            splashStatusLabel.textProperty().unbind();
            splashLoadingIndicator.progressProperty().unbind();
            handleStartupResult(startupTask.getValue()); // Pass the result to our handler
        });

        // --- What to do if the startup sequence fails ---
        startupTask.setOnFailed(e -> {
            logger.error("Startup sequence task failed.", startupTask.getException());
            splashStatusLabel.textProperty().unbind();
            splashLoadingIndicator.progressProperty().unbind();
            handleStartupError(startupTask.getException()); // Pass the error to our handler
        });

        // And... action!
        new Thread(startupTask).start();
    }

    /**
     * Called when a user clicks the "Sign In" button.
     */
    private void handleUserLogin() {
        logger.info("User initiated Google Sign-In process.");

        // --- I'm removing all my silly changes! ---
        // We don't need to hide the buttons or show the spinner here,
        // because the UiTaskExecutor handles all of that with its overlay!

        // NOW we can use our lovely UiTaskExecutor, because the login scene is visible!
        uiTaskExecutor.execute(
                primaryStage, "Connecting...", "Successfully connected!",
                (updater) -> {
                    logger.debug("Executing Google connection and data fetch tasks.");
                    updater.updateStatus("Connecting to Google...");
                    uiGoogleTaskService.connectToGoogle(updater);
                    return "unused";
                },
                (result) -> {
                    logger.info("Google sign-in successful.");
                    showManagementStage();
                },
                (error) -> {
                    // If login fails, we need to show the login UI again
                    handleStartupError(error);
                }
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
                // We need to switch to the login scene to show the dialog properly
                primaryStage.setScene(loginScene);
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
            // We just return; the app will likely hang, but this is a rare dev-time issue.
            // Or, we could show the login UI with an error.
            Platform.runLater(() -> showLoginUI("Error: Application port is already in use."));
            return;
        }

        if (isRootCauseTimeoutException(error)) {
            logger.warn("Authorization timed out, likely a 403. Showing 'Request Access' button.");
            // We must call showLoginUI *first* to swap the scene!
            Platform.runLater(() -> {
                showLoginUI("Login timed out.\nThis may be because you are not an approved user.");
                // showLoginUI already makes the button visible, but we can be extra sure.
                requestAccessButton.setVisible(true);
            });
            return;
        }

        // Generic error handler
        // We can't use the UiTaskExecutor here if the context is null
        // But by this point, the context *should* be available
        // Let's check, just in case
        if (uiGoogleTaskService == null) {
            logger.error("Cannot perform cleanup, services are null. Likely a Spring boot failure.");
            Platform.runLater(() -> {
                showLoginUI("A critical error occurred. Please try again.");
            });
            return;
        }

        uiTaskExecutor.execute(
                primaryStage, "Disconnecting...", "Successfully disconnected!",
                (updater) -> {
                    logger.debug("Performing generic error handling: disconnecting user.");
                    uiGoogleTaskService.disconnectFromGoogle(updater);
                    return "unused";
                },
                (result) -> {
                    logger.info("Disconnection successful.");
                    // Show the login scene *before* showing the dialog
                    Platform.runLater(() -> {
                        showLoginUI("Something went wrong. Please try signing in again.");
                        coreProvider.createDialog(DialogType.ERROR,"An error occurred: " + error.getMessage(), loginRoot).showAndWait();
                    });
                }
        );
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
        // We need to show this dialog *over* the splash screen.
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "Would you like to populate the database with dummy data?", ButtonType.YES, ButtonType.NO);
        confirmDialog.initOwner(primaryStage); // Ensures it's on top
        confirmDialog.setTitle("Test Data");
        confirmDialog.setHeaderText("H2 Test Mode Detected");

        if (confirmDialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            logger.debug("User chose to generate test data.");
            TextInputDialog countDialog = new TextInputDialog("20");
            countDialog.initOwner(primaryStage); // Ensures it's on top
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
                    coreProvider.createDialog(DialogType.ERROR, "Cannot update while running in an IDE, darling!", loginRoot).showAndWait();
                },
                (error) -> {
                    logger.error("Failed to apply update.", error);
                    coreProvider.createDialog(DialogType.ERROR, "Update failed: " + error.getMessage(), loginRoot).showAndWait();
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

    /**
     * Traverses the cause chain of an exception to find our new TimeoutException.
     * This is our trigger to show the "Request Access" button!
     */
    private boolean isRootCauseTimeoutException(Throwable throwable) {
        logger.debug("Checking exception chain for a root cause of AuthorizationTimeoutException.");
        Throwable cause = throwable;
        int depth = 0;
        while (cause != null) {
            if (cause instanceof AuthorizationTimeoutException) {
                logger.warn("Found AuthorizationTimeoutException at depth {} in the cause chain.", depth);
                return true;
            }
            cause = cause.getCause();
            depth++;
        }
        logger.debug("No AuthorizationTimeoutException found in the exception chain.");
        return false;
    }
}
