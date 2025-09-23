package org.poolen.frontend.gui.components.tabs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISetting;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.web.google.SheetsServiceManager;

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

    private final SettingsStore settingsStore = SettingsStore.getInstance();
    private final Accordion accordion = new Accordion();
    private final Map<ISetting, Node> settingControls = new HashMap<>();
    private final HBox buttonBox;
    private final BorderPane root;

    private static final List<ISetting> HIDDEN_SETTINGS = List.of(
            Settings.MatchmakerBonusSetting.BUDDY_BONUS
    );

    public SettingsTab() {
        super("Settings");

        // --- Main Layout ---
        root = new BorderPane();
        root.setPadding(new Insets(10));

        populateSettingsAccordion();

        ScrollPane scrollPane = new ScrollPane(accordion);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        // --- Action Buttons ---
        Button saveButton = new Button("Save Changes");
        saveButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Button cancelButton = new Button("Discard Changes");
        Button defaultsButton = new Button("Restore Defaults");
        defaultsButton.setStyle("-fx-background-color: #f0ad4e; -fx-text-fill: white;");

        buttonBox = new HBox(10, defaultsButton, cancelButton, saveButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(buttonBox);

        // --- Event Handlers ---
        saveButton.setOnAction(e -> saveSettings());
        cancelButton.setOnAction(e -> populateSettingsAccordion());
        defaultsButton.setOnAction(e -> {
            new ConfirmationDialog("Are you sure you want to restore all settings to their default values?", this.getTabPane())
                    .showAndWait()
                    .ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            settingsStore.setDefaultSettings();
                            populateSettingsAccordion();
                            new InfoDialog("All settings have been restored to their defaults.", this.getTabPane()).showAndWait();
                        }
                    });
        });

        this.setContent(root);
    }

    private void populateSettingsAccordion() {
        settingControls.clear();
        accordion.getPanes().clear();

        List<Class<? extends ISetting>> settingCategories = List.of(
                Settings.MatchmakerBonusSetting.class,
                Settings.MatchmakerMultiplierSetting.class,
                Settings.MatchmakerPrioritySetting.class,
                Settings.PersistenceSettings.class
        );

        for (Class<? extends ISetting> categoryClass : settingCategories) {
            GridPane grid = createGridPane();
            int rowIndex = 0;

            for (ISetting settingEnum : categoryClass.getEnumConstants()) {
                if (HIDDEN_SETTINGS.contains(settingEnum)) continue;

                Setting<?> setting = settingsStore.getSetting(settingEnum);
                if (setting == null) continue;

                Label nameLabel = new Label(formatEnumName(setting.getName().toString()));
                nameLabel.setTooltip(new Tooltip(setting.getDescription()));

                Node valueControl = createControlForSetting(setting);
                settingControls.put(setting.getName(), valueControl);

                grid.add(nameLabel, 0, rowIndex);
                grid.add(valueControl, 1, rowIndex++);
            }

            String categoryName = categoryClass.getSimpleName().replace("Settings", "").replace("Setting", "");
            TitledPane titledPane = new TitledPane(categoryName, grid);
            accordion.getPanes().add(titledPane);
        }

        if (!accordion.getPanes().isEmpty()) {
            accordion.setExpandedPane(accordion.getPanes().get(0));
        }
    }

    private Node createControlForSetting(Setting<?> setting) {
        Object value = setting.getSettingValue();

        if (setting.getName() instanceof Settings.MatchmakerPrioritySetting) {
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
                Collections.swap(priorities, index, index - 1);
                rebuildPriorityEditorUI(container);
            });
            downButton.setOnAction(e -> {
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
        // --- Step 1: Update the in-memory store from the UI ---
        try {
            for (Map.Entry<ISetting, Node> entry : settingControls.entrySet()) {
                ISetting settingEnum = entry.getKey();
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
                    settingsStore.updateSetting(settingEnum, newValue);
                }
            }
        } catch (Exception e) {
            new ErrorDialog("Could not save settings. Please check your values.\nError: " + e.getMessage(), this.getTabPane()).showAndWait();
            return; // Stop if parsing failed
        }

        // --- Step 2: Save the updated store to Google Sheets ---
        String spreadsheetId = (String) settingsStore.getSetting(Settings.PersistenceSettings.SHEETS_ID).getSettingValue();
        if (spreadsheetId == null || spreadsheetId.trim().isEmpty()) {
            new ErrorDialog("The Sheets ID is empty, darling! I can't save without an address.", this.getTabPane()).showAndWait();
            return;
        }

        buttonBox.setDisable(true);
        root.setCursor(javafx.scene.Cursor.WAIT);

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SheetsServiceManager.saveData(spreadsheetId);
                return null;
            }

            @Override
            protected void succeeded() {
                buttonBox.setDisable(false);
                root.setCursor(javafx.scene.Cursor.DEFAULT);
                new InfoDialog("Settings have been saved to Google Sheets successfully!", getTabPane()).showAndWait();
            }

            @Override
            protected void failed() {
                buttonBox.setDisable(false);
                root.setCursor(javafx.scene.Cursor.DEFAULT);
                new ErrorDialog("Could not save settings to Google Sheets.\nError: " + getException().getMessage(), getTabPane()).showAndWait();
            }
        };
        new Thread(saveTask).start();
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

