package org.poolen.frontend.gui.components.views.tables;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;

import java.util.function.Consumer;

/**
 * An abstract base class for creating reusable, paginated, and filterable table views.
 * @param <T> The type of the items that will populate the table.
 */
public abstract class BaseRosterTableView<T> extends VBox {

    protected final TableView<T> table;
    protected final TextField searchField;
    protected final Pagination pagination;
    protected final HBox topFilterBar; // For subclass-specific filters
    protected final ComboBox<House> houseFilterBox;
    protected final Button refreshButton;

    protected final ObservableList<T> sourceItems;
    protected final FilteredList<T> filteredData;

    private int rowsPerPage = 15;

    public BaseRosterTableView() {
        super(10);
        setPadding(new Insets(10));

        this.table = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.topFilterBar = new HBox(10);
        this.houseFilterBox = new ComboBox<>();
        this.refreshButton = new Button("🔄");

        this.sourceItems = FXCollections.observableArrayList();
        this.filteredData = new FilteredList<>(sourceItems, p -> true);

        setupCommonUI();

        // Add listeners now that components are ready
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        pagination.setPageFactory(this::createPageFactory);
        pagination.heightProperty().addListener((obs, oldH, newH) -> handleResize());
    }

    private void setupCommonUI() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search...");

        VBox filterContainer = new VBox(10);
        HBox mainFilterRow = new HBox(10);

        houseFilterBox.getItems().add(null); // Allow "All Houses"
        houseFilterBox.getItems().addAll(House.values());
        houseFilterBox.setPromptText("Filter by House");
        houseFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());

        refreshButton.setOnAction(e -> updateRoster());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        mainFilterRow.getChildren().addAll(houseFilterBox, topFilterBar, spacer, refreshButton);
        filterContainer.getChildren().addAll(mainFilterRow, searchField);

        getChildren().addAll(filterContainer, pagination);
    }

    // --- Abstract Methods for Subclasses to Implement ---

    /** Sets up the specific columns for the table. */
    protected abstract void setupTableColumns();

    /** Sets up any additional filter controls and adds them to the topFilterBar. */
    protected abstract void setupFilters();

    /** Applies the specific filtering logic for the table. */
    public abstract void applyFilter();

    /** Updates the source list of items for the table. */
    public abstract void updateRoster();


    // --- Common Functionality ---

    public void setOnItemDoubleClick(Consumer<T> onItemDoubleClick) {
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    onItemDoubleClick.accept(row.getItem());
                }
            });
            return row;
        });
    }

    public T getSelectedItem() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Node createPageFactory(int pageIndex) {
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        table.setItems(FXCollections.observableArrayList(filteredData.subList(fromIndex, toIndex)));
        return table;
    }

    private void updatePageCount(int totalItems) {
        int pageCount = (totalItems + rowsPerPage - 1) / rowsPerPage;
        if (pageCount == 0) pageCount = 1;
        pagination.setPageCount(pageCount);
        pagination.setPageFactory(this::createPageFactory);
    }

    protected void refreshTable() {
        updatePageCount(filteredData.size());
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
                refreshTable();
            }
        }
    }
}

