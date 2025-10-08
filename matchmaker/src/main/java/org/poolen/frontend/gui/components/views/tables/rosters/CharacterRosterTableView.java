package org.poolen.frontend.gui.components.views.tables.rosters;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.store.CharacterStoreProvider;
import org.poolen.backend.db.store.CharacterStore;

/**
 * A reusable table view for displaying and filtering Characters, inheriting from BaseRosterTableView.
 */
public class CharacterRosterTableView extends BaseRosterTableView<Character> {

    private final CharacterStore characterStore;
    private CheckBox retiredFilterCheckBox;
    private CheckBox mainsFilterCheckBox;
    private Player selectedPlayer; // Can be null to show all characters

    public CharacterRosterTableView(CharacterStoreProvider store) {
        super();
        this.characterStore = store.getCharacterStore();
        this.searchField.setPromptText("Search by character or player name...");
        setupTableColumns();
        setupFilters();
        updateRoster();
    }

    @Override
    protected void setupTableColumns() {
        TableColumn<Character, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Character, House> houseCol = new TableColumn<>("House");
        houseCol.setCellValueFactory(new PropertyValueFactory<>("house"));
        houseCol.setCellFactory(column -> {
            return new TableCell<Character, House>() {
                @Override
                protected void updateItem(House house, boolean empty) {
                    super.updateItem(house, empty);

                    if (house == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(house.toString());
                        String color = getHouseColor(house);
                        setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
                    }
                }
            };
        });

        TableColumn<Character, String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(cellData -> {
            Player player = cellData.getValue().getPlayer();
            return new SimpleStringProperty(player != null ? player.getName() : "N/A");
        });

        TableColumn<Character, Boolean> mainCol = new TableColumn<>("Main");
        mainCol.setCellValueFactory(cellData -> new SimpleBooleanProperty(cellData.getValue().isMain()));

        table.getColumns().addAll(nameCol, houseCol, playerCol, mainCol);
    }

    @Override
    protected void setupFilters() {
        retiredFilterCheckBox = new CheckBox("Show Retired");
        mainsFilterCheckBox = new CheckBox("Mains Only");

        retiredFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());
        mainsFilterCheckBox.selectedProperty().addListener((obs, old, val) -> applyFilter());

        topFilterBar.getChildren().addAll(mainsFilterCheckBox, retiredFilterCheckBox);
    }

    private String getHouseColor(House house) {
        if (house == null) {
            return "black"; // Default color for characters with no house
        }
        // Finalized palette with more distinct red and brown tones.
        switch (house) {
            case GARNET:
                return "#C0392B"; // A clearer, classic red.
            case AMBER:
                return "#8D6E63"; // A definite, warm brown.
            case AVENTURINE:
                return "#1E8449"; // A solid, forest green.
            case OPAL:
                return "#21618C"; // A deep, readable blue.
            default:
                return "black"; // Fallback color
        }
    }


    @Override
    public void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        boolean showRetired = retiredFilterCheckBox.isSelected();
        boolean mainsOnly = mainsFilterCheckBox.isSelected();
        House selectedHouse = houseFilterBox.getValue();

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

            if (selectedHouse != null && character.getHouse() != selectedHouse) {
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
        applyFilter();
    }

    public Player getFilteredPlayer() {
        return selectedPlayer;
    }
}

