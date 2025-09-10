package org.poolen.frontend.gui.components.tables;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

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
    private final FilteredList<Player> filteredData; // Make this a member variable
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private Map<UUID, Player> attendingPlayers = new HashMap<>();
    private int rowsPerPage = 15;
    private List<Player> roster;

    // --- The new filter controls! ---
    private final ComboBox<House> houseFilterBox;
    private final CheckBox dmFilterCheckBox;
    private final CheckBox attendingFilterCheckbox;


    public PlayerRosterTableView(Map<UUID, Player> attendingPlayers) {
        super(10); // Spacing for the VBox

        this.playerTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.allPlayers = FXCollections.observableArrayList();
        this.houseFilterBox = new ComboBox<>();
        this.dmFilterCheckBox = new CheckBox("Show DMs Only");
        this.attendingFilterCheckbox = new CheckBox("Show Attending Players Only");

        // --- Layout and Resizing ---
        VBox.setVgrow(this.pagination, Priority.ALWAYS);
        this.playerTable.setMaxWidth(Double.MAX_VALUE);
        this.setMinWidth(420);
        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search by name or UUID...");


        // --- Table Columns (created once) ---
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

        playerTable.getColumns().addAll(nameCol, dmCol, charCol);

        // --- Filtering & Smart Pagination Magic! ---
        this.filteredData = new FilteredList<>(allPlayers, p -> true);

        // Setup filter controls
        houseFilterBox.getItems().add(null); // for "All Houses"
        houseFilterBox.getItems().addAll(House.values());
        houseFilterBox.setPromptText("Filter by House");

        // Listen for changes on all filter controls
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
                double rowHeight = 24.0;

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

        setRoster(playerStore.getAllPlayers());
        updateRoster();

        // Create a new filter panel for our beautiful new controls
        HBox filterPanel = new HBox(10, houseFilterBox, attendingFilterCheckbox, dmFilterCheckBox);
        filterPanel.setAlignment(Pos.CENTER_LEFT);

        this.getChildren().addAll(new Label("Current Roster"), filterPanel, searchField, pagination);
    }

    /**
     * A single, beautiful method to apply all our filters at once!
     */
    private void applyFilter() {
        House selectedHouse = houseFilterBox.getValue();
        boolean dmsOnly = dmFilterCheckBox.isSelected();
        boolean attendingOnly = attendingFilterCheckbox.isSelected();
        String searchText = searchField.getText();

        if(attendingOnly) {
            setRoster(attendingPlayers.values().stream().toList());
        } else {
            setRoster(playerStore.getAllPlayers());
        }
        updateRoster();

        filteredData.setPredicate(player -> {
            // Text search filter
            boolean textMatch = true;
            if (searchText != null && !searchText.isEmpty()) {
                String lowerCaseFilter = searchText.toLowerCase();
                textMatch = player.getName().toLowerCase().contains(lowerCaseFilter) ||
                        player.getUuid().toString().toLowerCase().contains(lowerCaseFilter);
            }

            // DM filter
            boolean dmMatch = !dmsOnly || player.isDungeonMaster();

            // House filter
            boolean houseMatch = true;
            if (selectedHouse != null) {
                houseMatch = player.getCharacters().stream().anyMatch(c -> c.getHouse() == selectedHouse);
            }

            return textMatch && dmMatch && houseMatch;
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
        playerTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Player selectedPlayer = playerTable.getSelectionModel().getSelectedItem();
                if (selectedPlayer != null) {
                    onPlayerDoubleClick.accept(selectedPlayer);
                }
            }
        });
    }

    public Player getSelectedPlayer() {
        return playerTable.getSelectionModel().getSelectedItem();
    }

    public void updateRoster() {
        allPlayers.setAll(roster);
    }

    public void setRoster(List<Player> roster) {
        this.roster = roster;
    }
}
