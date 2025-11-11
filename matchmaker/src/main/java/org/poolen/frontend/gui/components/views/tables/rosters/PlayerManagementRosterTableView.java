package org.poolen.frontend.gui.components.views.tables.rosters;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.store.PlayerStoreProvider;
import org.poolen.backend.db.store.PlayerStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Our new, focused table for Player Management.
 * It inherits all the goodness from PlayerRosterTableView and only adds
 * the specific columns and filters needed for managing the main roster.
 */
public class PlayerManagementRosterTableView extends PlayerRosterTableView{

    private final PlayerStore playerStore;

    // --- Filter Controls ---
    private CheckBox dmFilterCheckBox;
    private CheckBox attendingFilterCheckbox;
    private CheckBox allowTrialDmsCheckbox;

    // --- Columns ---
    private TableColumn<Player, Boolean> attendingColumn;
    private TableColumn<Player, Boolean> dmingColumn;

    public PlayerManagementRosterTableView(PlayerStoreProvider storeProvider) {
        super();
        this.playerStore = storeProvider.getPlayerStore();
        setupTableColumns();
        setupFilters();
        updateRoster();
    }

    @Override
    protected void setupTableColumns() {
        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Player, String> charCol = new TableColumn<>("Characters");
        charCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getCharacters().stream()
                        .map(c -> c.getHouse() != null ? c.getHouse().toString() : "")
                        .collect(Collectors.joining(", "))
        ));

        this.attendingColumn = createAttendingColumn();
        this.dmingColumn = createDmingColumn();

        table.getColumns().addAll(nameCol, charCol, attendingColumn, dmingColumn);
    }

    @Override
    protected void setupFilters() {
        dmFilterCheckBox = new CheckBox("DMs");
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        attendingFilterCheckbox = new CheckBox("Attending");
        attendingFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        allowTrialDmsCheckbox = new CheckBox("Allow Trial DMs");
        allowTrialDmsCheckbox.selectedProperty().addListener((obs, old, val) -> table.refresh());

        topFilterBar.getChildren().addAll(dmFilterCheckBox, attendingFilterCheckbox, allowTrialDmsCheckbox);
    }

    @Override
    public void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        boolean attendingOnly = attendingFilterCheckbox.isSelected();
        House selectedHouse = houseFilterBox.getValue();

        filteredData.setPredicate(player -> {
            boolean textMatch = searchText.isEmpty() ||
                    player.getName().toLowerCase().contains(searchText) ||
                    player.getUuid().toString().toLowerCase().contains(searchText);

            boolean dmMatch = !dmsOnly || player.isDungeonMaster();
            boolean attendingMatch = !attendingOnly || attendingPlayers.containsKey(player.getUuid());

            if (selectedHouse != null) {
                boolean houseMatch = player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);
                if (!houseMatch) return false;
            }

            return textMatch && dmMatch && attendingMatch;
        });
    }

    @Override
    public void updateRoster() {
        // 1. Get the current UI list
        // We still make a copy, as we'll be modifying the 'sourceItems' live
        List<Player> currentPlayers = new ArrayList<>(sourceItems);

        // 2. Make our lookup map for the *current* players
        Map<UUID, Player> currentPlayerMap = currentPlayers.stream()
                .collect(Collectors.toMap(Player::getUuid, player -> player));

        // 3. Find players to REMOVE
        // (Loop current players, check if they are missing from the new map)
        List<Player> playersToRemove = currentPlayers.stream()
                .filter(player -> !playerStore.hasPlayer(player))
                .collect(Collectors.toList());

        // 4. Find players to ADD
        // (Loop new players, check if they are missing from the current map)
        List<Player> playersToAdd = playerStore.getAllPlayers().stream()
                .filter(player -> !currentPlayerMap.containsKey(player.getUuid()))
                .collect(Collectors.toList());

        // 5. Find players to UPDATE
        // (Loop new players, check if they existed before AND have changed)
        List<Player> playersToUpdate_RemoveOld = new ArrayList<>();
        List<Player> playersToUpdate_AddNew = new ArrayList<>();

        for (Player newPlayer : playerStore.getAllPlayers()) {
            UUID id = newPlayer.getUuid();

            // Check if this player *already* exists in our table
            if (currentPlayerMap.containsKey(id)) {
                Player oldPlayer = currentPlayerMap.get(id);

                // Now we check if the data is different!
                if (!newPlayer.deepEquals(oldPlayer)) {
                    // The data changed! We schedule a swap.
                    playersToUpdate_RemoveOld.add(oldPlayer);
                    playersToUpdate_AddNew.add(newPlayer);
                }
            }
        }

        if(playersToRemove.size() > 0 || playersToUpdate_RemoveOld.size() > 0) {
            sourceItems.removeAll(playersToRemove);
            sourceItems.removeAll(playersToUpdate_RemoveOld);
        }
        if(playersToAdd.size() > 0 || playersToUpdate_AddNew.size() > 0) {
            sourceItems.addAll(playersToAdd);
            sourceItems.addAll(playersToUpdate_AddNew);
        }
    }


    // --- Blacklisting Methods ---

    public void showBlacklistedPlayers(Player editingPlayer) {
        topFilterBar.getChildren().forEach(node -> node.setDisable(true));
        houseFilterBox.setDisable(true);
        refreshButton.setDisable(true);
        searchField.setDisable(true);
        attendingColumn.setEditable(false);
        dmingColumn.setEditable(false);
        filteredData.setPredicate(player -> editingPlayer.getBlacklist().contains(player.getUuid()));
    }

    public void showAllPlayers() {
        topFilterBar.getChildren().forEach(node -> node.setDisable(false));
        houseFilterBox.setDisable(false);
        refreshButton.setDisable(false);
        searchField.setDisable(false);
        attendingColumn.setEditable(true);
        dmingColumn.setEditable(true);
        applyFilter();
    }

    // --- Column Creation ---

    private TableColumn<Player, Boolean> createAttendingColumn() {
        TableColumn<Player, Boolean> col = new TableColumn<>("Attending");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(attendingPlayers.containsKey(player.getUuid()));
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    attendingPlayers.put(player.getUuid(), player);
                } else {
                    attendingPlayers.remove(player.getUuid());
                    if (dmingPlayers.containsKey(player.getUuid())) {
                        dmingPlayers.remove(player.getUuid());
                    }
                }
                onPlayerListChanged.run();
            });
            return property;
        });
        setCheckboxCellStyle(col, false);
        return col;
    }

    private TableColumn<Player, Boolean> createDmingColumn() {
        TableColumn<Player, Boolean> col = new TableColumn<>("DMing");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(dmingPlayers.containsKey(player.getUuid()));
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    dmingPlayers.put(player.getUuid(), player);
                    if (!attendingPlayers.containsKey(player.getUuid())) {
                        attendingPlayers.put(player.getUuid(), player);
                        //table.refresh();
                    }
                } else {
                    dmingPlayers.remove(player.getUuid());
                }
                onPlayerListChanged.run();
            });
            return property;
        });
        setCheckboxCellStyle(col, true);
        return col;
    }

    // --- Style and Helper Methods ---

    private void setCheckboxCellStyle(TableColumn<Player, Boolean> column, boolean isDmingColumn) {
        column.setCellFactory(param -> new CheckBoxTableCell<Player, Boolean>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                this.setAlignment(Pos.CENTER);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    this.setGraphic(null);
                } else if (isDmingColumn) {
                    Player player = getTableRow().getItem();
                    boolean allowTrials = allowTrialDmsCheckbox != null && allowTrialDmsCheckbox.isSelected();
                    boolean shouldBeVisible = player.isDungeonMaster() || allowTrials;

                    if (getGraphic() != null) {
                        getGraphic().setVisible(shouldBeVisible);
                    }
                }
            }
        });
    }

    public void setOnPlayerListChanged(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
    }
}
