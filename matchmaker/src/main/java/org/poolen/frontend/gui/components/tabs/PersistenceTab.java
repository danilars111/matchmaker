package org.poolen.frontend.gui.components.tabs;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.web.google.GoogleAuthManager;

/**
 * A tab dedicated to handling data persistence via Google Sheets.
 */
public class PersistenceTab extends Tab {

    private Button signInButton;
    private Button saveButton;
    private Button loadButton;
    private Button logoutButton;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private StackPane buttonContainer; // To swap between sign-in and other buttons
    private Runnable onDataChanged;
    private Runnable onLogoutRequestHandler;
    private final UiPersistenceService uiPersistenceService;
    private final CoreProvider coreProvider;

    public PersistenceTab(CoreProvider coreProvider, UiPersistenceService uiPersistenceService) {
        super("Persistence");

        this.uiPersistenceService = uiPersistenceService;
        this.coreProvider = coreProvider;

        // --- UI Components ---
        signInButton = createGoogleSignInButton();
        saveButton = new Button("Save Data");
        loadButton = new Button("Load Data");
        logoutButton = new Button("Logout");
        statusLabel = new Label("Checking sign-in status...");
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        buttonContainer = new StackPane();

        // --- Layout ---
        VBox root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);

        HBox statusBox = new HBox(10, statusLabel, progressIndicator);
        statusBox.setAlignment(Pos.CENTER);

        buttonContainer.getChildren().addAll(signInButton, createActionButtonsBox());

        root.getChildren().addAll(statusBox, buttonContainer);
        this.setContent(root);

        // --- Event Wiring ---
        signInButton.setOnAction(e -> handleSignIn());
        saveButton.setOnAction(e -> handleSave());
        loadButton.setOnAction(e -> handleLoad());
        logoutButton.setOnAction(e -> {
            if (onLogoutRequestHandler != null) {
                onLogoutRequestHandler.run();
            }
        });

        // --- Initial State ---
        // We add this listener so that when the user switches to this tab,
        // it re-checks the login status in case it has changed.
        this.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                updateUiState();
            }
        });

        // Initial check when the tab is first created.
        updateUiState();
    }
    public void init(Runnable onPlayerListChanged) {
        setOnDataChanged(onPlayerListChanged);
    }

    /**
     * Creates the VBox containing the save, load, and logout buttons, stacked vertically.
     */
    private VBox createActionButtonsBox() {
        saveButton.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        loadButton.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        logoutButton.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");

        // Make buttons take up the full width for a cleaner stacked look
        saveButton.setMaxWidth(Double.MAX_VALUE);
        loadButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setMaxWidth(Double.MAX_VALUE);

        VBox actionButtonsBox = new VBox(10, saveButton, loadButton, logoutButton);
        actionButtonsBox.setAlignment(Pos.CENTER);
        return actionButtonsBox;
    }

    /**
     * Checks the Google sign-in status and updates the UI accordingly.
     */
    private void updateUiState() {
        progressIndicator.setVisible(true);
        statusLabel.setText("Checking sign-in status...");

        Task<Boolean> checkTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return GoogleAuthManager.hasStoredCredentials();
            }

            @Override
            protected void succeeded() {
                boolean isSignedIn = getValue();
                statusLabel.setText(isSignedIn ? "Status: Signed In" : "Status: Signed Out");
                signInButton.setVisible(!isSignedIn);
                buttonContainer.getChildren().get(1).setVisible(isSignedIn); // Show the action button box
                progressIndicator.setVisible(false);
            }

            @Override
            protected void failed() {
                statusLabel.setText("Status: Error checking credentials.");
                signInButton.setVisible(true);
                buttonContainer.getChildren().get(1).setVisible(false);
                progressIndicator.setVisible(false);
            }
        };
        new Thread(checkTask).start();
    }


    private void handleSignIn() {
        runTask(() -> {
            //SheetsServiceManager.connect();
            return "Successfully signed in to Google!";
        }, "Signing in...");
    }

    private void handleSave() {
        uiPersistenceService.saveAll(getTabPane().getScene().getWindow(), onDataChanged);
    }

    private void handleLoad() {
        uiPersistenceService.findAll(getTabPane().getScene().getWindow(), onDataChanged);
    }

    private void runTask(TaskOperation operation, String statusMessage) {
        progressIndicator.setVisible(true);
        statusLabel.setText(statusMessage);
        buttonContainer.setDisable(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return operation.execute();
            }

            @Override
            protected void succeeded() {
                coreProvider.createDialog(DialogType.INFO, getValue(), PersistenceTab.this.getTabPane()).showAndWait();
                onDataChanged.run(); // Notify the app of potential changes
                updateUiState(); // Re-check and update the button visibility
                buttonContainer.setDisable(false);
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                coreProvider.createDialog(DialogType.ERROR, "Operation failed: " + error.getMessage(), PersistenceTab.this.getTabPane()).showAndWait();
                error.printStackTrace();
                updateUiState(); // Still update UI on failure
                buttonContainer.setDisable(false);
            }
        };

        new Thread(task).start();
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

    public void setOnLogoutRequestHandler(Runnable handler) {
        this.onLogoutRequestHandler = handler;
    }

    @FunctionalInterface
    private interface TaskOperation {
        String execute() throws Exception;
    }

    public Runnable getOnLogoutRequestHandler() {
        return onLogoutRequestHandler;
    }

    private void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }
}

