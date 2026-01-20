package org.poolen.frontend.gui.components.views.tables.rosters;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Callback;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.persistence.StorePersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;

import java.util.List;
import java.util.function.Consumer;

/**
 * An abstract base class for creating reusable, paginated, and filterable table views.
 * @param <T> The type of the items that will populate the table.
 */
public abstract class BaseRosterTableView<T> extends VBox {

    protected final TableView<T> table;
    protected final TextField searchField;
    protected final Pagination pagination;
    protected final HBox topFilterBar;
    protected final ComboBox<House> houseFilterBox;
    protected final Button refreshButton;

    protected final ObservableList<T> sourceItems;
    protected final FilteredList<T> filteredData;

    private int rowsPerPage = 15;
    private Consumer<T> onItemDoubleClickHandler;
    private StorePersistenceService storePersistenceService;
    private UiTaskExecutor uiTaskExecutor;

    public BaseRosterTableView(StorePersistenceService storePersistenceService, UiTaskExecutor uiTaskExecutor) {
        super(10);
        setPadding(new Insets(10));

        this.table = new TableView<>();
        this.searchField = new TextField();
        this.pagination = new Pagination();
        this.topFilterBar = new HBox(10);
        this.houseFilterBox = new ComboBox<>();
        this.refreshButton = new Button("🔄");
        this.storePersistenceService = storePersistenceService;
        this.uiTaskExecutor = uiTaskExecutor;

        this.sourceItems = FXCollections.observableArrayList();
        this.filteredData = new FilteredList<>(sourceItems, p -> true);

        setupCommonUI();

        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        pagination.heightProperty().addListener((obs, oldH, newH) -> handleResize());

        filteredData.addListener((javafx.collections.ListChangeListener.Change<? extends T> c) -> {
            updatePageCount(filteredData.size());
            pagination.setPageFactory(createPageFactory(filteredData));
        });

        pagination.setPageFactory(createPageFactory(filteredData));
    }

    private void setupCommonUI() {
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchField.setPromptText("Search...");
        VBox.setVgrow(pagination, Priority.ALWAYS);

        // --- Universal Row Number Column ---
        TableColumn<T, Void> rowNumCol = new TableColumn<>("#");
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
        table.getColumns().add(rowNumCol);

        // --- Row Factory for Styling and Double-Click ---
        table.setRowFactory(createRowFactory());


        VBox filterContainer = new VBox(10);
        HBox mainFilterRow = new HBox(10);

        houseFilterBox.getItems().add(null);
        houseFilterBox.getItems().addAll(House.values());
        houseFilterBox.setPromptText("Filter by House");
        houseFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());

        refreshButton.setOnAction(e ->
            uiTaskExecutor.execute(this.getParent().getScene().getWindow(), "Refreshing Roster...",
                    "Roster Successfully Refreshed",
                    (updater) -> {
                            storePersistenceService.findAll();
                            return null;
                        },
                    (result) -> updateRoster()
            ));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        mainFilterRow.getChildren().addAll(houseFilterBox, topFilterBar, spacer, refreshButton);
        filterContainer.getChildren().addAll(mainFilterRow, searchField);

        getChildren().addAll(filterContainer, pagination);
    }

    // --- Abstract Methods for Subclasses ---
    protected abstract void setupTableColumns();
    protected abstract void setupFilters();
    public abstract void applyFilter();
    public abstract void updateRoster();

    /**
     * An empty hook for subclasses to provide their own row styling.
     * @param row The table row being styled.
     * @param item The item in the row.
     * @param empty Whether the row is empty.
     */
    protected void styleRow(TableRow<T> row, T item, boolean empty) {
        // Default implementation does nothing.
    }


    // --- Common Functionality ---
    public void setOnItemDoubleClick(Consumer<T> onItemDoubleClick) {
        this.onItemDoubleClickHandler = onItemDoubleClick;
    }

    public T getSelectedItem() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void selectItem(T item) {
        if (item != null) {
            table.getSelectionModel().select(item);
        }
    }

    /**
     * Creates our universal row factory. This clever little thing handles both
     * the double-click logic and calls our new `styleRow` method, which lets
     * subclasses like PlayerRosterTableView do their own pretty styling!
     */
    private Callback<TableView<T>, TableRow<T>> createRowFactory() {
        return tv -> {
            TableRow<T> row = new TableRow<>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    // This is our new hook for the children to do their styling!
                    styleRow(this, item, empty);
                }
            };

            // This part handles the double-click for everyone.
            row.setOnMouseClicked(event -> {
                if (onItemDoubleClickHandler != null && event.getClickCount() == 2 && (!row.isEmpty()) && row.getItem() != null) {
                    onItemDoubleClickHandler.accept(row.getItem());
                }
            });

            return row;
        };
    }

    private Callback<Integer, Node> createPageFactory(List<T> data) {
        return pageIndex -> {
            int fromIndex = pageIndex * rowsPerPage;
            int toIndex = Math.min(fromIndex + rowsPerPage, data.size());
            table.setItems(FXCollections.observableArrayList(data.subList(fromIndex, toIndex)));
            table.refresh();
            return table;
        };
    }

    private void updatePageCount(int totalItems) {
        int pageCount = (totalItems + rowsPerPage - 1) / rowsPerPage;
        if (pageCount == 0) pageCount = 1;
        int currentPage = pagination.getCurrentPageIndex();
        pagination.setPageCount(pageCount);
        if (currentPage >= pageCount) {
            pagination.setCurrentPageIndex(pageCount - 1);
        }
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
}
