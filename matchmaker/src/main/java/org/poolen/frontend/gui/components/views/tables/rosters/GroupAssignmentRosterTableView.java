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
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.interfaces.PlayerAddRequestHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Our other new, focused table, this one for Group Assignment.
 * It has its own special filters and columns, and a much more complex
 * filtering logic, which is now neatly contained in its own class!
 */
public class GroupAssignmentRosterTableView extends PlayerRosterTableView {

    private final Map<UUID, Player> attendingPlayers;
    private final Map<UUID, Player> dmingPlayers;

    private Group currentGroup;
    private Map<UUID, Player> partyForNewGroup;
    private Player dmForNewGroup;
    private List<Group> allGroups = new ArrayList<>();
    private PlayerAddRequestHandler onPlayerAddRequestHandler;

    // --- Filter Controls ---
    private CheckBox dmFilterCheckBox;
    private CheckBox selectedFilterCheckbox;
    private CheckBox availableOnlyCheckbox;

    public GroupAssignmentRosterTableView(Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers) {
        super();
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;
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

        TableColumn<Player, Boolean> selectedCol = createSelectedColumn();

        table.getColumns().addAll(nameCol, charCol, selectedCol);
    }

    @Override
    protected void setupFilters() {
        dmFilterCheckBox = new CheckBox("DMs");
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        selectedFilterCheckbox = new CheckBox("Selected");
        selectedFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        availableOnlyCheckbox = new CheckBox("Available");
        availableOnlyCheckbox.setSelected(true);
        availableOnlyCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        topFilterBar.getChildren().addAll(dmFilterCheckBox, availableOnlyCheckbox, selectedFilterCheckbox);
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

            // --- The complex logic specific to group assignment ---
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
            boolean selectedOnly = selectedFilterCheckbox.isSelected();
            Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
            boolean selectedMatch = !selectedOnly || (partyMap != null && partyMap.containsKey(player.getUuid()));

            return textMatch && dmMatch && selectedMatch;
        });
    }

    @Override
    public void updateRoster() {
        sourceItems.setAll(attendingPlayers.values());
        applyFilter();
    }

    // --- Column Creation ---

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
        setCheckboxCellStyle(col);
        return col;
    }

    private void setCheckboxCellStyle(TableColumn<Player, Boolean> column) {
        column.setCellFactory(param -> {
            CheckBoxTableCell<Player, Boolean> cell = new CheckBoxTableCell<>();
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
    }

    // --- Getters and Setters for Parent Tab ---

    public void displayForGroup(Group group) {
        this.currentGroup = group;
        this.partyForNewGroup = null;
        this.dmForNewGroup = null;
        table.refresh();
        applyFilter(); // Apply filter to immediately hide players in other groups if needed
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

