package org.poolen.frontend.gui.components.views.tables;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.CharacterStore;

/**
 * A reusable table view for displaying and filtering Characters, inheriting from BaseRosterTableView.
 */
public class CharacterRosterTableView extends BaseRosterTableView<Character> {

    private final CharacterStore characterStore = CharacterStore.getInstance();
    private CheckBox retiredFilterCheckBox;
    private CheckBox mainsFilterCheckBox;
    private Button clearPlayerFilterButton;
    private Player selectedPlayer; // Can be null to show all characters

    public CharacterRosterTableView() {
        super();
        this.searchField.setPromptText("Search by character or player name...");
        setupTableColumns();
        setupFilters();
        updateRoster(); // Initial data load
    }

    @Override
    protected void setupTableColumns() {
        TableColumn<Character, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Character, String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(cellData -> {
            Player player = cellData.getValue().getPlayer();
            return new SimpleStringProperty(player != null ? player.getName() : "N/A");
        });

        TableColumn<Character, Boolean> mainCol = new TableColumn<>("Main");
        mainCol.setCellValueFactory(cellData -> new SimpleBooleanProperty(cellData.getValue().isMain()));

        table.getColumns().addAll(nameCol, playerCol, mainCol);
    }

    @Override
    protected void setupFilters() {
        retiredFilterCheckBox = new CheckBox("Show Retired");
        mainsFilterCheckBox = new CheckBox("Mains Only");
        clearPlayerFilterButton = new Button("Clear Player Filter");

        retiredFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        mainsFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        clearPlayerFilterButton.setOnAction(e -> filterByPlayer(null));
        clearPlayerFilterButton.setVisible(false); // Initially hidden

        topFilterBar.getChildren().addAll(mainsFilterCheckBox, retiredFilterCheckBox, clearPlayerFilterButton);
    }

    @Override
    public void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        boolean showRetired = retiredFilterCheckBox.isSelected();
        boolean mainsOnly = mainsFilterCheckBox.isSelected();

        filteredData.setPredicate(character -> {
            if (character.isRetired() != showRetired) {
                return false;
            }

            if (selectedPlayer != null && !character.getPlayer().equals(selectedPlayer)) {
                return false;
            }

            if (mainsOnly && !character.isMain()) {
                return false;
            }

            boolean nameMatch = character.getName().toLowerCase().contains(searchText);
            boolean playerMatch = character.getPlayer() != null && character.getPlayer().getName().toLowerCase().contains(searchText);

            return nameMatch || playerMatch;
        });
    }

    @Override
    public void updateRoster() {
        sourceItems.setAll(characterStore.getAllCharacters());
        applyFilter();
    }

    /**
     * Filters the roster to show characters only for a specific player.
     * @param player The player to filter by, or null to clear the filter.
     */
    public void filterByPlayer(Player player) {
        this.selectedPlayer = player;
        this.houseFilterBox.setDisable(player != null); // Disable house filter when player is selected
        this.clearPlayerFilterButton.setVisible(player != null);
        applyFilter();
    }
}
