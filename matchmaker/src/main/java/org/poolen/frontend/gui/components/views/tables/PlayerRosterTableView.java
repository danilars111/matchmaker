package org.poolen.frontend.gui.components.views.tables;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.interfaces.PlayerAddRequestHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A highly reusable JavaFX component that displays a filterable and paginated table of players.
 */
public class PlayerRosterTableView extends VBox {

    public enum RosterMode {
        PLAYER_MANAGEMENT,
        GROUP_ASSIGNMENT
    }

    private final TableView<Player> playerTable;
    private final TextField searchField;
    private final Pagination pagination;
    private final ObservableList<Player> sourcePlayers;
    private final FilteredList<Player> filteredData;
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private int rowsPerPage = 15;

    private TableColumn<Player, ?> sortColumn = null;
    private SortType sortType = null;

    private final RosterMode mode;
    private final Map<UUID, Player> attendingPlayers;
    private final Map<UUID, Player> dmingPlayers;
    private Group currentGroup;
    private Map<UUID, Player> partyForNewGroup;
    private Player dmForNewGroup;
    private TableColumn<Player, Boolean> interactiveColumn;
    private TableColumn<Player, Boolean> dmingColumn; // The new column!
    private CheckBox modeSpecificFilterCheckbox;
    private CheckBox availableOnlyCheckbox;
    private CheckBox allowTrialDmsCheckbox;
    private List<Group> allGroups = new ArrayList<>();
    private final ComboBox<House> houseFilterBox;
    private final CheckBox dmFilterCheckBox;
    private PlayerAddRequestHandler onPlayerAddRequestHandler;

    public PlayerRosterTableView(RosterMode mode, Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        super(10);
        this.setPadding(new Insets(10));
        this.mode = mode;
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;

        this.playerTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.sourcePlayers = FXCollections.observableArrayList();
        this.houseFilterBox = new ComboBox<>();
        this.dmFilterCheckBox = new CheckBox("DMs");

        VBox.setVgrow(this.pagination, Priority.ALWAYS);
        this.playerTable.setMaxWidth(Double.MAX_VALUE);
        this.setMinWidth(420);
        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search by name or UUID...");
        playerTable.setEditable(true);

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
                setText(empty ? null : String.valueOf((pagination.getCurrentPageIndex() * rowsPerPage) + getIndex() + 1));
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

        houseFilterBox.getItems().add(null);
        houseFilterBox.getItems().addAll(House.values());
        houseFilterBox.setPromptText("Filter by House");

        Button refreshButton = new Button("🔄");
        refreshButton.setOnAction(e -> updateRoster());

        HBox filterPanel = new HBox(10);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        filterPanel.getChildren().addAll(houseFilterBox, spacer, refreshButton);
        filterPanel.setAlignment(Pos.CENTER_LEFT);

        this.filteredData = new FilteredList<>(sourcePlayers, p -> true);

        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            setupForPlayerManagement(onPlayerListChanged, filterPanel);
            playerTable.getColumns().addAll(rowNumCol, nameCol, charCol, interactiveColumn, dmingColumn);
        } else {
            setupForGroupAssignment(filterPanel);
            playerTable.getColumns().addAll(rowNumCol, nameCol, charCol, interactiveColumn);
        }

        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        houseFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        if (modeSpecificFilterCheckbox != null)
            modeSpecificFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        if (availableOnlyCheckbox != null)
            availableOnlyCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        if (allowTrialDmsCheckbox != null)
            allowTrialDmsCheckbox.selectedProperty().addListener((obs, old, val) -> playerTable.refresh());


        filteredData.addListener((javafx.collections.ListChangeListener.Change<? extends Player> c) -> {
            updatePageCount(filteredData.size());
            pagination.setPageFactory(createPageFactory(filteredData));
        });

