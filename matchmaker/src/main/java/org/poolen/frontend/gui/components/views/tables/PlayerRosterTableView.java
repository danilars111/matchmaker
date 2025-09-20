package org.poolen.frontend.gui.components.views.tables;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.interfaces.PlayerAddRequestHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A concrete implementation of BaseRosterTableView for displaying Players.
 */
public class PlayerRosterTableView extends BaseRosterTableView<Player> {

    public enum RosterMode {
        PLAYER_MANAGEMENT,
        GROUP_ASSIGNMENT
    }

    private final RosterMode mode;
    private final Map<UUID, Player> attendingPlayers;
    private final Map<UUID, Player> dmingPlayers;
    private final Runnable onPlayerListChanged;

    private Group currentGroup;
    private Map<UUID, Player> partyForNewGroup;
    private Player dmForNewGroup;
    private List<Group> allGroups = new ArrayList<>();

    private PlayerAddRequestHandler onPlayerAddRequestHandler;

    // Filter controls
    private CheckBox dmFilterCheckBox;
    private CheckBox modeSpecificFilterCheckbox;
    private CheckBox availableOnlyCheckbox;
    private CheckBox allowTrialDmsCheckbox;

    // Columns that change based on mode
    private TableColumn<Player, Boolean> interactiveColumn;
    private TableColumn<Player, Boolean> dmingColumn;


    public PlayerRosterTableView(RosterMode mode, Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        super();
        this.mode = mode;
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;
        this.onPlayerListChanged = onPlayerListChanged;

        // Call setup methods now that fields are initialized
        setupTableColumns();
        setupFilters();

        // Now that everything is set up, we can populate the table
        updateRoster();
    }

