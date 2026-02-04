package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.store.PlayerStoreProvider;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.interfaces.DmSelectRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component for creating or updating a group, updated with MaxSize and Locked fields.
 */
public class GroupFormView extends BaseFormView<Group> {

    private static final Logger logger = LoggerFactory.getLogger(GroupFormView.class);

    private ComboBox<Object> dmComboBox;
    private TextField locationField;
    private TextField maxSizeField;
    private CheckBox lockedCheckBox;
    private Map<House, CheckBox> houseCheckBoxes;
    private Button deleteButton;
    private Button showPlayersButton;

    private Consumer<Player> onDmSelectionHandler;
    private DmSelectRequestHandler dmSelectRequestHandler;
    private static final String UNASSIGNED_PLACEHOLDER = "Unassigned";
    private boolean isRevertingDmSelection = false;
    private final PlayerStore playerStore;

    public GroupFormView(PlayerStoreProvider playerStoreProvider) {
        super();
        this.playerStore = playerStoreProvider.getPlayerStore();
        setupFormControls();
        clearForm();
        logger.info("GroupFormView initialised with extended group settings.");
    }

    @Override
    protected void setupFormControls() {
        logger.debug("Setting up form controls for GroupFormView.");

        dmComboBox = new ComboBox<>();
        dmComboBox.setMaxWidth(Double.MAX_VALUE);
        setupDmComboBoxCellFactory();

        dmComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isRevertingDmSelection) return;

            Player selectedPlayer = (newVal instanceof Player) ? (Player) newVal : null;
            if (dmSelectRequestHandler != null && newVal instanceof Player) {
                boolean success = dmSelectRequestHandler.onDmSelectionRequest(selectedPlayer);
                if (!success) {
                    isRevertingDmSelection = true;
                    Platform.runLater(() -> {
                        dmComboBox.setValue(oldVal);
                        isRevertingDmSelection = false;
                    });
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

        locationField = new TextField();
        locationField.setPromptText("Enter session location (e.g., Room 5)");
        locationField.setMaxWidth(Double.MAX_VALUE);

        // --- New Max Size Field with numeric filter ---
        maxSizeField = new TextField();
        maxSizeField.setPromptText("Max party size (e.g., 6)");
        maxSizeField.setMaxWidth(Double.MAX_VALUE);
        maxSizeField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*")) {
                return change;
            }
            return null; // Reject non-numeric input
        }));

        // --- New Locked Checkbox ---
        lockedCheckBox = new CheckBox("Lock Group (Prevent auto-population)");

        showPlayersButton = new Button("Show Players");
        showPlayersButton.setMaxWidth(Double.MAX_VALUE);
        showPlayersButton.setStyle("-fx-background-color: #6A5ACD; -fx-text-fill: white;");

        deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");

        // Layout organisation
        add(new Label("Dungeon Master:"), 0, 2);
        add(dmComboBox, 0, 3);
        add(new Label("House Themes:"), 0, 4);
        add(houseGrid, 0, 5);
        add(new Label("Location:"), 0, 6);
        add(locationField, 0, 7);
        add(new Label("Settings:"), 0, 8);

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(10);
        settingsGrid.add(new Label("Max Size:"), 0, 0);
        settingsGrid.add(maxSizeField, 1, 0);
        settingsGrid.add(lockedCheckBox, 0, 1, 2, 1);
        GridPane.setHgrow(maxSizeField, Priority.ALWAYS);
        add(settingsGrid, 0, 9);

        add(showPlayersButton, 0, 10);
        add(deleteButton, 0, 11);

        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        add(spacer, 0, 12);
        add(mainActionsBox, 0, 13);
    }

    @Override
    protected UUID getUuidFromItem(Group item) {
        return item.getUuid();
    }

    @Override
    public void populateForm(Group group) {
        super.populateForm(group);
        if (group.getDungeonMaster() == null) {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        } else {
            dmComboBox.setValue(group.getDungeonMaster());
        }
        locationField.setText(group.getLocation() != null ? group.getLocation() : "");
        maxSizeField.setText(group.getMaxSize() != null ? group.getMaxSize().toString() : "");
        lockedCheckBox.setSelected(group.isLocked());
        houseCheckBoxes.forEach((house, checkBox) -> checkBox.setSelected(group.getHouses().contains(house)));

        actionButton.setText("Update");
        actionButton.setStyle("-fx-background-color: #FFA500; -fx-text-fill: white;");
        deleteButton.setVisible(true);
    }

    @Override
    public void clearForm() {
        super.clearForm();
        dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        locationField.clear();
        maxSizeField.clear();
        lockedCheckBox.setSelected(false);
        houseCheckBoxes.values().forEach(cb -> cb.setSelected(false));

        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        Platform.runLater(dmComboBox::requestFocus);
    }

    public Player getSelectedDm() {
        Object selected = dmComboBox.getValue();
        return (selected instanceof Player) ? (Player) selected : null;
    }

    public String getSelectedLocation() {
        String loc = locationField.getText();
        return (loc == null || loc.trim().isEmpty()) ? null : loc.trim();
    }

    public Integer getSelectedMaxSize() {
        String text = maxSizeField.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isLocked() {
        return lockedCheckBox.isSelected();
    }

    public List<House> getSelectedHouses() {
        return houseCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Button getDeleteButton() { return deleteButton; }
    public Button getShowPlayersButton() { return showPlayersButton; }
    public Group getGroupBeingEdited() { return super.getItemBeingEdited(); }

    public void setOnDmSelection(Consumer<Player> handler) { this.onDmSelectionHandler = handler; }
    public void setOnDmSelectionRequest(DmSelectRequestHandler handler) { this.dmSelectRequestHandler = handler; }

    public void updateDmList(Set<Player> unavailablePlayers) {
        Object selectedDm = dmComboBox.getValue();
        ObservableList<Object> items = FXCollections.observableArrayList();
        items.add(UNASSIGNED_PLACEHOLDER);

        List<Player> allDms = playerStore.getDmingPlayers().values().stream()
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        Player currentDmForThisGroup = itemBeingEdited != null ? itemBeingEdited.getDungeonMaster() : null;

        List<Player> availableDms = allDms.stream().filter(dm -> !unavailablePlayers.contains(dm) || dm.equals(currentDmForThisGroup)).toList();
        List<Player> trulyUnavailableDms = allDms.stream().filter(dm -> unavailablePlayers.contains(dm) && !dm.equals(currentDmForThisGroup)).toList();

        items.addAll(availableDms);
        if (!trulyUnavailableDms.isEmpty()) {
            items.add(new Separator());
            items.addAll(trulyUnavailableDms);
        }

        dmComboBox.setItems(items);
        if (selectedDm != null && items.contains(selectedDm)) {
            dmComboBox.setValue(selectedDm);
        } else if (currentDmForThisGroup != null) {
            dmComboBox.setValue(currentDmForThisGroup);
        } else {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        }
    }

    private void setupDmComboBoxCellFactory() {
        Callback<ListView<Object>, ListCell<Object>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(false);
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
                } else {
                    setText(((Player) item).getName());
                    setFont(Font.getDefault());
                }
            }
        };
        dmComboBox.setCellFactory(cellFactory);
        dmComboBox.setButtonCell(cellFactory.call(null));
    }

    private String formatHouseName(House house) {
        String lowerCase = house.name().toLowerCase();
        return lowerCase.substring(0, 1).toUpperCase() + lowerCase.substring(1);
    }
}