        pagination.setPageFactory(createPageFactory(filteredData));
        pagination.heightProperty().addListener((obs, oldH, newH) -> handleResize());
        updateRoster();
        this.getChildren().addAll(filterPanel, searchField, pagination);
    }

    private void setupForPlayerManagement(Runnable onPlayerListChanged, HBox filterPanel) {
        modeSpecificFilterCheckbox = new CheckBox("Attending");
        allowTrialDmsCheckbox = new CheckBox("Allow Trial DMs");
        filterPanel.getChildren().add(1, dmFilterCheckBox);
        filterPanel.getChildren().add(2, modeSpecificFilterCheckbox);
        filterPanel.getChildren().add(3, allowTrialDmsCheckbox);
        interactiveColumn = createAttendingColumn(onPlayerListChanged);
        dmingColumn = createDmingColumn(onPlayerListChanged);
    }

    private void setupForGroupAssignment(HBox filterPanel) {
        modeSpecificFilterCheckbox = new CheckBox("Selected");
        availableOnlyCheckbox = new CheckBox("Available");
        availableOnlyCheckbox.setSelected(true);
        filterPanel.getChildren().add(1, availableOnlyCheckbox);
        filterPanel.getChildren().add(2, modeSpecificFilterCheckbox);
        interactiveColumn = createSelectedColumn();
    }

    private TableColumn<Player, Boolean> createAttendingColumn(Runnable onPlayerListChanged) {
        TableColumn<Player, Boolean> col = new TableColumn<>("Attending");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(attendingPlayers.containsKey(player.getUuid()));
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    attendingPlayers.put(player.getUuid(), player);
                } else {
                    attendingPlayers.remove(player.getUuid());
                    // Also remove them from DMing if they are no longer attending
                    if (dmingPlayers.containsKey(player.getUuid())) {
                        dmingPlayers.remove(player.getUuid());
                        playerTable.refresh();
                    }
                }
                onPlayerListChanged.run();
            });
            return property;
        });
        setCheckboxCellStyle(col, false);
        return col;
    }

    private TableColumn<Player, Boolean> createDmingColumn(Runnable onPlayerListChanged) {
        TableColumn<Player, Boolean> col = new TableColumn<>("DMing");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(dmingPlayers.containsKey(player.getUuid()));
            property.addListener((obs, was, isNow) -> {
                if (isNow) {
                    dmingPlayers.put(player.getUuid(), player);
                    // Also add them to attending if they are now DMing
                    if (!attendingPlayers.containsKey(player.getUuid())) {
                        attendingPlayers.put(player.getUuid(), player);
                        playerTable.refresh();
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
                    if (onPlayerAddRequestHandler != null) {
                        boolean success = onPlayerAddRequestHandler.onPlayerAddRequest(player);
                        if (!success) {
                            Platform.runLater(() -> property.set(false));
                        }
                    }
                } else {
                    if (currentGroup != null) {
                        currentGroup.removePartyMember(player);
                        playerTable.refresh();
                    } else if (partyForNewGroup != null) {
                        partyForNewGroup.remove(player.getUuid());
                    }
                }
            });
            return property;
        });
        setCheckboxCellStyle(col, false);
        return col;
    }

    public void displayForGroup(Group group) {
        this.currentGroup = group;
        this.partyForNewGroup = null;
        this.dmForNewGroup = null;
        playerTable.refresh();
    }

    public void setPartyForNewGroup(Map<UUID, Player> partyMap) {
        this.currentGroup = null;
        this.partyForNewGroup = partyMap;
        playerTable.refresh();
    }

    public void setDmForNewGroup(Player dm) {
        this.dmForNewGroup = dm;
        applyFilter();
        playerTable.refresh();
    }

    public void setAllGroups(List<Group> groups) {
        this.allGroups = groups;
        applyFilter();
    }

    public void setOnPlayerAddRequest(PlayerAddRequestHandler handler) {
        this.onPlayerAddRequestHandler = handler;
    }

    public void showBlacklistedPlayers(Player editingPlayer) {
        getFilterPanel().getChildren().forEach(node -> node.setDisable(true));
        searchField.setDisable(true);
        interactiveColumn.setEditable(false);
        dmingColumn.setEditable(false);
        filteredData.setPredicate(player -> editingPlayer.getBlacklist().containsKey(player.getUuid()));
    }

    public void showAllPlayers() {
        getFilterPanel().getChildren().forEach(node -> node.setDisable(false));
        searchField.setDisable(false);
        interactiveColumn.setEditable(true);
        dmingColumn.setEditable(true);
        applyFilter();
    }

    private void applyFilter() {
        House selectedHouse = houseFilterBox.getValue();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        boolean attendingOnly = (mode == RosterMode.PLAYER_MANAGEMENT && modeSpecificFilterCheckbox.isSelected());
        String searchText = searchField.getText();

        if (!playerTable.getSortOrder().isEmpty()) {
            sortColumn = (TableColumn<Player, ?>) playerTable.getSortOrder().get(0);
            sortType = sortColumn.getSortType();
        }

        filteredData.setPredicate(player -> {
            boolean textMatch = searchText == null || searchText.isEmpty() ||
                    player.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                    player.getUuid().toString().toLowerCase().contains(searchText.toLowerCase());

            boolean dmMatch = !dmsOnly || player.isDungeonMaster();
            boolean attendingMatch = !attendingOnly || attendingPlayers.containsKey(player.getUuid());
            boolean houseMatch = selectedHouse == null || player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);

            if (mode == RosterMode.GROUP_ASSIGNMENT) {
                // --- Consolidated "Is Unavailable" Logic ---
                boolean isUnavailable = false;

                // Unavailable if they are the DM for the group being edited/created
                Player dmToExclude = (currentGroup != null) ? currentGroup.getDungeonMaster() : dmForNewGroup;
                if (dmToExclude != null && player.equals(dmToExclude)) {
                    isUnavailable = true;
                }

                // If "Available Only" is checked, check all other reasons for unavailability
                if (!isUnavailable && availableOnlyCheckbox.isSelected()) {
                    // Unavailable if they are on the master DM list for the week
                    if (dmingPlayers != null && dmingPlayers.containsKey(player.getUuid())) {
                        isUnavailable = true;
                    }
                    // Unavailable if they are in another group's party
                    if (!isUnavailable) {
                        for (Group group : allGroups) {
                            if (currentGroup != null && currentGroup.equals(group)) continue;
                            if (group.getParty().containsKey(player.getUuid())) {
                                isUnavailable = true;
                                break;
                            }
                        }
                    }
                }

                if (isUnavailable) return false;
                // --- End of Unavailability Logic ---


                boolean selectedOnly = modeSpecificFilterCheckbox.isSelected();
                Map<UUID, Player> partyMap = (currentGroup != null) ? currentGroup.getParty() : partyForNewGroup;
                boolean selectedMatch = !selectedOnly || (partyMap != null && partyMap.containsKey(player.getUuid()));


                return textMatch && houseMatch && selectedMatch;
            }

            return textMatch && dmMatch && houseMatch && attendingMatch;
        });

        if (sortColumn != null) {
            playerTable.getSortOrder().add(sortColumn);
            sortColumn.setSortType(sortType);
            sortColumn.setSortable(true);
        }
        playerTable.sort();
    }

    public void updateRoster() {
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            sourcePlayers.setAll(playerStore.getAllPlayers());
        } else {
            sourcePlayers.setAll(attendingPlayers.values());
        }
    }

    private HBox getFilterPanel() {
        return (HBox) this.getChildren().get(0);
    }

    private Callback<Integer, Node> createPageFactory(List<Player> data) {
        return pageIndex -> {
            int fromIndex = pageIndex * this.rowsPerPage;
            int toIndex = Math.min(fromIndex + this.rowsPerPage, data.size());
            playerTable.setItems(FXCollections.observableArrayList(data.subList(fromIndex, toIndex)));
            playerTable.refresh();
            return playerTable;
        };
    }

    private void updatePageCount(int totalItems) {
        int pageCount = (totalItems + this.rowsPerPage - 1) / this.rowsPerPage;
        if (pageCount == 0) pageCount = 1;
        int currentPage = pagination.getCurrentPageIndex();
        pagination.setPageCount(pageCount);
        if (currentPage >= pageCount) {
            pagination.setCurrentPageIndex(pageCount - 1);
        }
    }

    public void setOnPlayerDoubleClick(Consumer<Player> onPlayerDoubleClick) {
        playerTable.setRowFactory(tv -> {
            TableRow<Player> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Player rowData = row.getItem();
                    onPlayerDoubleClick.accept(rowData);
                }
            });
            return row;
        });
    }

    public Player getSelectedPlayer() {
        return playerTable.getSelectionModel().getSelectedItem();
    }

    private void handleResize() {
        double newHeight = pagination.getHeight();
        if (newHeight > 0) {
            double headerHeight = 30.0;
            double indicatorHeight = 30.0;
            double rowHeight = 26.0;
            double availableTableHeight = newHeight - indicatorHeight;
            int newRows = (int) ((availableTableHeight - headerHeight) / rowHeight);

            if (newRows > 0 && newRows != this.rowsPerPage) {
                this.rowsPerPage = newRows;
                updatePageCount(filteredData.size());
                pagination.setPageFactory(createPageFactory(filteredData));
            }
        }
    }

    private void setCheckboxCellStyle(TableColumn<Player, Boolean> column, boolean isDmingColumn) {
        column.setCellFactory(param -> new CheckBoxTableCell<Player, Boolean>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);

                // Default style for all cells in the column
                this.setStyle("-fx-font-size: 1.5em;");
                this.setAlignment(Pos.CENTER);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    // For empty rows, make sure nothing is visible
                    this.setGraphic(null);
                } else if (isDmingColumn) {
                    Player player = getTableRow().getItem();
                    boolean allowTrials = allowTrialDmsCheckbox != null && allowTrialDmsCheckbox.isSelected();
                    boolean shouldBeVisible = player.isDungeonMaster() || allowTrials;

                    // The checkbox is the graphic. Show/hide it by controlling the graphic property.
                    if (!shouldBeVisible) {
                        this.setGraphic(null);
                    } else {
                        // The super.updateItem call already added the checkbox.
                        // We just need to make sure it's not null if it should be visible.
                        // This call re-applies the default graphic.
                        this.setGraphic(this.getGraphic());
                    }
                }
            }
        });
    }
}

