package org.poolen.frontend.gui.components.tables;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A highly reusable JavaFX component that displays a filterable and paginated table of players.
 * It can operate in two modes: PLAYER_MANAGEMENT and GROUP_ASSIGNMENT.
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
    // A beautiful little place to remember how you like things sorted, just like you wanted!
    private List<TableColumn<Player, ?>> savedSortOrder = new ArrayList<>();

    // --- Mode-specific variables ---
    private final RosterMode mode;
    private final Map<UUID, Player> attendingPlayers;
    private Group currentGroup; // Used in GROUP_ASSIGNMENT mode (editing)
    private Player dmForNewGroup; // Used in GROUP_ASSIGNMENT mode (creating)
    private TableColumn<Player, Boolean> interactiveColumn; // Attending or Selected
    private CheckBox modeSpecificFilterCheckbox; // Attending or Selected filter
    private final ComboBox<House> houseFilterBox;
    private final CheckBox dmFilterCheckBox;


    public PlayerRosterTableView(RosterMode mode, Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super(10);
        this.setPadding(new Insets(10));
        this.mode = mode;
        this.attendingPlayers = attendingPlayers;

        this.playerTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.sourcePlayers = FXCollections.observableArrayList();
        this.houseFilterBox = new ComboBox<>();
        this.dmFilterCheckBox = new CheckBox("Show DMs Only");

        VBox.setVgrow(this.pagination, Priority.ALWAYS);
        this.playerTable.setMaxWidth(Double.MAX_VALUE);
        this.setMinWidth(420);
        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search by name or UUID...");
        playerTable.setEditable(true);

        // --- Column Definitions ---
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
                if (empty) {
                    setText(null);
                } else {
                    int pageIndex = pagination.getCurrentPageIndex();
                    int rowIndex = getIndex();
                    setText(String.valueOf((pageIndex * rowsPerPage) + rowIndex + 1));
                }
            }
        });

        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Player, Boolean> dmCol = new TableColumn<>("DM");
        dmCol.setCellValueFactory(new PropertyValueFactory<>("dungeonMaster"));
        TableColumn<Player, String> charCol = new TableColumn<>("Characters");
        charCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getCharacters().stream()
                        .map(c -> c.getHouse().toString())
                        .collect(Collectors.joining(", "))
        ));

        // --- Filter Setup ---
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

        // --- Mode-Specific Setup ---
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            setupForPlayerManagement(onPlayerListChanged, filterPanel);
            playerTable.getColumns().addAll(rowNumCol, nameCol, dmCol, charCol, interactiveColumn);
        } else { // GROUP_ASSIGNMENT
            setupForGroupAssignment(filterPanel);
            playerTable.getColumns().addAll(rowNumCol, nameCol, charCol, interactiveColumn);
        }

        // --- Universal Listeners ---
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        houseFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        if (modeSpecificFilterCheckbox != null) {
            modeSpecificFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        }

        filteredData.addListener((javafx.collections.ListChangeListener.Change<? extends Player> c) -> {
            updatePageCount(filteredData.size());
            pagination.setPageFactory(createPageFactory(filteredData));
        });

        pagination.setPageFactory(createPageFactory(filteredData));
        pagination.heightProperty().addListener((obs, oldH, newH) -> handleResize());

        updateRoster();

        this.getChildren().addAll(new Label("Roster"), filterPanel, searchField, pagination);
    }

    private void setupForPlayerManagement(Runnable onPlayerListChanged, HBox filterPanel) {
        modeSpecificFilterCheckbox = new CheckBox("Show Attending Only");
        filterPanel.getChildren().add(1, dmFilterCheckBox);
        filterPanel.getChildren().add(2, modeSpecificFilterCheckbox);
        interactiveColumn = createAttendingColumn(onPlayerListChanged);
    }

    private void setupForGroupAssignment(HBox filterPanel) {
        modeSpecificFilterCheckbox = new CheckBox("Show Selected Only");
        filterPanel.getChildren().add(1, modeSpecificFilterCheckbox);
        interactiveColumn = createSelectedColumn();
    }

    private TableColumn<Player, Boolean> createAttendingColumn(Runnable onPlayerListChanged) {
        TableColumn<Player, Boolean> col = new TableColumn<>("Attending");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(attendingPlayers.containsKey(player.getUuid()));
            property.addListener((obs, was, isNow) -> {
                if (isNow) attendingPlayers.put(player.getUuid(), player);
                else attendingPlayers.remove(player.getUuid());
                onPlayerListChanged.run();
            });
            return property;
        });
        setCheckboxCellStyle(col);
        return col;
    }

    private TableColumn<Player, Boolean> createSelectedColumn() {
        TableColumn<Player, Boolean> col = new TableColumn<>("Selected");
        col.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            boolean isSelected = currentGroup != null && currentGroup.getParty().containsKey(player.getUuid());
            SimpleBooleanProperty property = new SimpleBooleanProperty(isSelected);
            property.addListener((obs, was, isNow) -> {
                if (currentGroup != null) {
                    if (isNow) currentGroup.addPartyMember(player);
                    else currentGroup.removePartyMember(player);
                }
            });
            return property;
        });
        setCheckboxCellStyle(col);
        return col;
    }

    public void displayForGroup(Group group) {
        this.currentGroup = group;
        this.dmForNewGroup = null;
        applyFilter();
        playerTable.refresh();
    }

    public void setDmForNewGroup(Player dm) {
        this.currentGroup = null;
        this.dmForNewGroup = dm;
        applyFilter();
        playerTable.refresh();
    }

    public void showBlacklistedPlayers(Player editingPlayer) {
        getFilterPanel().getChildren().forEach(node -> node.setDisable(true));
        searchField.setDisable(true);
        interactiveColumn.setEditable(false);
        filteredData.setPredicate(player -> editingPlayer.getBlacklist().containsKey(player.getUuid()));
    }

    public void showAllPlayers() {
        getFilterPanel().getChildren().forEach(node -> node.setDisable(false));
        searchField.setDisable(false);
        interactiveColumn.setEditable(true);
        applyFilter();
    }

    private void applyFilter() {
        House selectedHouse = houseFilterBox.getValue();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        boolean attendingOnly = (mode == RosterMode.PLAYER_MANAGEMENT && modeSpecificFilterCheckbox.isSelected());
        boolean selectedOnly = (mode == RosterMode.GROUP_ASSIGNMENT && modeSpecificFilterCheckbox.isSelected());
        String searchText = searchField.getText();

        filteredData.setPredicate(player -> {
            boolean textMatch = true;
            if (searchText != null && !searchText.isEmpty()) {
                String lowerCaseFilter = searchText.toLowerCase();
                textMatch = player.getName().toLowerCase().contains(lowerCaseFilter) ||
                        player.getUuid().toString().toLowerCase().contains(lowerCaseFilter);
            }

            boolean dmMatch = !dmsOnly || player.isDungeonMaster();
            boolean attendingMatch = !attendingOnly || attendingPlayers.containsKey(player.getUuid());
            boolean selectedMatch = !selectedOnly || (currentGroup != null && currentGroup.getParty().containsKey(player.getUuid()));
            boolean houseMatch = true;
            if (selectedHouse != null) {
                houseMatch = player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);
            }

            if (mode == RosterMode.GROUP_ASSIGNMENT) {
                Player dmToExclude = (currentGroup != null) ? currentGroup.getDungeonMaster() : dmForNewGroup;

                if (dmToExclude != null && player.equals(dmToExclude)) {
                    return false;
                }
                return textMatch && houseMatch && selectedMatch;
            }

            return textMatch && dmMatch && houseMatch && attendingMatch;
        });
    }

    /**
     * Updates the roster with fresh data and re-applies the last used sort order.
     */
    public void updateRoster() {
        // --- Save the current sort order ---
        savedSortOrder = new ArrayList<>(playerTable.getSortOrder());

        // --- Update the source data ---
        if (mode == RosterMode.PLAYER_MANAGEMENT) {
            sourcePlayers.setAll(playerStore.getAllPlayers());
        } else { // GROUP_ASSIGNMENT
            sourcePlayers.setAll(attendingPlayers.values());
        }

        // --- Restore the saved sort order ---
        if (!savedSortOrder.isEmpty()) {
            playerTable.getSortOrder().setAll(savedSortOrder);
            playerTable.sort(); // This performs the sort on the currently visible page
        }
    }

    private HBox getFilterPanel() {
        return (HBox) this.getChildren().get(1);
    }

    private Callback<Integer, Node> createPageFactory(List<Player> data) {
        return pageIndex -> {
            int fromIndex = pageIndex * this.rowsPerPage;
            int toIndex = Math.min(fromIndex + this.rowsPerPage, data.size());
            playerTable.setItems(FXCollections.observableArrayList(data.subList(fromIndex, toIndex)));
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

    private void setCheckboxCellStyle(TableColumn<Player, Boolean> column) {
        Callback<TableColumn<Player, Boolean>, TableCell<Player, Boolean>> cellFactory = CheckBoxTableCell.forTableColumn(column);
        column.setCellFactory(col -> {
            TableCell<Player, Boolean> cell = cellFactory.call(col);
            cell.setStyle("-fx-font-size: 1.5em;");
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
    }
}

