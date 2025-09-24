package org.poolen.frontend.gui.components.views.tables;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.interfaces.PlayerAddRequestHandler;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A highly reusable JavaFX component that displays a filterable and paginated table of players.
 */
public class PlayerRosterTableView extends BaseRosterTableView<Player> implements PlayerUpdateListener {

    public enum RosterMode {
        PLAYER_MANAGEMENT,
        GROUP_ASSIGNMENT
    }

    private final PlayerStore playerStore = PlayerStore.getInstance();
    private final RosterMode mode;
    private final Map<UUID, Player> attendingPlayers;
    private final Map<UUID, Player> dmingPlayers;
    private Group currentGroup;
    private Map<UUID, Player> partyForNewGroup;
    private Player dmForNewGroup;
    private List<Group> allGroups = new ArrayList<>();
    private PlayerAddRequestHandler onPlayerAddRequestHandler;

    // --- Filter Controls ---
    private CheckBox dmFilterCheckBox;
    private CheckBox modeSpecificFilterCheckbox; // "Attending" or "Selected"
    private CheckBox availableOnlyCheckbox;      // Only in GROUP_ASSIGNMENT mode
    private CheckBox allowTrialDmsCheckbox;      // Only in PLAYER_MANAGEMENT mode

    // --- Columns for special handling ---
    private TableColumn<Player, Boolean> attendingColumn;
    private TableColumn<Player, Boolean> dmingColumn;


