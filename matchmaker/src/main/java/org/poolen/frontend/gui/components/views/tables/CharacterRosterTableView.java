package org.poolen.frontend.gui.components.views.tables;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * A reusable component that displays a filterable, paginated table of characters.
 */
public class CharacterRosterTableView extends VBox {

    private final TableView<Character> characterTable;
    private final TextField searchField;
    private final Pagination pagination;
    private final ObservableList<Character> sourceCharacters;
    private final FilteredList<Character> filteredData;
    private final CheckBox showRetiredCheckBox;
    private int rowsPerPage = 15;

    public CharacterRosterTableView() {
        super(10);
        setPadding(new Insets(10));

        this.characterTable = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.sourceCharacters = FXCollections.observableArrayList();
        this.showRetiredCheckBox = new CheckBox("Show Retired");

        setupTableColumns();

        HBox filterBar = new HBox(10, searchField, showRetiredCheckBox);
        getChildren().addAll(filterBar, pagination);

        filteredData = new FilteredList<>(sourceCharacters, p -> true);
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        showRetiredCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        pagination.setPageFactory(this::createPageFactory);
        pagination.heightProperty().addListener((obs, oldH, newH) -> handleResize());

        updateRoster();
    }

    private void setupTableColumns() {
        characterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search by name...");

        TableColumn<Character, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Character, House> houseCol = new TableColumn<>("House");
        houseCol.setCellValueFactory(new PropertyValueFactory<>("house"));

        TableColumn<Character, String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(cellData -> {
            if (cellData.getValue().getPlayer() != null) {
                return new javafx.beans.property.SimpleStringProperty(cellData.getValue().getPlayer().getName());
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });

        TableColumn<Character, Boolean> mainCol = new TableColumn<>("Main");
        mainCol.setCellValueFactory(new PropertyValueFactory<>("main"));
        mainCol.setCellFactory(col -> createBooleanCell());

        TableColumn<Character, Boolean> retiredCol = new TableColumn<>("Retired");
        retiredCol.setCellValueFactory(new PropertyValueFactory<>("isRetired"));
        retiredCol.setCellFactory(col -> createBooleanCell());


        characterTable.getColumns().addAll(nameCol, houseCol, playerCol, mainCol, retiredCol);
    }

    private TableCell<Character, Boolean> createBooleanCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item ? "✔" : "✖");
                    setStyle(item ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
                }
            }
        };
    }

    public void updateRoster() {
        // In a real app, this would fetch from CharacterStore
        sourceCharacters.setAll(org.poolen.backend.db.store.CharacterStore.getInstance().getAllCharacters());
        applyFilter();
    }

    private void applyFilter() {
        String searchText = searchField.getText();
        boolean showRetired = showRetiredCheckBox.isSelected();

        filteredData.setPredicate(character -> {
            boolean retiredMatch = showRetired || !character.isRetired();
            boolean textMatch = searchText == null || searchText.isEmpty() ||
                    character.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                    (character.getPlayer() != null && character.getPlayer().getName().toLowerCase().contains(searchText.toLowerCase()));

            return retiredMatch && textMatch;
        });

        updatePageCount(filteredData.size());
        pagination.setPageFactory(this::createPageFactory);
    }

    public void setOnCharacterDoubleClick(Consumer<Character> onCharacterDoubleClick) {
        characterTable.setRowFactory(tv -> {
            TableRow<Character> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    onCharacterDoubleClick.accept(row.getItem());
                }
            });
            return row;
        });
    }

    public Character getSelectedCharacter() {
        return characterTable.getSelectionModel().getSelectedItem();
    }


    // --- Pagination and Resizing Logic ---

    private Node createPageFactory(int pageIndex) {
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        characterTable.setItems(FXCollections.observableArrayList(filteredData.subList(fromIndex, toIndex)));
        return characterTable;
    }

    private void updatePageCount(int totalItems) {
        int pageCount = (totalItems + rowsPerPage - 1) / rowsPerPage;
        if (pageCount == 0) pageCount = 1;
        pagination.setPageCount(pageCount);
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
                pagination.setPageFactory(this::createPageFactory);
            }
        }
    }
}
