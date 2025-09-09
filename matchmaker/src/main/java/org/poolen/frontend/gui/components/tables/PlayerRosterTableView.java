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

import java.util.function.Consumer;
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
        this.setMinWidth(420);

        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


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
        updateRoster();

        this.getChildren().addAll(new Label("Current Roster"), playerTable);
    }

    /**
     * Sets up a listener for double-clicks on rows in the table.
     * @param onPlayerDoubleClick The action to perform when a player is double-clicked.
     */
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

    /**
     * A handy little getter to find out who is currently selected in the table.
     * @return The currently selected Player, or null if no one is selected.
     */
    public Player getSelectedPlayer() {
        return playerTable.getSelectionModel().getSelectedItem();
    }

    public void updateRoster() {
        playerTable.getItems().clear();
        playerTable.getItems().addAll(playerStore.getAllPlayers());
    }
}

