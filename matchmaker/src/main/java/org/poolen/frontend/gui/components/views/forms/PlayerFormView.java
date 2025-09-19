package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.VBox;
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
    private final Button deleteButton;
    private final Button blacklistButton;
    private final Button showBlacklistButton; // The new button!
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
        uuidField.setStyle("-fx-control-inner-background: #f0f0f0; -fx-text-fill: #555;");

        Button copyButton = new Button("📋");
        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(uuidField.getText());
            clipboard.setContent(content);
        });

        HBox uuidBox = new HBox(5, uuidField, copyButton);
        HBox.setHgrow(uuidField, Priority.ALWAYS);

        nameField = new TextField();
        dmCheckBox = new CheckBox("Dungeon Master");
        actionButton = new Button("Create");
        cancelButton = new Button("Cancel");

        // --- Buttons and Styling ---
        deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");
        deleteButton.setVisible(false);

        blacklistButton = new Button("Blacklist");
        blacklistButton.setStyle("-fx-background-color: #2F4F4F; -fx-text-fill: white;");
        blacklistButton.setVisible(false);

        showBlacklistButton = new Button("Show Blacklist");
        showBlacklistButton.setStyle("-fx-background-color: #6A5ACD; -fx-text-fill: white;"); // A lovely slate blue
        showBlacklistButton.setVisible(false);


        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        // --- New Layout Logic ---
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        blacklistButton.setMaxWidth(Double.MAX_VALUE);
        showBlacklistButton.setMaxWidth(Double.MAX_VALUE);

        HBox mainActionsBox = new HBox(10, cancelButton, actionButton);
        mainActionsBox.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        // ------------------------

        add(new Label("UUID"), 0, 0);
        add(uuidBox, 0, 1);
        add(new Label("Name:"), 0, 2);
        add(nameField, 0, 3);
        add(dmCheckBox, 0, 4);
        add(showBlacklistButton, 0, 5); // New button gets its own row
        add(blacklistButton, 0, 6);
        add(deleteButton, 0, 7);
        add(spacer, 0, 8);
        add(mainActionsBox, 0, 9);

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

    public Button getDeleteButton() {
        return deleteButton;
    }

    public Button getBlacklistButton() {
        return blacklistButton;
    }

    public Button getShowBlacklistButton() {
        return showBlacklistButton;
    }

    public Player getPlayerBeingEdited() {
        return playerBeingEdited;
    }

    /**
     * Toggles the text and style of the blacklist buttons.
     * @param isShowingBlacklist True if the view is in "show blacklist" mode.
     */
    public void setBlacklistMode(boolean isShowingBlacklist) {
        if (isShowingBlacklist) {
            showBlacklistButton.setText("Hide Blacklist");
            blacklistButton.setText("Remove from Blacklist");
            blacklistButton.setStyle("-fx-background-color: #B22222; -fx-text-fill: white;"); // Firebrick red for removal
        } else {
            showBlacklistButton.setText("Show Blacklist");
            blacklistButton.setText("Blacklist");
            blacklistButton.setStyle("-fx-background-color: #2F4F4F; -fx-text-fill: white;"); // Back to slate grey
        }
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
        deleteButton.setVisible(true);
        showBlacklistButton.setVisible(true);
        blacklistButton.setVisible(true);
        Platform.runLater(nameField::requestFocus);
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
        deleteButton.setVisible(false);
        showBlacklistButton.setVisible(false);
        blacklistButton.setVisible(false);
        setBlacklistMode(false); // Make sure to reset the button text!
        Platform.runLater(nameField::requestFocus);
    }
}
