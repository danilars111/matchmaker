package org.poolen.frontend.gui.components.views.tables;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.store.CharacterStore;

/**
 * A concrete implementation of BaseRosterTableView for displaying Characters.
 */
public class CharacterRosterTableView extends BaseRosterTableView<Character> {

    private CheckBox showRetiredCheckBox;
    private CheckBox showMainsOnlyCheckBox;

    public CharacterRosterTableView() {
        super();

        // Call setup methods now that fields are initialized
        setupTableColumns();
        setupFilters();

        // Now that everything is set up, we can populate the table
        updateRoster();
    }

    @Override
    protected void setupTableColumns() {
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

        table.getColumns().addAll(nameCol, houseCol, playerCol, mainCol);
    }

    @Override
    protected void setupFilters() {
        showRetiredCheckBox = new CheckBox("Show Retired");
        showMainsOnlyCheckBox = new CheckBox("Mains Only");

        showRetiredCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        showMainsOnlyCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        topFilterBar.getChildren().addAll(showRetiredCheckBox, showMainsOnlyCheckBox);
    }

    @Override
    public void applyFilter() {
        String searchText = searchField.getText();
        boolean showRetired = showRetiredCheckBox.isSelected();
        boolean mainsOnly = showMainsOnlyCheckBox.isSelected();
        House selectedHouse = houseFilterBox.getValue();

        filteredData.setPredicate(character -> {
            boolean retiredMatch = character.isRetired() == showRetired;
            boolean mainMatch = !mainsOnly || character.isMain();
            boolean houseMatch = selectedHouse == null || character.getHouse() == selectedHouse;
            boolean textMatch = searchText == null || searchText.isEmpty() ||
                    character.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                    (character.getPlayer() != null && character.getPlayer().getName().toLowerCase().contains(searchText.toLowerCase()));

            return retiredMatch && textMatch && mainMatch && houseMatch;
        });

        refreshTable();
    }

    @Override
    public void updateRoster() {
        sourceItems.setAll(CharacterStore.getInstance().getAllCharacters());
        applyFilter();
    }

    private TableCell<Character, Boolean> createBooleanCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    setStyle(item ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
                }
            }
        };
    }

    // Alias for clarity
    public void setOnCharacterDoubleClick(java.util.function.Consumer<Character> handler) {
        setOnItemDoubleClick(handler);
    }

    public Character getSelectedCharacter() {
        return getSelectedItem();
    }
}

