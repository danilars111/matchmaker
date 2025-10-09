package org.poolen.frontend.gui.components.views.forms;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
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
import org.poolen.frontend.gui.interfaces.DmSelectRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component for creating or updating a group, inheriting from BaseFormView.
 */
public class GroupFormView extends BaseFormView<Group> {

    private static final Logger logger = LoggerFactory.getLogger(GroupFormView.class);

    private ComboBox<Object> dmComboBox;
    private Map<House, CheckBox> houseCheckBoxes;
    private Button deleteButton;
    private Button showPlayersButton;

    private Consumer<Player> onDmSelectionHandler;
    private DmSelectRequestHandler dmSelectRequestHandler;
    private static final String UNASSIGNED_PLACEHOLDER = "Unassigned";
    private boolean isRevertingDmSelection = false;

    public GroupFormView() {
        super();
        setupFormControls();
        clearForm(); // Set initial state
        logger.info("GroupFormView initialised.");
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
            logger.trace("DM selection changed from '{}' to '{}'.", oldVal, newVal);
            if (dmSelectRequestHandler != null && newVal instanceof Player) {
                boolean success = dmSelectRequestHandler.onDmSelectionRequest(selectedPlayer);
                if (!success) {
                    logger.warn("DM selection request for '{}' was denied. Reverting selection to '{}'.", selectedPlayer.getName(), oldVal);
                    isRevertingDmSelection = true;
                    Platform.runLater(() -> {
                        dmComboBox.setValue(oldVal);
                        isRevertingDmSelection = false;
                    });
                    return;
                }
            }
            if (onDmSelectionHandler != null) {
                logger.debug("Invoking DM selection handler for player: {}", selectedPlayer != null ? selectedPlayer.getName() : "null");
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

        deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #DC143C; -fx-text-fill: white;");

        add(new Label("Dungeon Master:"), 0, 2);
        add(dmComboBox, 0, 3);
        add(new Label("House Themes:"), 0, 4);
        add(houseGrid, 0, 5);
        add(showPlayersButton, 0, 6);
        add(deleteButton, 0, 7);

        // Add the common controls from the parent at the end
        VBox spacer = new VBox();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        add(spacer, 0, 8);
        add(mainActionsBox, 0, 9);
    }

    @Override
    protected UUID getUuidFromItem(Group item) {
        return item.getUuid();
    }

    @Override
    public void populateForm(Group group) {
        super.populateForm(group);
        logger.debug("Populating group-specific fields for group with UUID '{}'.", group.getUuid());
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

    @Override
    public void clearForm() {
        super.clearForm();
        logger.debug("Clearing group-specific fields.");
        dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        houseCheckBoxes.values().forEach(cb -> cb.setSelected(false));
        actionButton.setText("Create");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");
        deleteButton.setVisible(false);
        Platform.runLater(dmComboBox::requestFocus);
    }

    // --- Specific Getters and Methods ---
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

    public Button getDeleteButton() {
        return deleteButton;
    }

    public Button getShowPlayersButton() {
        return showPlayersButton;
    }

    public Group getGroupBeingEdited() {
        return super.getItemBeingEdited();
    }

    public void setOnDmSelection(Consumer<Player> handler) {
        this.onDmSelectionHandler = handler;
    }

    public void setOnDmSelectionRequest(DmSelectRequestHandler handler) {
        this.dmSelectRequestHandler = handler;
    }

    public void updateDmList(Map<UUID, Player> dmingPlayers, Set<Player> unavailablePlayers) {
        logger.debug("Updating DM list. Total DMs: {}, Unavailable DMs in other groups: {}.", dmingPlayers.size(), unavailablePlayers.size());
        Object selectedDm = dmComboBox.getValue();
        ObservableList<Object> items = FXCollections.observableArrayList();
        items.add(UNASSIGNED_PLACEHOLDER);

        List<Player> allDms = dmingPlayers.values().stream()
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        Player currentDmForThisGroup = itemBeingEdited != null ? itemBeingEdited.getDungeonMaster() : null;

        List<Player> availableDms = allDms.stream().filter(dm -> !unavailablePlayers.contains(dm) || dm.equals(currentDmForThisGroup)).toList();
        List<Player> trulyUnavailableDms = allDms.stream().filter(dm -> unavailablePlayers.contains(dm) && !dm.equals(currentDmForThisGroup)).toList();
        logger.trace("Found {} available DMs and {} truly unavailable DMs.", availableDms.size(), trulyUnavailableDms.size());

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
                setDisable(false); // Reset disable state
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
