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
import java.util.function.Consumer;

/**
 * A reusable JavaFX component for creating or updating a player, inheriting from BaseFormView.
 */
public class PlayerFormView extends BaseFormView<Player> {

    private TextField nameField;
    private CheckBox dmCheckBox;
    private Button deleteButton;
    private Button blacklistButton;
    private Button showBlacklistButton;
    private Button showCharactersButton;
    private Button createCharacterButton;

    private Consumer<Player> onShowCharactersRequestHandler;
    private Consumer<Player> onCreateCharacterRequestHandler;

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

        showCharactersButton = new Button("Show Characters");
        showCharactersButton.setStyle("-fx-background-color: #1E90FF; -fx-text-fill: white;");

        createCharacterButton = new Button("Create Character");
        createCharacterButton.setStyle("-fx-background-color: #32CD32; -fx-text-fill: white;");

        deleteButton.setMaxWidth(Double.MAX_VALUE);
        blacklistButton.setMaxWidth(Double.MAX_VALUE);
        showBlacklistButton.setMaxWidth(Double.MAX_VALUE);
        showCharactersButton.setMaxWidth(Double.MAX_VALUE);
        createCharacterButton.setMaxWidth(Double.MAX_VALUE);


        // Start adding controls at row 2
        add(new Label("Name:"), 0, 2);
        add(nameField, 0, 3);
        add(dmCheckBox, 0, 4);
        add(showBlacklistButton, 0, 5);
        add(blacklistButton, 0, 6);
        add(showCharactersButton, 0, 7);
        add(createCharacterButton, 0, 8);
        add(deleteButton, 0, 9);


        // Add the common controls from the parent at the end
        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        add(spacer, 0, 10);
        add(mainActionsBox, 0, 11);

        // --- Event Wiring ---
        showCharactersButton.setOnAction(e -> {
            if (onShowCharactersRequestHandler != null && itemBeingEdited != null) {
                onShowCharactersRequestHandler.accept(itemBeingEdited);
            }
        });

        createCharacterButton.setOnAction(e -> {
            if (onCreateCharacterRequestHandler != null && itemBeingEdited != null) {
                onCreateCharacterRequestHandler.accept(itemBeingEdited);
            }
        });
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

        // --- Visibility Logic ---
        deleteButton.setVisible(true);
        showBlacklistButton.setVisible(true);
        blacklistButton.setVisible(true);
        showCharactersButton.setVisible(player.hasCharacters());
        createCharacterButton.setVisible(player.hasEmptyCharacterSlot());

        Platform.runLater(nameField::requestFocus);
    }

    @Override
    public void clearForm() {
        super.clearForm();
        nameField.clear();
        dmCheckBox.setSelected(false);
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        // --- Visibility Logic ---
        deleteButton.setVisible(false);
        showBlacklistButton.setVisible(false);
        blacklistButton.setVisible(false);
        showCharactersButton.setVisible(false);
        createCharacterButton.setVisible(false);

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

    public void setOnShowCharactersRequestHandler(Consumer<Player> handler) {
        this.onShowCharactersRequestHandler = handler;
    }

    public void setOnCreateCharacterRequestHandler(Consumer<Player> handler) {
        this.onCreateCharacterRequestHandler = handler;
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
