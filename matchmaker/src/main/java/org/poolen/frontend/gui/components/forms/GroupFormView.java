package org.poolen.frontend.gui.components.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component for creating or updating a group.
 */
public class GroupFormView extends GridPane {

    private final TextField uuidField;
    private final ComboBox<Player> dmComboBox;
    private final Map<House, CheckBox> houseCheckBoxes; // To hold our dynamic checkboxes

    private final Button actionButton;
    private final Button cancelButton;
    private final Button showPlayersButton; // The new button!
    private Group groupBeingEdited;

    public GroupFormView(Map<UUID, Player> attendingPlayers) {
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

        dmComboBox = new ComboBox<>();
        dmComboBox.setMaxWidth(Double.MAX_VALUE);

        dmComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getName());
            }
        });
        dmComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Player item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getName());
            }
        });

        updateDmList(attendingPlayers);

        this.houseCheckBoxes = new EnumMap<>(House.class);
        VBox houseBox = new VBox(5);
        Arrays.stream(House.values())
                .sorted(Comparator.comparing(this::formatHouseName))
                .forEach(house -> {
                    CheckBox cb = new CheckBox(formatHouseName(house));
                    houseCheckBoxes.put(house, cb);
                    houseBox.getChildren().add(cb);
                });

        showPlayersButton = new Button("Show Players");
        showPlayersButton.setMaxWidth(Double.MAX_VALUE);
        showPlayersButton.setStyle("-fx-background-color: #6A5ACD; -fx-text-fill: white;");

        actionButton = new Button("Create");
        cancelButton = new Button("Cancel");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");


        HBox mainActionsBox = new HBox(10, cancelButton, actionButton);
        mainActionsBox.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);

        add(new Label("UUID"), 0, 0);
        add(uuidBox, 0, 1);
        add(new Label("Dungeon Master:"), 0, 2);
        add(dmComboBox, 0, 3);
        add(new Label("House Themes:"), 0, 4);
        add(houseBox, 0, 5);
        add(showPlayersButton, 0, 6); // Add the new button
        add(spacer, 0, 7);
        add(mainActionsBox, 0, 8);

        Platform.runLater(dmComboBox::requestFocus);
    }

    public void updateDmList(Map<UUID, Player> attendingPlayers) {
        Player selectedDm = dmComboBox.getValue();
        List<Player> availableDms = attendingPlayers.values().stream()
                .filter(Player::isDungeonMaster)
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());
        dmComboBox.setItems(FXCollections.observableArrayList(availableDms));
        if (selectedDm != null && availableDms.contains(selectedDm)) {
            dmComboBox.setValue(selectedDm);
        }
    }

    // --- Public Getters ---
    public Player getSelectedDm() {
        return dmComboBox.getValue();
    }

    public List<House> getSelectedHouses() {
        return houseCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Button getActionButton() {
        return actionButton;
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public Button getShowPlayersButton() {
        return showPlayersButton;
    }

    public Group getGroupBeingEdited() {
        return groupBeingEdited;
    }

    public void populateForm(Group group) {
        this.groupBeingEdited = group;
        uuidField.setText(group.getUuid().toString());
        dmComboBox.setValue(group.getDungeonMaster());

        houseCheckBoxes.forEach((house, checkBox) -> {
            checkBox.setSelected(group.getHouses().contains(house));
        });

        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
    }

    public void clearForm() {
        this.groupBeingEdited = null;
        uuidField.clear();
        dmComboBox.getSelectionModel().clearSelection();

        houseCheckBoxes.values().forEach(cb -> cb.setSelected(false));

        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        Platform.runLater(dmComboBox::requestFocus);
    }

    private String formatHouseName(House house) {
        String lowerCase = house.name().toLowerCase();
        return lowerCase.substring(0, 1).toUpperCase() + lowerCase.substring(1);
    }
}