    public PlayerRosterTableView(RosterMode mode, Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        super();
        this.mode = mode;
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;
        this.searchField.setPromptText("Search by name or UUID...");

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

        table.getColumns().addAll(nameCol, charCol);

        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            setupPlayerManagementColumns();
        } else {
            setupGroupAssignmentColumns();
        }
    }

    @Override
    protected void setupFilters() {
        dmFilterCheckBox = new CheckBox("DMs");
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        topFilterBar.getChildren().add(dmFilterCheckBox);

        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            modeSpecificFilterCheckbox = new CheckBox("Attending");
            allowTrialDmsCheckbox = new CheckBox("Allow Trial DMs");
            allowTrialDmsCheckbox.selectedProperty().addListener((obs, old, val) -> table.refresh());
            topFilterBar.getChildren().addAll(modeSpecificFilterCheckbox, allowTrialDmsCheckbox);
        } else { // GROUP_ASSIGNMENT
            modeSpecificFilterCheckbox = new CheckBox("Selected");
            availableOnlyCheckbox = new CheckBox("Available");
            availableOnlyCheckbox.setSelected(true);
            availableOnlyCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
            topFilterBar.getChildren().addAll(availableOnlyCheckbox, modeSpecificFilterCheckbox);
        }
        modeSpecificFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
    }

    @Override
    public void setOnItemDoubleClick(Consumer<Player> onItemDoubleClick) {
        table.setRowFactory(tv -> {
            TableRow<Player> row = new TableRow<>() {
                @Override
                protected void updateItem(Player player, boolean empty) {
                    super.updateItem(player, empty);

                    // Always clear the style first to handle recycling
                    setStyle("");

                    if (player != null && !empty) {
                        // Priority 1: Player has no characters at all (needs attention)
                        if (player.getCharacters().isEmpty()) {
                            setStyle("-fx-background-color: #FFEBEE;"); // Light Red
                            // Priority 2: Player has characters, but no main (needs attention)
                        } else if (player.getMainCharacter() == null) {
                            setStyle("-fx-background-color: #FFF8E1;"); // Light Amber
                            // Priority 3: Player has exactly one character (just for info)
                        } else if (player.getCharacters().size() == 1) {
                            setStyle("-fx-background-color: #E3F2FD;"); // Light Blue
                        }
                    }
                }
            };

            // Re-apply the double-click functionality
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty()) && row.getItem() != null) {
                    onItemDoubleClick.accept(row.getItem());
                }
            });

            return row;
        });
    }

    @Override
    public void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        House selectedHouse = houseFilterBox.getValue();


        filteredData.setPredicate(player -> {
            boolean textMatch = searchText.isEmpty() ||
                    player.getName().toLowerCase().contains(searchText) ||
                    player.getUuid().toString().toLowerCase().contains(searchText);

            boolean dmMatch = !dmsOnly || player.isDungeonMaster();

            if (selectedHouse != null) {
                boolean houseMatch = player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);
                if (!houseMatch) return false;
            }

            if (mode == RosterMode.PLAYER_MANAGEMENT) {
                boolean attendingOnly = modeSpecificFilterCheckbox.isSelected();
                boolean attendingMatch = !attendingOnly || attendingPlayers.containsKey(player.getUuid());
                return textMatch && dmMatch && attendingMatch;
            } else { // GROUP_ASSIGNMENT
                if (dmingPlayers != null && dmingPlayers.containsKey(player.getUuid())) {
                    return false;
                }
                Player dmToExclude = (currentGroup != null) ? currentGroup.getDungeonMaster() : dmForNewGroup;
                if (dmToExclude != null && player.equals(dmToExclude)) {
                    return false;
                }
                if (availableOnlyCheckbox.isSelected()) {
                    for (Group group : allGroups) {
                        if (currentGroup != null && group.equals(currentGroup)) continue;
                        if (group.getParty().containsKey(player.getUuid())) return false;
                    }
                }
                boolean selectedOnly = modeSpecificFilterCheckbox.isSelected();
                Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
                boolean selectedMatch = !selectedOnly || (partyMap != null && partyMap.containsKey(player.getUuid()));

                return textMatch && dmMatch && selectedMatch;
            }
        });
    }

    @Override
    public void updateRoster() {
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            sourceItems.setAll(playerStore.getAllPlayers());
        } else {
            sourceItems.setAll(attendingPlayers.values());
        }
        applyFilter();
    }

    /**
     * This is our new listener method! When another part of the app says something changed,
     * this will be called, and we can just refresh our table's visuals.
     */
    @Override
    public void onPlayerUpdate() {
        // A player's character list might have changed, which affects our row coloring.
        // We just need to refresh the visual state of the table, not reload all the data.
        table.refresh();
    }

    // --- Blacklisting Methods ---

    public void showBlacklistedPlayers(Player editingPlayer) {
        topFilterBar.getChildren().forEach(node -> node.setDisable(true));
        houseFilterBox.setDisable(true);
        refreshButton.setDisable(true);
        searchField.setDisable(true);
        attendingColumn.setEditable(false);
        dmingColumn.setEditable(false);
        filteredData.setPredicate(player -> editingPlayer.getBlacklist().containsKey(player.getUuid()));
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

    // --- Mode-Specific Setup ---

    private void setupPlayerManagementColumns() {
        this.attendingColumn = createAttendingColumn();
        this.dmingColumn = createDmingColumn();
        table.getColumns().addAll(attendingColumn, dmingColumn);
    }

    private void setupGroupAssignmentColumns() {
        TableColumn<Player, Boolean> selectedCol = createSelectedColumn();
        table.getColumns().add(selectedCol);
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
                        table.refresh();
                    }
                }
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
                        table.refresh();
                    }
                } else {
                    dmingPlayers.remove(player.getUuid());
                }
            });
            return property;
        });
        setCheckboxCellStyle(col, true);
        return col;
    }

    private TableColumn<Player, Boolean> createSelectedColumn() {
        TableColumn<Player, Boolean> col = new TableColumn<>("Selected");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
            boolean isSelected = partyMap != null && partyMap.containsKey(player.getUuid());
            SimpleBooleanProperty property = new SimpleBooleanProperty(isSelected);
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    if (onPlayerAddRequestHandler != null) {
                        boolean success = onPlayerAddRequestHandler.onPlayerAddRequest(player);
                        if (!success) Platform.runLater(() -> property.set(false));
                    }
                } else {
                    if (currentGroup != null) currentGroup.removePartyMember(player);
                    else if (partyForNewGroup != null) partyForNewGroup.remove(player.getUuid());
                }
            });
            return property;
        });
        setCheckboxCellStyle(col, false);
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

    // --- Getters and Setters for Parent Tab ---

    public Player getSelectedPlayer() {
        return getSelectedItem();
    }

    public void displayForGroup(Group group) {
        this.currentGroup = group;
        this.partyForNewGroup = null;
        this.dmForNewGroup = null;
        table.refresh();
    }

    public void setPartyForNewGroup(Map<UUID, Player> partyMap) {
        this.currentGroup = null;
        this.partyForNewGroup = partyMap;
        table.refresh();
    }

    public void setDmForNewGroup(Player dm) {
        this.dmForNewGroup = dm;
        applyFilter();
    }

    public void setAllGroups(List<Group> groups) {
        this.allGroups = groups;
        applyFilter();
    }

    public void setOnPlayerAddRequest(PlayerAddRequestHandler handler) {
        this.onPlayerAddRequestHandler = handler;
    }
}

