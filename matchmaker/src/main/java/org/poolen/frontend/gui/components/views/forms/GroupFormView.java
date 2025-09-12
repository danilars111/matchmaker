package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.interfaces.DmSelectRequestHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component for creating or updating a group.
 */
public class GroupFormView extends GridPane {

    private final TextField uuidField;
    private final ComboBox<Object> dmComboBox; // We use Object to hold Players, Separators, and our placeholder!
    private final Map<House, CheckBox> houseCheckBoxes;
    private final Button actionButton;
    private final Button cancelButton;
    private final Button deleteButton;
    private final Button showPlayersButton;
    private Group groupBeingEdited;
    private Consumer<Player> onDmSelectionHandler;
    private DmSelectRequestHandler dmSelectRequestHandler;

    // A beautiful, special placeholder for our "Unassigned" option!
    private static final String UNASSIGNED_PLACEHOLDER = "Unassigned";

    public GroupFormView(Map<UUID, Player> attendingPlayers) {
        super();
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20));

        // --- Layout Constraints ---
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        this.getColumnConstraints().addAll(col1, new ColumnConstraints());

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
        setupDmComboBoxCellFactory();

        dmComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            Player selectedPlayer = (newVal instanceof Player) ? (Player) newVal : null;
            if (dmSelectRequestHandler != null && newVal instanceof Player) {
                boolean success = dmSelectRequestHandler.onDmSelectRequest(selectedPlayer);
                if (!success) {
                    Platform.runLater(() -> dmComboBox.setValue(oldVal));
                    return;
                }
            }
            if (onDmSelectionHandler != null) {
                onDmSelectionHandler.accept(selectedPlayer);
            }
        });

        this.houseCheckBoxes = new EnumMap<>(House.class);
        GridPane houseGrid = new GridPane();
        houseGrid.setHgap(10);
        houseGrid.setVgap(5);
        List<House> sortedHouses = Arrays.stream(House.values())
                .sorted(Comparator.comparing(this::formatHouseName))
                .toList();
        for (int i = 0; i < sortedHouses.size(); i++) {
            House house = sortedHouses.get(i);
            CheckBox cb = new CheckBox(formatHouseName(house));
            houseCheckBoxes.put(house, cb);
            houseGrid.add(cb, i % 2, i / 2);
        }

        showPlayersButton = new Button("Show Players");
        showPlayersButton.setMaxWidth(Double.MAX_VALUE);
        showPlayersButton.setStyle("-fx-background-color: #6A5ACD; -fx-text-fill: white;");

        actionButton = new Button("Create");
        cancelButton = new Button("Cancel");
        deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        HBox mainActionsBox = new HBox(10, cancelButton, actionButton);
        mainActionsBox.setAlignment(Pos.CENTER_RIGHT);

        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);

        add(new Label("UUID"), 0, 0, 2, 1);
        add(uuidBox, 0, 1, 2, 1);
        add(new Label("Dungeon Master:"), 0, 2, 2, 1);
        add(dmComboBox, 0, 3, 2, 1);
        add(new Label("House Themes:"), 0, 4, 2, 1);
        add(houseGrid, 0, 5, 2, 1);
        add(showPlayersButton, 0, 6, 2, 1);
        add(deleteButton, 0, 7, 2, 1);
        add(spacer, 0, 8, 2, 1);
        add(mainActionsBox, 0, 9, 2, 1);

        Platform.runLater(dmComboBox::requestFocus);
    }

    public void updateDmList(Map<UUID, Player> attendingPlayers, Set<Player> unavailablePlayers) {
        Object selectedDm = dmComboBox.getValue();
        List<Player> allDms = attendingPlayers.values().stream()
                .filter(Player::isDungeonMaster)
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        List<Player> availableDms = allDms.stream().filter(dm -> !unavailablePlayers.contains(dm)).toList();
        List<Player> unavailableDms = allDms.stream().filter(unavailablePlayers::contains).toList();

        ObservableList<Object> items = FXCollections.observableArrayList();
        items.add(UNASSIGNED_PLACEHOLDER);
        items.addAll(availableDms);
        if (!unavailableDms.isEmpty()) {
            items.add(new Separator());
            items.addAll(unavailableDms);
        }

        dmComboBox.setItems(items);
        if (selectedDm != null && items.contains(selectedDm)) {
            dmComboBox.setValue(selectedDm);
        } else {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        }
    }

    private void setupDmComboBoxCellFactory() {
        Callback<ListView<Object>, ListCell<Object>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof Separator) {
                    setText(null);
                    Region separatorLine = new Region();
                    separatorLine.setStyle("-fx-border-style: solid; -fx-border-width: 1 0 0 0; -fx-border-color: #c0c0c0;");
                    separatorLine.setMaxHeight(1);
                    setGraphic(separatorLine);
                    setPadding(new Insets(5, 0, 5, 0));
                    setDisable(true);
                } else if (item.equals(UNASSIGNED_PLACEHOLDER)) {
                    setText(UNASSIGNED_PLACEHOLDER);
                    setFont(Font.font("System", FontPosture.ITALIC, 12));
                    setGraphic(null);
                    setDisable(false);
                } else { // It must be a Player
                    setText(((Player) item).getName());
                    setFont(Font.getDefault());
                    setGraphic(null);
                    setDisable(false);
                }
            }
        };
        dmComboBox.setCellFactory(cellFactory);
        dmComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item instanceof Separator) {
                    setText(null);
                } else if (item.equals(UNASSIGNED_PLACEHOLDER)) {
                    setText(UNASSIGNED_PLACEHOLDER);
                    setFont(Font.font("System", FontPosture.ITALIC, 12));
                } else {
                    setText(((Player) item).getName());
                    setFont(Font.getDefault());
                }
            }
        });
    }

    public Player getSelectedDm() {
        Object selected = dmComboBox.getValue();
        return (selected instanceof Player) ? (Player) selected : null;
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

    public Button getDeleteButton() {
        return deleteButton;
    }

    public Button getShowPlayersButton() {
        return showPlayersButton;
    }

    public Group getGroupBeingEdited() {
        return groupBeingEdited;
    }

    public void setOnDmSelection(Consumer<Player> handler) {
        this.onDmSelectionHandler = handler;
    }

    public void setOnDmSelectionRequest(DmSelectRequestHandler handler) {
        this.dmSelectRequestHandler = handler;
    }

    public void populateForm(Group group) {
        this.groupBeingEdited = group;
        uuidField.setText(group.getUuid().toString());
        if (group.getDungeonMaster() == null) {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        } else {
            dmComboBox.setValue(group.getDungeonMaster());
        }
        houseCheckBoxes.forEach((house, checkBox) -> checkBox.setSelected(group.getHouses().contains(house)));
        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
        deleteButton.setVisible(true);
    }

    public void clearForm() {
        this.groupBeingEdited = null;
        uuidField.clear();
        dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        houseCheckBoxes.values().forEach(cb -> cb.setSelected(false));
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        Platform.runLater(dmComboBox::requestFocus);
    }

    private String formatHouseName(House house) {
        String lowerCase = house.name().toLowerCase();
        return lowerCase.substring(0, 1).toUpperCase() + lowerCase.substring(1);
    }
}

