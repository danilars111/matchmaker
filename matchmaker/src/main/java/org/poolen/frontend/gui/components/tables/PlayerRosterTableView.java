package org.poolen.frontend.gui.components.tables;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
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
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private int rowsPerPage = 15;

    public PlayerRosterTableView() {
        super(10); // Spacing for the VBox

        this.playerTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.allPlayers = FXCollections.observableArrayList();

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
                    .map(c -> c.getHouse().toString().substring(0, 1))
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(characters);
        });

        playerTable.getColumns().addAll(nameCol, dmCol, charCol);

        // --- Filtering & Smart Pagination Magic! ---
        FilteredList<Player> filteredData = new FilteredList<>(allPlayers, p -> true);

        // The search now happens in real-time, on every keystroke!
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(player -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                if (player.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (player.getUuid().toString().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        filteredData.addListener((javafx.collections.ListChangeListener.Change<? extends Player> c) -> {
            updatePageCount(filteredData.size());
            pagination.setPageFactory(null);
            pagination.setPageFactory(createPageFactory(filteredData));
        });

        // Set the initial page factory
        pagination.setPageFactory(createPageFactory(filteredData));

        // The resize logic now happens in real-time, on every pixel change!
        pagination.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (newHeight.doubleValue() > 0) {
                double headerHeight = 35.0;
                double indicatorHeight = 25.0;
                double rowHeight = 25.0;

                double availableTableHeight = newHeight.doubleValue() - indicatorHeight;
                int newRows = (int) ((availableTableHeight - headerHeight) / rowHeight);

                if (newRows > 0 && newRows != this.rowsPerPage) {
                    this.rowsPerPage = newRows;
                    updatePageCount(filteredData.size());
                    // Force the pagination to redraw with the new row count!
                    pagination.setPageFactory(null);
                    pagination.setPageFactory(createPageFactory(filteredData));
                }
            }
        });

        updateRoster();

        this.getChildren().addAll(new Label("Current Roster"), searchField, pagination);
    }

    /**
     * A beautiful helper method to create our page factory, keeping the code lovely and tidy.
     * @param data The filtered list of players to paginate.
     * @return A Callback to be used by the Pagination control.
     */
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
        allPlayers.setAll(playerStore.getAllPlayers());
    }
}
