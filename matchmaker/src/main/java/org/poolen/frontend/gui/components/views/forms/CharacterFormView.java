package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A reusable JavaFX component for creating or updating a character.
 */
public class CharacterFormView extends GridPane {

    private final TextField nameField;
    private final ComboBox<House> houseComboBox;
    private final ComboBox<Player> playerComboBox;
    private final CheckBox mainCheckBox;
    private final CheckBox retiredCheckBox;

    private final Button actionButton;
    private final Button cancelButton;
    private final Button deleteButton;
    private Character characterBeingEdited;

    public CharacterFormView(List<Player> allPlayers) {
        super();
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20));

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        this.getColumnConstraints().addAll(col1);

        nameField = new TextField();
        houseComboBox = new ComboBox<>(FXCollections.observableArrayList(Arrays.asList(House.values())));
        playerComboBox = new ComboBox<>(FXCollections.observableArrayList(allPlayers));
        mainCheckBox = new CheckBox("Main Character");
        retiredCheckBox = new CheckBox("Retired");

        setupPlayerComboBoxCellFactory();

        actionButton = new Button("Create");
        cancelButton = new Button("Cancel");
        deleteButton = new Button("Delete");

        // --- Styling ---
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");
        deleteButton.setVisible(false);

        HBox mainActionsBox = new HBox(10, cancelButton, actionButton);
        mainActionsBox.setAlignment(Pos.CENTER_RIGHT);
        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);

        add(new Label("Name:"), 0, 0);
        add(nameField, 0, 1);
        add(new Label("Player:"), 0, 2);
        add(playerComboBox, 0, 3);
        add(new Label("House:"), 0, 4);
        add(houseComboBox, 0, 5);
        add(mainCheckBox, 0, 6);
        add(retiredCheckBox, 0, 7);
        add(deleteButton, 0, 8);
        add(spacer, 0, 9);
        add(mainActionsBox, 0, 10);

        Platform.runLater(nameField::requestFocus);
    }

    private void setupPlayerComboBoxCellFactory() {
        Callback<ListView<Player>, ListCell<Player>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getName());
            }
        };
        playerComboBox.setCellFactory(cellFactory);
        playerComboBox.setButtonCell(cellFactory.call(null));
    }

    // --- Getters for form data ---
    public String getCharacterName() { return nameField.getText(); }
    public House getSelectedHouse() { return houseComboBox.getValue(); }
    public Player getSelectedPlayer() { return playerComboBox.getValue(); }
    public boolean isMain() { return mainCheckBox.isSelected(); }
    public boolean isRetired() { return retiredCheckBox.isSelected(); }

    // --- Getters for controls ---
    public Button getActionButton() { return actionButton; }
    public Button getCancelButton() { return cancelButton; }
    public Button getDeleteButton() { return deleteButton; }
    public Character getCharacterBeingEdited() { return characterBeingEdited; }

    /**
     * Populates the form with an existing character's data for editing.
     * @param character The character to edit.
     */
    public void populateForm(Character character) {
        this.characterBeingEdited = character;
        nameField.setText(character.getName());
        houseComboBox.setValue(character.getHouse());
        playerComboBox.setValue(character.getPlayer());
        mainCheckBox.setSelected(character.isMain());
        retiredCheckBox.setSelected(character.isRetired());
        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
        deleteButton.setVisible(true);
        Platform.runLater(nameField::requestFocus);
    }

    /**
     * Clears the form fields and resets to "Create" mode.
     */
    public void clearForm() {
        this.characterBeingEdited = null;
        nameField.clear();
        houseComboBox.getSelectionModel().clearSelection();
        playerComboBox.getSelectionModel().clearSelection();
        mainCheckBox.setSelected(false);
        retiredCheckBox.setSelected(false);
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        Platform.runLater(nameField::requestFocus);
    }

    /**
     * Updates the list of players available in the dropdown.
     * @param players The full list of players.
     */
    public void updatePlayerList(List<Player> players) {
        players.sort(Comparator.comparing(Player::getName));
        playerComboBox.setItems(FXCollections.observableArrayList(players));
    }
}
