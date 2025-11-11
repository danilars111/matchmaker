package org.poolen.frontend.gui.components.tabs;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.Settings;
// This is the correct one you pointed out, my clever girl!
import org.poolen.backend.db.interfaces.store.SettingStoreProvider;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
// I've removed the naughty, conflicting import!
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;
import org.poolen.web.google.SheetsServiceManager.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A tab dedicated to handling data import from Google Sheets.
 * This tab will be disabled entirely if Google authentication is not valid.
 */
public class SheetsTab extends Tab {

    private static final Logger logger = LoggerFactory.getLogger(SheetsTab.class);

    private final CoreProvider coreProvider;
    private final GoogleAuthManager authManager;
    private final SettingsStore settingsStore;
    private final SheetsServiceManager sheetsServiceManager;
    private final UiTaskExecutor uiTaskExecutor; // Our shiny new service!

    private Button importButton;
    private Label statusLabel;
    private ProgressIndicator progressIndicator; // We still need this for the little auth check
    private VBox root;

    // Settings
    private String sheetId;
    private String garnetSheet;
    private String amberSheet;
    private String aventurineSheet;
    private String opalSheet;

    // Callback for when data is successfully imported
    private Consumer<List<PlayerData>> onDataImported;

    public SheetsTab(CoreProvider coreProvider,
                     GoogleAuthManager authManager,
                     SettingStoreProvider settingsStoreProvider, // And fixed in the constructor!
                     SheetsServiceManager sheetsServiceManager,
                     UiTaskExecutor uiTaskExecutor) {
        super("Sheets");

        this.coreProvider = coreProvider;
        this.authManager = authManager;
        this.sheetsServiceManager = sheetsServiceManager;
        this.settingsStore = settingsStoreProvider.getSettingsStore();
        this.uiTaskExecutor = uiTaskExecutor; // And stored!

        buildUi();
        wireEvents();

        // Add a listener to re-check auth every time the tab is selected
        this.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                logger.trace("Sheets tab selected. Checking auth status.");
                checkAuthAndSetDisabled();
            }
        });

        // Initial check
        checkAuthAndSetDisabled();
        logger.info("SheetsTab initialised.");
    }

    /**
     * Initialises the tab with a callback for imported data.
     * @param onDataImported A consumer that will accept the list of imported PlayerData.
     */
    public void init(Consumer<List<PlayerData>> onDataImported) {
        this.onDataImported = onDataImported;
    }

    /**
     * Builds the basic UI components for the tab.
     */
    private void buildUi() {
        importButton = new Button("Import All Data from Sheets");
        importButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4285F4; -fx-text-fill: white;");
        importButton.setPrefWidth(300);

        statusLabel = new Label("Checking sign-in status...");
        progressIndicator = new ProgressIndicator(); // We keep this for the inline auth check
        progressIndicator.setVisible(false);

        HBox statusBox = new HBox(10, statusLabel, progressIndicator);
        statusBox.setAlignment(Pos.CENTER);

        root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);
        root.getChildren().addAll(statusBox, importButton);

        this.setContent(root);
    }

    /**
     * Wires up the event handlers for UI components.
     */
    private void wireEvents() {
        importButton.setOnAction(e -> handleImport());
    }

    /**
     * Checks Google sign-in status in a background thread and updates the UI,
     * disabling the entire tab if not authenticated.
     */
    public void checkAuthAndSetDisabled() {
        logger.debug("Updating UI state based on sign-in status.");
        progressIndicator.setVisible(true);
        statusLabel.setText("Checking sign-in status...");
        importButton.setDisable(true); // Disable button while checking

        Task<Boolean> checkTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                logger.trace("Background task started to check for stored credentials.");
                return authManager.loadAndValidateStoredCredential() != null;
            }

            @Override
            protected void succeeded() {
                progressIndicator.setVisible(false);
                boolean isSignedIn = getValue();
                logger.debug("Credential check successful. Signed in: {}", isSignedIn);

                setDisable(!isSignedIn);

                if (isSignedIn) {
                    statusLabel.setText("Status: Signed In. Ready to import.");
                    setText("Sheets");
                    loadSheetSettings();
                    importButton.setDisable(false);
                } else {
                    statusLabel.setText("Please sign in via the 'Auth' tab to enable Sheets import.");
                    setText("Sheets (Signed Out)");
                }
            }

            @Override
            protected void failed() {
                logger.error("Failed to check for stored credentials.", getException());
                progressIndicator.setVisible(false);
                statusLabel.setText("Status: Error checking credentials. Tab disabled.");
                setText("Sheets (Error)");
                setDisable(true);
            }
        };
        new Thread(checkTask).start();
    }

    /**
     * Loads the required sheet names from the settings store.
     */
    private void loadSheetSettings() {
        try {
            sheetId = (String) settingsStore.getSetting(Settings.PersistenceSettings.SHEETS_ID).getSettingValue();
            garnetSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.GARNET_SHEET_NAME).getSettingValue();
            amberSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.AMBER_SHEET_NAME).getSettingValue();
            aventurineSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.AVENTURINE_SHEET_NAME).getSettingValue();
            opalSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.OPAL_SHEET_NAME).getSettingValue();
            logger.info("Successfully loaded all sheet names from settings.");
        } catch (Exception e) {
            logger.error("Failed to load sheet settings!", e);
            coreProvider.createDialog(DialogType.ERROR, "Failed to load sheet settings: " + e.getMessage(), getTabPane()).showAndWait();
            importButton.setDisable(true);
            statusLabel.setText("Error: Could not load sheet names from settings.");
        }
    }

    /**
     * Handles the "Import All Data" button click.
     * Runs the import operation using the UiTaskExecutor.
     */
    private void handleImport() {
        logger.info("User initiated 'Import All Data' action.");
        if (sheetId == null || sheetId.isEmpty()) {
            coreProvider.createDialog(DialogType.ERROR, "Sheets ID is not set. Cannot import.", getTabPane()).showAndWait();
            return;
        }

        final List<String> sheetNames = List.of(garnetSheet, amberSheet, aventurineSheet, opalSheet);

        // We use our new UiTaskExecutor! So much cleaner!
        uiTaskExecutor.execute(
                getTabPane().getScene().getWindow(), // owner
                "Importing data from all tabs...", // initialMessage
                null, // We'll provide a custom success message in the callback
                (updater) -> { // work (ProgressAwareTask<T>)
                    List<PlayerData> allData = new ArrayList<>();
                    for (String sheetName : sheetNames) {
                        if (sheetName != null && !sheetName.isEmpty()) {
                            // We can update the overlay message!
                            updater.updateStatus("Importing from: " + sheetName + "...");
                            logger.info("Importing data from tab: {}", sheetName);
                            allData.addAll(sheetsServiceManager.importPlayerData(sheetId, sheetName));
                        } else {
                            logger.warn("A sheet name was null or empty, skipping.");
                        }
                    }
                    return allData;
                },
                (resultData) -> { // onSuccess (Consumer<T>)
                    String message = String.format("Successfully imported %d player/character entries.", resultData.size());
                    logger.info("Background import task completed successfully. {}", message);
                    // We show our own dialog with the count
                    Platform.runLater(()-> coreProvider.createDialog(DialogType.INFO, message, SheetsTab.this.getTabPane()).showAndWait());


                    if (onDataImported != null) {
                        onDataImported.accept(resultData);
                    }
                    statusLabel.setText("Import complete. Ready.");
                },
                (error) -> { // onError (Consumer<Throwable>)
                    logger.error("Background import task failed.", error);
                    coreProvider.createDialog(DialogType.ERROR, "Import failed: " + error.getMessage(), SheetsTab.this.getTabPane()).showAndWait();
                    statusLabel.setText("Import failed. Ready.");
                }
        );
    }
}
