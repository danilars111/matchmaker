package org.poolen.frontend.gui.components.tabs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.store.SettingsStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A dedicated tab for viewing and editing application settings.
 */
public class SettingsTab extends Tab {

    private final SettingsStore settingsStore = SettingsStore.getInstance();
    private final Map<String, TextField> settingFields = new HashMap<>();
    private final Accordion accordion = new Accordion();

    // A beautiful, dedicated list for any settings you don't want to show the user!
    private static final List<String> HIDDEN_SETTINGS = List.of(
            Settings.BUDDY_BONUS
            // You can add more settings to hide here in the future!
    );

    public SettingsTab() {
        super("Settings");

        // --- Main Layout ---
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        populateSettingsAccordion();

        ScrollPane scrollPane = new ScrollPane(accordion);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        // --- Action Buttons ---
        Button saveButton = new Button("Save Changes");
        saveButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Button cancelButton = new Button("Cancel");
        Button defaultsButton = new Button("Restore Defaults");

        HBox buttonBox = new HBox(10, defaultsButton, cancelButton, saveButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(buttonBox);

        // --- Event Handlers ---
        saveButton.setOnAction(e -> saveSettings());
        cancelButton.setOnAction(e -> resetFields());
        defaultsButton.setOnAction(e -> {
            Alert info = new Alert(Alert.AlertType.INFORMATION, "The 'Restore Defaults' feature is coming soon, darling!");
            info.showAndWait();
        });

        this.setContent(root);
    }

    /**
     * A simpler, more robust method that builds a categorized and sorted accordion.
     */
    private void populateSettingsAccordion() {
        settingFields.clear();
        accordion.getPanes().clear();

        // Step 1: We organize all setting keys by their category and display name.
        // We use TreeMaps to automatically sort everything alphabetically!
        Map<String, Map<String, String>> categorizedKeys = new TreeMap<>();
        for (String settingKey : settingsStore.getSettingsMap().keySet()) {
            // The new, more elegant check!
            if (HIDDEN_SETTINGS.contains(settingKey)) {
                continue;
            }

            String[] parts = settingKey.split("_");
            if (parts.length < 3) continue; // Skip malformed keys

            String category = camelCaseToTitleCase(parts[1]);
            String settingName = camelCaseToTitleCase(parts[2]);

            categorizedKeys.computeIfAbsent(category, k -> new TreeMap<>()).put(settingName, settingKey);
        }

        // Step 2: Now we build the UI from our beautifully sorted map.
        for (Map.Entry<String, Map<String, String>> categoryEntry : categorizedKeys.entrySet()) {
            String categoryName = categoryEntry.getKey();
            Map<String, String> settingsInCategory = categoryEntry.getValue();

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(10));

            int rowIndex = 0;
            for (Map.Entry<String, String> settingEntry : settingsInCategory.entrySet()) {
                String displayName = settingEntry.getKey();
                String originalKey = settingEntry.getValue();
                Double value = settingsStore.getSettingsMap().get(originalKey);

                Label nameLabel = new Label(displayName);
                TextField valueField = new TextField(String.valueOf(value));

                grid.add(nameLabel, 0, rowIndex);
                grid.add(valueField, 1, rowIndex);

                settingFields.put(originalKey, valueField);
                rowIndex++;
            }

            TitledPane titledPane = new TitledPane(categoryName, grid);
            accordion.getPanes().add(titledPane);
        }

        // Open the first category by default
        if (!accordion.getPanes().isEmpty()) {
            accordion.setExpandedPane(accordion.getPanes().get(0));
        }
    }

    private void saveSettings() {
        try {
            for (Map.Entry<String, TextField> fieldEntry : settingFields.entrySet()) {
                String settingName = fieldEntry.getKey();
                String valueText = fieldEntry.getValue().getText();
                double newValue = Double.parseDouble(valueText);
                settingsStore.getSettingsMap().put(settingName, newValue);
            }
            new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully!").showAndWait();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Oops! One of the settings is not a valid number, sweetheart.").showAndWait();
        }
    }

    private void resetFields() {
        populateSettingsAccordion();
    }

    /**
     * A clever little helper to turn "camelCaseNames" into "Title Case Names".
     * @param camelCase The input string.
     * @return The beautifully formatted title case string.
     */
    private String camelCaseToTitleCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }
        String result = camelCase.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
}

