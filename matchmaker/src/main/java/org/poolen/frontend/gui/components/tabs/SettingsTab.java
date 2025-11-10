package org.poolen.frontend.gui.components.tabs;

import javafx.application.Platform; // We need this, my love!
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window; // We'll need this, darling!
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISettings;
import org.poolen.backend.db.interfaces.store.SettingStoreProvider;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.stages.email.BugReportStage;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.StageProvider;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.frontend.util.services.UiGoogleTaskService; // Our new friend!
import org.poolen.frontend.util.services.UiTaskExecutor; // And this one!
import org.poolen.web.google.GoogleAuthManager; // Our new friend, darling!
import org.poolen.web.google.GoogleAuthManager.AuthorizationTimeoutException; // And this!
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A dedicated tab for viewing and editing application settings, built to dynamically
 * handle the new SettingsStore architecture.
 */
public class SettingsTab extends Tab {

    private static final Logger logger = LoggerFactory.getLogger(SettingsTab.class);

    private final SettingsStore settingsStore;
    private final Accordion accordion = new Accordion();
    private final Map<ISettings, Node> settingControls = new HashMap<>();
    private final HBox buttonBox;
    private final BorderPane root;
    private final UiPersistenceService uiPersistenceService;
    private final CoreProvider coreProvider;
    private final StageProvider stageProvider;
    // Our new services, my love!
    private final UiTaskExecutor uiTaskExecutor;
    private final UiGoogleTaskService uiGoogleTaskService;

    // Our new auth friends!
    private final GoogleAuthManager authManager;
    private final Button authButton;

    private static final List<ISettings> HIDDEN_SETTINGS = List.of(
            Settings.MatchmakerBonusSettings.BUDDY_BONUS
    );

