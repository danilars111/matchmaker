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
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.interfaces.store.SettingStoreProvider;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;
import org.poolen.web.google.SheetsServiceManager.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final UiTaskExecutor uiTaskExecutor;
    private Button importButton;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private VBox root;
    private String sheetId;
    private String garnetSheet;
    private String amberSheet;
    private String aventurineSheet;
    private String opalSheet;
    private Consumer<List<PlayerData>> onDataImported;

    public SheetsTab(CoreProvider coreProvider,
                     GoogleAuthManager authManager,
                     SettingStoreProvider settingsStoreProvider,
                     SheetsServiceManager sheetsServiceManager,
                     UiTaskExecutor uiTaskExecutor) {
        super("Sheets");

        this.coreProvider = coreProvider;
        this.authManager = authManager;
        this.sheetsServiceManager = sheetsServiceManager;
        this.settingsStore = settingsStoreProvider.getSettingsStore();
        this.uiTaskExecutor = uiTaskExecutor;

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
        importButton = new Button("Sync with Google Sheets");
        importButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4285F4; -fx-text-fill: white;");
        importButton.setPrefWidth(300);
        setText("Sheets");

        statusLabel = new Label("Checking sign-in status...");
        progressIndicator = new ProgressIndicator();
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
                    loadSheetSettings();
                    importButton.setDisable(false);
                } else {
                    statusLabel.setText("Please sign in via the 'Auth' tab to enable Sheets import.");
                }
            }

            @Override
            protected void failed() {
                logger.error("Failed to check for stored credentials.", getException());
                progressIndicator.setVisible(false);
                statusLabel.setText("Status: Error checking credentials. Tab disabled.");
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

        final Map<String, House> sheetsToImport = Map.of(
                garnetSheet, House.GARNET,
                amberSheet, House.AMBER,
                aventurineSheet, House.AVENTURINE,
                opalSheet, House.OPAL
        );

        uiTaskExecutor.execute(
                getTabPane().getScene().getWindow(), // owner
                "Importing data from all tabs...", // initialMessage
                null, // We'll provide a custom success message in the callback
                (updater) -> { // work (ProgressAwareTask<T>)
                    List<PlayerData> allData = new ArrayList<>();
                    for (Map.Entry<String, House> entry : sheetsToImport.entrySet()) {
                        String sheetName = entry.getKey();
                        House house = entry.getValue();

                        if (sheetName != null && !sheetName.isEmpty()) {
                            updater.updateStatus("Importing from: " + sheetName + "...");
                            logger.info("Importing data from tab: {}", sheetName);
                            allData.addAll(sheetsServiceManager.importPlayerData(sheetId, sheetName, house));
                        } else {
                            logger.warn("A sheet name was null or empty, skipping.");
                        }
                    }
                    return allData;
                },
                (resultData) -> { // onSuccess (Consumer<T>)
                    String message = String.format("Successfully imported %d player/character entries.", resultData.size());
                    logger.info("Background import task completed successfully. {}", message);

                    // We just go straight to the matcher!
                    if (onDataImported != null) {
                        onDataImported.accept(resultData);
                    }
                    statusLabel.setText("Import complete. Ready.");
                },
                (error) -> { // onError (Consumer<Throwable>)
                    logger.error("Background import task failed.", error);
                    Platform.runLater(() -> coreProvider.createDialog(DialogType.ERROR, "Import failed: ".formatted(error.getMessage()), SheetsTab.this.getTabPane()).showAndWait());
                    statusLabel.setText("Import failed. Ready.");
                }
        );
    }
}
