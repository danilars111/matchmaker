package org.poolen.frontend.gui.components.tables;


import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A reusable JavaFX component that displays a table of players.
 */
/**
 * A reusable JavaFX component that displays a table of all players from the PlayerStore.
 */
public class PlayerRosterTableView extends VBox {

    private final TableView<Player> playerTable;
    private static final PlayerStore playerStore = PlayerStore.getInstance();

    public PlayerRosterTableView() {
        super(10); // Spacing for the VBox

        this.playerTable = new TableView<>();

        VBox.setVgrow(this.playerTable, Priority.ALWAYS);
        this.playerTable.setMaxWidth(Double.MAX_VALUE);


        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Player, Boolean> dmCol = new TableColumn<>("DM");
        dmCol.setCellValueFactory(new PropertyValueFactory<>("dungeonMaster"));

        TableColumn<Player, String> charCol = new TableColumn<>("Characters");
        charCol.setCellValueFactory(cellData -> {
            Player player = cellData.getValue();
            String characters = player.getCharacters().stream()
                    .map(c -> c.getHouse().toString().substring(0, 1)) // Abbreviate for space
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(characters);
        });

        // --- The Proportional Stretching Magic! ---
        // Instead of a fixed width, we bind each column's width to a percentage of the table's total width.
        // This ensures they all resize beautifully together.
        nameCol.prefWidthProperty().bind(playerTable.widthProperty().multiply(0.375));
        dmCol.prefWidthProperty().bind(playerTable.widthProperty().multiply(0.125));
        charCol.prefWidthProperty().bind(playerTable.widthProperty().multiply(0.50));
        // ------------------------------------------


        playerTable.getColumns().addAll(nameCol, dmCol, charCol);
        updateRoster(); // Populate the table on creation

        this.getChildren().addAll(new Label("Current Roster"), playerTable);
    }

    /**
     * Clears and repopulates the table with the latest data from the PlayerStore.
     */
    public void updateRoster() {
        playerTable.getItems().clear();
        playerTable.getItems().addAll(playerStore.getAllPlayers());
    }
}
