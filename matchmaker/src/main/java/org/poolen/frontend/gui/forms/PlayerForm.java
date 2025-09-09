package org.poolen.frontend.gui.forms;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerForm extends Stage {

    public PlayerForm(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        // This makes the form a pop-up that must be closed before using the main window.
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Create New Player");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(10);
        grid.setVgap(10);

        // --- Form Fields ---
        TextField nameField = new TextField();
        CheckBox dmCheckBox = new CheckBox("Dungeon Master");
        ChoiceBox<House> mainCharHouse = new ChoiceBox<>(FXCollections.observableArrayList(House.values()));
        ChoiceBox<House> altCharHouse = new ChoiceBox<>(FXCollections.observableArrayList(House.values()));

        // We'll use a custom cell factory to show player names instead of object references
        ListView<Player> buddyList = new ListView<>();
        buddyList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getName());
            }
        });
        buddyList.getItems().addAll(attendingPlayers.values().stream().filter(p -> !p.isDungeonMaster()).collect(Collectors.toList()));
        buddyList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ListView<Player> blacklist = new ListView<>();
        blacklist.setCellFactory(buddyList.getCellFactory()); // Reuse the same pretty cell factory
        blacklist.getItems().addAll(attendingPlayers.values());
        blacklist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);


        // --- Layout ---
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(dmCheckBox, 1, 1);
        grid.add(new Label("Main Character:"), 0, 2);
        grid.add(mainCharHouse, 1, 2);
        grid.add(new Label("Alt Character:"), 0, 3);
        grid.add(altCharHouse, 1, 3);
        grid.add(new Label("Buddies:"), 0, 4);
        grid.add(buddyList, 1, 4);
        grid.add(new Label("Blacklist:"), 0, 5);
        grid.add(blacklist, 1, 5);


        // --- Action Buttons ---
        Button createButton = new Button("Create Player");
        createButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Button cancelButton = new Button("Cancel");
        HBox buttonBox = new HBox(10, cancelButton, createButton);
        grid.add(buttonBox, 1, 6);

        // --- Event Handlers ---
        cancelButton.setOnAction(e -> this.close());

        createButton.setOnAction(e -> {
            // Basic validation
            if (nameField.getText().isEmpty() || mainCharHouse.getValue() == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Player must have a name and a main character house.");
                alert.showAndWait();
                return;
            }

            // Create the player object
            Player newPlayer = new Player(nameField.getText(), dmCheckBox.isSelected());
//            newPlayer.addCharacter(new Character(mainCharHouse.getValue()));
            if (altCharHouse.getValue() != null) {
                newPlayer.addCharacter(new Character(nameField.getText(), altCharHouse.getValue()));
            }

            // Add social links
/*            for (Player buddy : buddyList.getSelectionModel().getSelectedItems()) {
                newPlayer.add(buddy);
            }*/
            for (Player blacklisted : blacklist.getSelectionModel().getSelectedItems()) {
                newPlayer.blacklist(blacklisted);
            }

            // Add to the main map and update the UI
            attendingPlayers.put(newPlayer.getUuid(), newPlayer);
            onUpdate.run(); // This calls the updateUI() method in MainFrame
            this.close();
        });

        Scene scene = new Scene(grid);
        setScene(scene);
    }
}
