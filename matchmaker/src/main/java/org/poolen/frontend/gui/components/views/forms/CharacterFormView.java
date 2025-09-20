package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A reusable form for creating or updating a Character, inheriting from BaseFormView.
 */
public class CharacterFormView extends BaseFormView<Character> {

    private TextField nameField;
    private ComboBox<House> houseComboBox;
    private ComboBox<Player> playerComboBox;
    private CheckBox isMainCheckBox;
    private Button retireButton;
    private Button unretireButton;
    private Button deleteButton;
    private Button openPlayerButton;
    private Consumer<Player> onOpenPlayerRequestHandler;


    public CharacterFormView() {
        super();
        setupFormControls();
        clearForm(); // Set initial state
    }

    @Override
    protected void setupFormControls() {
        nameField = new TextField();
        houseComboBox = new ComboBox<>(FXCollections.observableArrayList(House.values()));
        playerComboBox = new ComboBox<>();
        isMainCheckBox = new CheckBox("Is this their main character?");

        List<Player> sortedPlayers = PlayerStore.getInstance().getAllPlayers().stream()
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());
        playerComboBox.setItems(FXCollections.observableArrayList(sortedPlayers));
        playerComboBox.setCellFactory(lv -> createPlayerCell());
        playerComboBox.setButtonCell(createPlayerCell());

        retireButton = new Button("Retire");
        unretireButton = new Button("Un-Retire");
        deleteButton = new Button("Delete Permanently");
        openPlayerButton = new Button("Open Player");

        retireButton.setStyle("-fx-background-color: #f0ad4e; -fx-text-fill: white;");
        unretireButton.setStyle("-fx-background-color: #5bc0de; -fx-text-fill: white;");
        deleteButton.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white;");
        retireButton.setMaxWidth(Double.MAX_VALUE);
        unretireButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setMaxWidth(Double.MAX_VALUE);

        // Start adding controls at row 2
        add(new Label("Character Name:"), 0, 2);
        add(nameField, 0, 3);
        add(new Label("House:"), 0, 4);
        add(houseComboBox, 0, 5);
        add(new Label("Player:"), 0, 6);

        HBox playerBox = new HBox(10, playerComboBox, openPlayerButton);
        HBox.setHgrow(playerComboBox, Priority.ALWAYS); // Let the combo box grow
        add(playerBox, 0, 7);

        add(isMainCheckBox, 0, 8);
        add(retireButton, 0, 9);
        add(unretireButton, 0, 10);
        add(deleteButton, 0, 11);

        // Add the common controls from the parent at the end
        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        add(spacer, 0, 12);
        add(mainActionsBox, 0, 13);

        openPlayerButton.setOnAction(e -> {
            if (onOpenPlayerRequestHandler != null && itemBeingEdited != null && itemBeingEdited.getPlayer() != null) {
                onOpenPlayerRequestHandler.accept(itemBeingEdited.getPlayer());
            }
        });
    }

    @Override
    protected UUID getUuidFromItem(Character item) {
        return item.getUuid();
    }

    @Override
    public void populateForm(Character character) {
        super.populateForm(character); // Populates UUID
        nameField.setText(character.getName());
        houseComboBox.setValue(character.getHouse());
        playerComboBox.setValue(character.getPlayer());
        isMainCheckBox.setSelected(character.isMain());

        playerComboBox.setDisable(true);
        actionButton.setText("Update");
        updateButtonVisibility();
        Platform.runLater(nameField::requestFocus);
    }

    @Override
    public void clearForm() {
        super.clearForm(); // Clears UUID
        nameField.clear();
        houseComboBox.setValue(null);
        playerComboBox.setValue(null);
        isMainCheckBox.setSelected(false);

        playerComboBox.setDisable(false);
        actionButton.setText("Create");
        updateButtonVisibility();
        Platform.runLater(nameField::requestFocus);
    }

    // --- Specific Getters and Methods ---
    public String getCharacterName() { return nameField.getText(); }
    public House getSelectedHouse() { return houseComboBox.getValue(); }
    public Player getSelectedPlayer() { return playerComboBox.getValue(); }
    public boolean isMainCharacter() { return isMainCheckBox.isSelected(); }
    public Button getRetireButton() { return retireButton; }
    public Button getUnretireButton() { return unretireButton; }
    public Button getDeleteButton() { return deleteButton; }
    public Character getCharacterBeingEdited() { return super.getItemBeingEdited(); }

    public void setOnOpenPlayerRequestHandler(Consumer<Player> handler) {
        this.onOpenPlayerRequestHandler = handler;
    }

    private void updateButtonVisibility() {
        boolean isEditing = itemBeingEdited != null;
        openPlayerButton.setVisible(isEditing);

        if (!isEditing) {
            retireButton.setVisible(false);
            unretireButton.setVisible(false);
            deleteButton.setVisible(false);
        } else {
            if (itemBeingEdited.isRetired()) {
                retireButton.setVisible(false);
                unretireButton.setVisible(true);
                deleteButton.setVisible(true);
            } else {
                retireButton.setVisible(true);
                unretireButton.setVisible(false);
                deleteButton.setVisible(false);
            }
        }
    }

    private ListCell<Player> createPlayerCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        };
    }
}
