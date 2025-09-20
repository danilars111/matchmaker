package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.entities.Player;

import java.util.UUID;

/**
 * A reusable JavaFX component for creating or updating a player, inheriting from BaseFormView.
 */
public class PlayerFormView extends BaseFormView<Player> {

    private TextField nameField;
    private CheckBox dmCheckBox;
    private Button deleteButton;
    private Button blacklistButton;
    private Button showBlacklistButton;

    public PlayerFormView() {
        super();
        setupFormControls();
        clearForm(); // Set initial state
    }

    @Override
    protected void setupFormControls() {
        nameField = new TextField();
        dmCheckBox = new CheckBox("Dungeon Master");

        deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");

        blacklistButton = new Button("Blacklist");
        blacklistButton.setStyle("-fx-background-color: #2F4F4F; -fx-text-fill: white;");

        showBlacklistButton = new Button("Show Blacklist");
        showBlacklistButton.setStyle("-fx-background-color: #6A5ACD; -fx-text-fill: white;");

        deleteButton.setMaxWidth(Double.MAX_VALUE);
        blacklistButton.setMaxWidth(Double.MAX_VALUE);
        showBlacklistButton.setMaxWidth(Double.MAX_VALUE);

        // Start adding controls at row 2
        add(new Label("Name:"), 0, 2);
        add(nameField, 0, 3);
        add(dmCheckBox, 0, 4);
        add(showBlacklistButton, 0, 5);
        add(blacklistButton, 0, 6);
        add(deleteButton, 0, 7);

        // Add the common controls from the parent at the end
        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        add(spacer, 0, 8);
        add(mainActionsBox, 0, 9);
    }

    @Override
    protected UUID getUuidFromItem(Player item) {
        return item.getUuid();
    }

    @Override
    public void populateForm(Player player) {
        super.populateForm(player);
        nameField.setText(player.getName());
        dmCheckBox.setSelected(player.isDungeonMaster());
        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
        deleteButton.setVisible(true);
        showBlacklistButton.setVisible(true);
        blacklistButton.setVisible(true);
        Platform.runLater(nameField::requestFocus);
    }

    @Override
    public void clearForm() {
        super.clearForm();
        nameField.clear();
        dmCheckBox.setSelected(false);
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        showBlacklistButton.setVisible(false);
        blacklistButton.setVisible(false);
        setBlacklistMode(false);
        Platform.runLater(nameField::requestFocus);
    }

    // --- Specific Getters and Methods ---
    public String getPlayerName() {
        return nameField.getText();
    }
    public boolean isDungeonMaster() {
        return dmCheckBox.isSelected();
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
        return super.getItemBeingEdited();
    }
    public void setBlacklistMode(boolean isShowingBlacklist) {
        if (isShowingBlacklist) {
            showBlacklistButton.setText("Hide Blacklist");
            blacklistButton.setText("Remove from Blacklist");
            blacklistButton.setStyle("-fx-background-color: #B22222; -fx-text-fill: white;");
        } else {
            showBlacklistButton.setText("Show Blacklist");
            blacklistButton.setText("Blacklist");
            blacklistButton.setStyle("-fx-background-color: #2F4F4F; -fx-text-fill: white;");
        }
    }
}

