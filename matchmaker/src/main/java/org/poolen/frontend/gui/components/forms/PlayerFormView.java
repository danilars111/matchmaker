package org.poolen.frontend.gui.components.forms;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.poolen.backend.db.entities.Player;

/**
 * A reusable JavaFX component for creating or updating a player.
 */
public class PlayerFormView extends GridPane {

    private final TextField uuidField;
    private final TextField nameField;
    private final CheckBox dmCheckBox;

    private final Button actionButton;
    private final Button cancelButton;
    private Player playerBeingEdited;

    public PlayerFormView() {
        super();
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20));

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        this.getColumnConstraints().addAll(col1);

        uuidField = new TextField();
        uuidField.setEditable(false);
        // --- The new styling magic! ---
        // A lovely grey to show it's read-only.
        uuidField.setStyle("-fx-control-inner-background: #f0f0f0; -fx-text-fill: #555;");

        // --- The new copy button! ---
        Button copyButton = new Button("📋"); // A cute clipboard icon!
        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(uuidField.getText());
            clipboard.setContent(content);
        });

        // We'll put the field and button together in a little box.
        HBox uuidBox = new HBox(5, uuidField, copyButton);
        HBox.setHgrow(uuidField, Priority.ALWAYS); // Let the text field stretch

        nameField = new TextField();
        dmCheckBox = new CheckBox("Dungeon Master");
        actionButton = new Button("Player");
        cancelButton = new Button("Cancel");

        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        add(new Label("UUID"), 0, 0);
        add(uuidBox, 0, 1); // Add our new box to the layout
        add(new Label("Name:"), 0, 2);
        add(nameField, 0, 3);
        add(dmCheckBox, 0, 4);

        HBox buttonBox = new HBox(10, cancelButton, actionButton);
        add(buttonBox, 0, 5);

        // --- The new focus magic! ---
        // This politely asks the name field to take focus after the window has appeared.
        Platform.runLater(nameField::requestFocus);
    }

    // --- Public Getters ---
    public String getPlayerName() {
        return nameField.getText();
    }

    public boolean isDungeonMaster() {
        return dmCheckBox.isSelected();
    }

    public Button getActionButton() {
        return actionButton;
    }

    public ButtonBase getCancelButton() {
        return cancelButton;
    }

    public Player getPlayerBeingEdited() {
        return playerBeingEdited;
    }

    /**
     * Populates the form with an existing player's data and switches to "Update" mode.
     * @param player The player to edit.
     */
    public void populateForm(Player player) {
        this.playerBeingEdited = player;
        uuidField.setText(player.getUuid().toString());
        nameField.setText(player.getName());
        dmCheckBox.setSelected(player.isDungeonMaster());
        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
        Platform.runLater(nameField::requestFocus); // Also set focus when updating
    }

    /**
     * Clears the input fields of the form and switches back to "Create" mode.
     */
    public void clearForm() {
        this.playerBeingEdited = null;
        nameField.clear();
        uuidField.clear();
        dmCheckBox.setSelected(false);
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Platform.runLater(nameField::requestFocus); // And set focus when clearing
    }
}
