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
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component that displays a filterable and paginated table of all players from the PlayerStore.
 */
public class PlayerRosterTableView extends VBox {

    private final TableView<Player> playerTable;
    private final TextField searchField;
    private final Pagination pagination;
    private final ObservableList<Player> allPlayers;
    private final FilteredList<Player> filteredData;
    private final Map<UUID, Player> attendingPlayers;
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private int rowsPerPage = 15;

    private final ComboBox<House> houseFilterBox;
    private final CheckBox dmFilterCheckBox;
    private final CheckBox attendingFilterCheckbox;
    private final TableColumn<Player, Boolean> attendingCol; // Now a member variable!


    public PlayerRosterTableView(Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super(10); // Spacing for the VBox
        this.setPadding(new Insets(10));

        this.playerTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.allPlayers = FXCollections.observableArrayList();
        this.houseFilterBox = new ComboBox<>();
        this.dmFilterCheckBox = new CheckBox("Show DMs Only");
        this.attendingFilterCheckbox = new CheckBox("Show Attending Only");
        this.attendingPlayers = attendingPlayers;

        VBox.setVgrow(this.pagination, Priority.ALWAYS);
        this.playerTable.setMaxWidth(Double.MAX_VALUE);
        this.setMinWidth(420);
        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search by name or UUID...");
        playerTable.setEditable(true);


        // --- The "Attending" Checkbox Column! ---
        this.attendingCol = new TableColumn<>("Attending");

        attendingCol.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            SimpleBooleanProperty attendingProperty = new SimpleBooleanProperty(attendingPlayers.containsKey(player.getUuid()));
            attendingProperty.addListener((obs, wasAttending, isNowAttending) -> {
                if (isNowAttending) {
                    attendingPlayers.put(player.getUuid(), player);
                } else {
                    attendingPlayers.remove(player.getUuid());
                }
                onPlayerListChanged.run();
            });
            return attendingProperty;
        });

        Callback<TableColumn<Player, Boolean>, TableCell<Player, Boolean>> cellFactory = CheckBoxTableCell.forTableColumn(attendingCol);
        attendingCol.setCellFactory(col -> {
            TableCell<Player, Boolean> cell = cellFactory.call(col);
            cell.setStyle("-fx-font-size: 1.5em;");
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        attendingCol.setEditable(true);


        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Player, Boolean> dmCol = new TableColumn<>("DM");
        dmCol.setCellValueFactory(new PropertyValueFactory<>("dungeonMaster"));

        TableColumn<Player, String> charCol = new TableColumn<>("Characters");
        charCol.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            String characters = player.getCharacters().stream()
                    .map(c -> c.getHouse().toString())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(characters);
        });

        playerTable.getColumns().addAll(nameCol, dmCol, charCol, attendingCol);

        this.filteredData = new FilteredList<>(allPlayers, p -> true);

        houseFilterBox.getItems().add(null);
        houseFilterBox.getItems().addAll(House.values());
        houseFilterBox.setPromptText("Filter by House");

        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        houseFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());
        dmFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        attendingFilterCheckbox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        filteredData.addListener((javafx.collections.ListChangeListener.Change<? extends Player> c) -> {
            updatePageCount(filteredData.size());
            pagination.setPageFactory(null);
            pagination.setPageFactory(createPageFactory(filteredData));
        });

        pagination.setPageFactory(createPageFactory(filteredData));

        pagination.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (newHeight.doubleValue() > 0) {
                double headerHeight = 30.0;
                double indicatorHeight = 30.0;
                double rowHeight = 26.0;
                double availableTableHeight = newHeight.doubleValue() - indicatorHeight;
                int newRows = (int) ((availableTableHeight - headerHeight) / rowHeight);

                if (newRows > 0 && newRows != this.rowsPerPage) {
                    this.rowsPerPage = newRows;
                    updatePageCount(filteredData.size());
                    pagination.setPageFactory(null);
                    pagination.setPageFactory(createPageFactory(filteredData));
                }
            }
        });

        updateRoster();

        Button refreshButton = new Button("🔄");
        refreshButton.setOnAction(e -> updateRoster());

        // --- filter panel layout! ---
        HBox filterPanel = new HBox(10);
        Region spacer = new Region(); // Our clever little invisible spacer
        HBox.setHgrow(spacer, Priority.ALWAYS); // It grows to push the button to the right
        filterPanel.getChildren().addAll(houseFilterBox, attendingFilterCheckbox, dmFilterCheckBox, spacer, refreshButton);
        filterPanel.setAlignment(Pos.CENTER_LEFT);

        this.getChildren().addAll(new Label("Current Roster"), filterPanel, searchField, pagination);
    }

    /**
     * A new method to show only players on a specific player's blacklist.
     * @param editingPlayer The player whose blacklist we want to see.
     */
    public void showBlacklistedPlayers(Player editingPlayer) {
        searchField.setDisable(true);
        houseFilterBox.setDisable(true);
        dmFilterCheckBox.setDisable(true);
        attendingFilterCheckbox.setDisable(true);
        this.attendingCol.setEditable(false); // Disable the checkbox column!

        filteredData.setPredicate(player -> editingPlayer.getBlacklist().containsKey(player.getUuid()));
    }

    /**
     * A new method to restore the view to the standard filters.
     */
    public void showAllPlayers() {
        searchField.setDisable(false);
        houseFilterBox.setDisable(false);
        dmFilterCheckBox.setDisable(false);
        attendingFilterCheckbox.setDisable(false);
        this.attendingCol.setEditable(true); // Re-enable the checkbox column!
        applyFilter();
    }

    private void applyFilter() {
        House selectedHouse = houseFilterBox.getValue();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        boolean attendingOnly = attendingFilterCheckbox.isSelected();
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
            boolean houseMatch = true;
            if (selectedHouse != null) {
                houseMatch = player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);
            }
            return textMatch && dmMatch && houseMatch && attendingMatch;
        });
    }

    private Callback<Integer, Node> createPageFactory(FilteredList<Player> data) {
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
                // The event only fires if a non-empty row is double-clicked.
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

    public void updateRoster() {
        allPlayers.setAll(playerStore.getAllPlayers());
    }
}