    @Override
    protected void setupTableColumns() {
        TableColumn<Player, Void> rowNumCol = new TableColumn<>("#");
        rowNumCol.setSortable(false);
        rowNumCol.setPrefWidth(40);
        rowNumCol.setMaxWidth(40);
        rowNumCol.setMinWidth(40);
        rowNumCol.setStyle("-fx-alignment: CENTER;");
        rowNumCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf((pagination.getCurrentPageIndex() * getRowsPerPage()) + getIndex() + 1));
            }
        });

        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Player, String> charCol = new TableColumn<>("Characters");
        charCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getCharacters().stream()
                        .map(c -> c.getHouse().toString())
                        .collect(Collectors.joining(", "))
        ));

        table.getColumns().addAll(rowNumCol, nameCol, charCol);

        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            interactiveColumn = createAttendingColumn();
            dmingColumn = createDmingColumn();
            table.getColumns().addAll(interactiveColumn, dmingColumn);
        } else {
            interactiveColumn = createSelectedColumn();
            table.getColumns().add(interactiveColumn);
        }
        table.setEditable(true);
    }

    @Override
    protected void setupFilters() {
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            dmFilterCheckBox = new CheckBox("DMs");
            modeSpecificFilterCheckbox = new CheckBox("Attending");
            allowTrialDmsCheckbox = new CheckBox("Allow Trial DMs");

            dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
            modeSpecificFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
            allowTrialDmsCheckbox.selectedProperty().addListener((obs, old, val) -> table.refresh());

            topFilterBar.getChildren().addAll(dmFilterCheckBox, modeSpecificFilterCheckbox, allowTrialDmsCheckbox);

        } else { // GROUP_ASSIGNMENT mode
            availableOnlyCheckbox = new CheckBox("Available");
            availableOnlyCheckbox.setSelected(true);
            modeSpecificFilterCheckbox = new CheckBox("Selected");

            availableOnlyCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
            modeSpecificFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());

            topFilterBar.getChildren().addAll(availableOnlyCheckbox, modeSpecificFilterCheckbox);
        }
    }

    @Override
    public void applyFilter() {
        String searchText = searchField.getText();
        House selectedHouse = houseFilterBox.getValue();

        filteredData.setPredicate(player -> {
            boolean textMatch = searchText == null || searchText.isEmpty() ||
                    player.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                    player.getUuid().toString().toLowerCase().contains(searchText.toLowerCase());

            boolean houseMatch = selectedHouse == null || player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);

            if (mode == RosterMode.PLAYER_MANAGEMENT) {
                boolean dmsOnly = dmFilterCheckBox.isSelected();
                boolean attendingOnly = modeSpecificFilterCheckbox.isSelected();
                boolean dmMatch = !dmsOnly || player.isDungeonMaster();
                boolean attendingMatch = !attendingOnly || attendingPlayers.containsKey(player.getUuid());
                return textMatch && houseMatch && dmMatch && attendingMatch;

            } else { // GROUP_ASSIGNMENT mode
                Player dmToExclude = (currentGroup != null) ? currentGroup.getDungeonMaster() : dmForNewGroup;
                if (dmToExclude != null && player.equals(dmToExclude)) return false;
                if (dmingPlayers != null && dmingPlayers.containsKey(player.getUuid())) return false;

                if (availableOnlyCheckbox.isSelected()) {
                    for (Group group : allGroups) {
                        if (currentGroup != null && currentGroup.equals(group)) continue;
                        if (group.getParty().containsKey(player.getUuid())) return false;
                    }
                }

                boolean selectedOnly = modeSpecificFilterCheckbox.isSelected();
                Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
                boolean selectedMatch = !selectedOnly || (partyMap != null && partyMap.containsKey(player.getUuid()));

                return textMatch && houseMatch && selectedMatch;
            }
        });

        refreshTable();
    }

    @Override
    public void updateRoster() {
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            sourceItems.setAll(PlayerStore.getInstance().getAllPlayers());
        } else {
            sourceItems.setAll(attendingPlayers.values());
        }
        applyFilter();
    }


    // --- Column Creation Logic ---

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
                        table.refresh();
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

    private TableColumn<Player, Boolean> createSelectedColumn() {
        TableColumn<Player, Boolean> col = new TableColumn<>("Selected");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
            boolean isSelected = partyMap != null && partyMap.containsKey(player.getUuid());
            SimpleBooleanProperty property = new SimpleBooleanProperty(isSelected);
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    if (onPlayerAddRequestHandler != null && !onPlayerAddRequestHandler.onPlayerAddRequest(player)) {
                        Platform.runLater(() -> property.set(false));
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

    // --- Public Setters & Getters for Group Assignment Mode ---

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

    // --- Other Methods ---

    public void showBlacklistedPlayers(Player editingPlayer) {
        topFilterBar.getParent().setDisable(true); // Disable the whole HBox
        interactiveColumn.setEditable(false);
        if (dmingColumn != null) dmingColumn.setEditable(false);
        filteredData.setPredicate(player -> editingPlayer.getBlacklist().containsKey(player.getUuid()));
        refreshTable();
    }

    public void showAllPlayers() {
        topFilterBar.getParent().setDisable(false);
        interactiveColumn.setEditable(true);
        if (dmingColumn != null) dmingColumn.setEditable(true);
        applyFilter();
    }

    private void setCheckboxCellStyle(TableColumn<Player, Boolean> column, boolean isDmingColumn) {
        column.setCellFactory(param -> new CheckBoxTableCell<Player, Boolean>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                this.setStyle("-fx-font-size: 1.5em;");
                this.setAlignment(Pos.CENTER);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    this.setGraphic(null);
                } else if (isDmingColumn) {
                    Player player = getTableRow().getItem();
                    boolean allowTrials = allowTrialDmsCheckbox != null && allowTrialDmsCheckbox.isSelected();
                    this.setGraphic(player.isDungeonMaster() || allowTrials ? this.getGraphic() : null);
                }
            }
        });
    }

    private int getRowsPerPage() { return 15; } // Simplified for row num column

    // Alias for clarity
    public void setOnPlayerDoubleClick(java.util.function.Consumer<Player> handler) {
        setOnItemDoubleClick(handler);
    }

    public Player getSelectedPlayer() {
        return getSelectedItem();
    }
}