    public SettingsTab(CoreProvider coreProvider, UiPersistenceService uiPersistenceService, SettingStoreProvider store,
                       StageProvider stageProvider, GoogleAuthManager authManager,
                       UiTaskExecutor uiTaskExecutor, UiGoogleTaskService uiGoogleTaskService) { // Added our new friends!
        super("Settings");
        this.settingsStore = store.getSettingsStore();
        this.uiPersistenceService = uiPersistenceService;
        this.coreProvider = coreProvider;
        this.stageProvider = stageProvider;
        this.authManager = authManager; // Store him!
        this.uiTaskExecutor = uiTaskExecutor; // Store this one!
        this.uiGoogleTaskService = uiGoogleTaskService; // And this one!

        // --- Main Layout ---
        root = new BorderPane();
        root.setPadding(new Insets(10));

        populateSettingsAccordion();

        ScrollPane scrollPane = new ScrollPane(accordion);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        // --- Action Buttons (Right) ---
        Button saveButton = new Button("Save Changes");
        saveButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Button cancelButton = new Button("Discard Changes");
        Button defaultsButton = new Button("Restore Defaults");
        defaultsButton.setStyle("-fx-background-color: #f0ad4e; -fx-text-fill: white;");

        buttonBox = new HBox(10, defaultsButton, cancelButton, saveButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // --- Bug Report Button (Left) ---
        Button bugReportButton = new Button("Report a Bug");
        bugReportButton.setStyle("-fx-background-color: #5bc0de; -fx-text-fill: white;"); // A nice info blue
        bugReportButton.setOnAction(e -> handleBugReport());

        // --- Our new Auth Button! ---
        authButton = new Button();
        setupAuthButton(); // Set its initial state!

        HBox leftButtonBox = new HBox(10, bugReportButton, authButton); // Add our new button!
        leftButtonBox.setAlignment(Pos.CENTER_LEFT);

        // --- Bottom Bar Container ---
        BorderPane bottomBar = new BorderPane();
        bottomBar.setLeft(leftButtonBox);
        bottomBar.setRight(buttonBox);
        bottomBar.setPadding(new Insets(10, 0, 0, 0)); // Padding on top

        root.setBottom(bottomBar);

        // --- Event Handlers ---
        saveButton.setOnAction(e -> saveSettings());
        cancelButton.setOnAction(e -> {
            logger.debug("Discard changes button clicked. Repopulating settings accordion.");
            populateSettingsAccordion();
        });
        defaultsButton.setOnAction(e -> {
            logger.debug("Restore defaults button clicked. Showing confirmation dialog.");
            coreProvider.createDialog(DialogType.CONFIRMATION,"Are you sure you want to restore all settings to their default values?", this.getTabPane())
                    .showAndWait()
                    .ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            logger.info("User confirmed restoring default settings.");
                            settingsStore.setDefaultSettings();
                            populateSettingsAccordion();
                            coreProvider.createDialog(DialogType.INFO, "All settings have been restored to their defaults.", this.getTabPane()).showAndWait();
                        } else {
                            logger.info("User cancelled restoring default settings.");
                        }
                    });
        });

        this.setContent(root);
        logger.info("SettingsTab initialised.");
    }

    private void populateSettingsAccordion() {
        logger.debug("Populating settings accordion from the settings store.");
        settingControls.clear();
        accordion.getPanes().clear();

        List<Class<? extends ISettings>> settingCategories = List.of(
                Settings.MatchmakerBonusSettings.class,
                Settings.MatchmakerMultiplierSettings.class,
                Settings.MatchmakerPrioritySettings.class,
                Settings.PersistenceSettings.class
        );

        for (Class<? extends ISettings> categoryClass : settingCategories) {
            GridPane grid = createGridPane();
            int rowIndex = 0;

            for (ISettings settingEnum : categoryClass.getEnumConstants()) {
                if (HIDDEN_SETTINGS.contains(settingEnum)) continue;

                Setting<?> setting = settingsStore.getSetting(settingEnum);
                if (setting == null) {
                    logger.warn("No setting found in store for enum: {}", settingEnum);
                    continue;
                }

                Label nameLabel = new Label(formatEnumName(setting.getName().toString()));
                nameLabel.setTooltip(new Tooltip(setting.getDescription()));

                Node valueControl = createControlForSetting(setting);
                settingControls.put(setting.getName(), valueControl);

                grid.add(nameLabel, 0, rowIndex);
                grid.add(valueControl, 1, rowIndex++);
            }

            if (grid.getRowCount() > 0) {
                String categoryName = categoryClass.getSimpleName().replace("Settings", "").replace("Setting", "");
                TitledPane titledPane = new TitledPane(categoryName, grid);
                accordion.getPanes().add(titledPane);
            }
        }

        if (!accordion.getPanes().isEmpty()) {
            accordion.setExpandedPane(accordion.getPanes().get(0));
        }
        logger.debug("Finished populating settings accordion with {} categories.", accordion.getPanes().size());
    }

    private Node createControlForSetting(Setting<?> setting) {
        Object value = setting.getSettingValue();
        logger.trace("Creating control for setting '{}' of type '{}'.", setting.getName(), value != null ? value.getClass().getSimpleName() : "null");

        if (setting.getName() instanceof Settings.MatchmakerPrioritySettings) {
            return createPriorityEditor((List<House>) value);
        } else if (value instanceof String || value == null) {
            return new TextField((String) value);
        } else if (value instanceof Double || value instanceof Integer) {
            return new TextField(String.valueOf(value));
        } else if (value instanceof Boolean) {
            CheckBox cb = new CheckBox();
            cb.setSelected((Boolean) value);
            return cb;
        }

        logger.warn("Unsupported setting type for control creation: {}", (value != null ? value.getClass().getSimpleName() : "null"));
        return new Label("Unsupported setting type: " + (value != null ? value.getClass().getSimpleName() : "null"));
    }

    private VBox createPriorityEditor(List<House> priorities) {
        VBox container = new VBox(5);
        container.setUserData(new ArrayList<>(priorities));
        rebuildPriorityEditorUI(container);
        return container;
    }

    private void rebuildPriorityEditorUI(VBox container) {
        container.getChildren().clear();
        List<House> priorities = (List<House>) container.getUserData();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        ColumnConstraints rankCol = new ColumnConstraints();
        rankCol.setMinWidth(20);
        ColumnConstraints houseCol = new ColumnConstraints();
        houseCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints buttonCol = new ColumnConstraints();
        buttonCol.setMinWidth(60);
        grid.getColumnConstraints().addAll(rankCol, houseCol, buttonCol);

        for (int i = 0; i < priorities.size(); i++) {
            House house = priorities.get(i);
            Label rankLabel = new Label((i + 1) + ".");
            Label houseLabel = new Label(house.toString());
            Button upButton = new Button("↑");
            upButton.setStyle("-fx-font-size: 8px; -fx-padding: 2 4 2 4;");
            Button downButton = new Button("↓");
            downButton.setStyle("-fx-font-size: 8px; -fx-padding: 2 4 2 4;");

            upButton.setDisable(i == 0);
            downButton.setDisable(i == priorities.size() - 1);

            final int index = i;
            upButton.setOnAction(e -> {
                logger.trace("Moving priority '{}' up from index {}.", priorities.get(index), index);
                Collections.swap(priorities, index, index - 1);
                rebuildPriorityEditorUI(container);
            });
            downButton.setOnAction(e -> {
                logger.trace("Moving priority '{}' down from index {}.", priorities.get(index), index);
                Collections.swap(priorities, index, index + 1);
                rebuildPriorityEditorUI(container);
            });

            HBox buttonBox = new HBox(5, upButton, downButton);
            buttonBox.setAlignment(Pos.CENTER_LEFT);

            grid.add(rankLabel, 0, i);
            grid.add(houseLabel, 1, i);
            grid.add(buttonBox, 2, i);
        }
        container.getChildren().add(grid);
    }

    private void saveSettings() {
        logger.info("Save changes button clicked. Attempting to update and save settings.");
        try {
            for (Map.Entry<ISettings, Node> entry : settingControls.entrySet()) {
                ISettings settingEnum = entry.getKey();
                Node control = entry.getValue();
                Object originalValue = settingsStore.getSetting(settingEnum).getSettingValue();

                Object newValue = null;
                if (control instanceof TextField tf) {
                    String textValue = tf.getText();
                    if (originalValue instanceof String || originalValue == null) newValue = textValue;
                    else if (originalValue instanceof Double) newValue = Double.parseDouble(textValue);
                    else if (originalValue instanceof Integer) newValue = Integer.parseInt(textValue);
                } else if (control instanceof CheckBox cb) {
                    newValue = cb.isSelected();
                } else if (control instanceof VBox priorityEditor) {
                    newValue = new ArrayList<>((List<House>) priorityEditor.getUserData());
                }

                if (newValue != null) {
                    logger.trace("Updating setting '{}' to new value: {}", settingEnum, newValue);
                    settingsStore.updateSetting(settingEnum, newValue);
                }
            }
        } catch (NumberFormatException e) {
            logger.error("Could not save settings due to a number format error.", e);
            coreProvider.createDialog(DialogType.ERROR,"Could not save settings. Please check your values.\nError: " + e.getMessage(), this.getTabPane()).showAndWait();
            return; // Stop if parsing failed
        }

        buttonBox.setDisable(true);
        root.setCursor(javafx.scene.Cursor.WAIT);

        logger.info("Settings updated in memory. Calling persistence service to save.");
        // Note: The UiPersistenceService should handle the UI feedback (enabling buttons, changing cursor) on completion.
        uiPersistenceService.saveSettings(getTabPane().getScene().getWindow());
    }

    /**
     * Shows the Bug Report stage.
     */
    private void handleBugReport() {
        try {
            // We use the springContext we now have from the constructor
            BugReportStage bugReportStage = stageProvider.createBugReportStage();
            bugReportStage.initOwner(getTabPane().getScene().getWindow());
            bugReportStage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open Bug Report window.", e);
            // Show an error dialog if it fails to even open
            coreProvider.createDialog(DialogType.ERROR, "Could not open bug report form: " + e.getMessage(), this.getTabPane()).showAndWait();
        }
    }

    /**
     * Called by our new handleSignOut logic when the login is successful!
     */
    private void handleLogoutSuccess(Object result) {
        logger.info("Google sign-out and data fetch successful from Settings tab.");
        setupAuthButton();

        Platform.runLater(() -> {
            coreProvider.createDialog(DialogType.INFO, "You are now signed out.", this.getTabPane()).showAndWait();
        });
    }

    /**
     * Called by our new handleSignOut logic when the login fails.
     */
    private void handleLogoutError(Throwable error) {
        logger.info("Google sign-out and data fetch successful from Settings tab.");
        setupAuthButton();

        Platform.runLater(() -> {
            coreProvider.createDialog(DialogType.ERROR,"An error occurred: " + error.getMessage(), this.getTabPane()).showAndWait();
        });
    }

    /**
     * Called by our new handleSignIn logic when the login is successful!
     */
    private void handleLoginSuccess(Object result) {
        logger.info("Google sign-in and data fetch successful from Settings tab.");
        setupAuthButton();

        Platform.runLater(() -> {
            coreProvider.createDialog(DialogType.INFO, "You are now signed in!", this.getTabPane()).showAndWait();
        });
    }

    /**
     * Called by our new handleSignIn logic when the login fails.
     */
    private void handleLoginError(Throwable error) {
        logger.error("An error occurred during the sign-in process from Settings tab.", error);

        // We can even be clever and check for our special timeout!
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        if (cause instanceof AuthorizationTimeoutException) {
            logger.warn("Authorization timed out, likely a 403.");
            Platform.runLater(() -> { // It's safer to do all UI popups in runLater, darling!
                coreProvider.createDialog(DialogType.ERROR,"Login timed out.\nThis may be because you are not an approved tester.\n\nPlease use the 'Request Access' button on the login screen.", this.getTabPane()).showAndWait();
            });
        } else {
            Platform.runLater(() -> { // Let's make this one safe too!
                coreProvider.createDialog(DialogType.ERROR,"An error occurred: " + error.getMessage(), this.getTabPane()).showAndWait();
            });
        }
    }

    /**
     * Sets the text, style, and action for our Sign In / Sign Out button
     * based on whether we have stored credentials.
     */
    private void setupAuthButton() {
        try {
            // We have to ask our auth manager!
            if (authManager.loadAndValidateStoredCredential() != null) {
                authButton.setText("Sign Out");
                authButton.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white;"); // A feisty red!
                authButton.setOnAction(e -> handleSignOut());
            } else {
                authButton.setText("Sign In");
                authButton.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: white;"); // A lovely green!
                authButton.setOnAction(e -> handleSignIn());
            }
        } catch (Exception e) {
            logger.error("Failed to check credential status for auth button.", e);
            authButton.setText("Auth Error");
            authButton.setDisable(true);
        }
    }

    /**
     * Handles the "Sign Out" button click.
     * We'll log the user out and tell them to restart.
     */
    private void handleSignOut() {
        coreProvider.createDialog(DialogType.CONFIRMATION, "Are you sure you want to sign out?", this.getTabPane())
                .showAndWait()
                .ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        logger.info("User confirmed sign out.");
                        Window parentWindow = (getTabPane() != null && getTabPane().getScene() != null)
                                ? getTabPane().getScene().getWindow()
                                : null;

                        uiTaskExecutor.execute(
                                parentWindow,
                                "Disconnecting...",
                                "Successfully disconnected!",
                                (updater) -> {
                                    logger.debug("Disconnecting the user.");
                                    uiGoogleTaskService.disconnectFromGoogle(updater);
                                    return "unused";
                                },
                                this::handleLogoutSuccess,
                                this::handleLogoutError
                        );
                    } else {
                        logger.info("User cancelled sign out.");
                    }
                });
    }

    /**
     * Handles the "Sign In" button click.
     * This is for our "offline" users. We'll tell them to restart.
     */
    private void handleSignIn() {
        logger.info("User initiated Google Sign-In process from Settings tab.");

        Window parentWindow = (getTabPane() != null && getTabPane().getScene() != null)
                ? getTabPane().getScene().getWindow()
                : null;

        uiTaskExecutor.execute(
                parentWindow,
                "Connecting...",
                "Successfully connected!",
                (updater) -> {
                    logger.debug("Executing Google connection and data fetch tasks.");
                    uiGoogleTaskService.connectToGoogle(updater);
                    uiPersistenceService.findAllWithProgress(updater); // Let's fetch their data, too!
                    return "unused";
                },
                this::handleLoginSuccess, // Our new success helper!
                this::handleLoginError    // Our new error helper!
        );
    }


    private String formatEnumName(String enumName) {
        return Arrays.stream(enumName.toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private GridPane createGridPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(30);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(70);
        grid.getColumnConstraints().addAll(col1, col2);
        return grid;
    }
}
