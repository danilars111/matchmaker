package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.forms.PlayerFormView;
import org.poolen.frontend.gui.components.tables.PlayerRosterTableView;

import java.util.Map;
import java.util.UUID;

/**
 * A dedicated pop-up window for creating new players and viewing the current roster.
 */
public class PlayerManagementStage extends Stage {

    static PlayerStore playerStore = PlayerStore.getInstance();

    public PlayerManagementStage(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Player Management");

        // --- Main Layout ---
        HBox root = new HBox(20);
        root.setPadding(new Insets(20));

        // --- Create instances of our reusable components ---
        PlayerFormView formView = new PlayerFormView();
        PlayerRosterTableView rosterView = new PlayerRosterTableView();
        HBox.setHgrow(rosterView, Priority.ALWAYS);

        // --- Add them to the window ---
        root.getChildren().addAll(formView, rosterView);

        // --- Event Handlers ---
        formView.getCancelButton().setOnAction(e -> this.close());

        formView.getCreateButton().setOnAction(e -> {
            String playerName = formView.getPlayerName();
            if (playerName.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Player must have a name.");
                alert.showAndWait();
                return;
            }

            Player newPlayer = new Player(playerName, formView.isDungeonMaster());

            playerStore.addPlayer(newPlayer);

            // This updates the main window's UI
            onUpdate.run();
            // This updates the table inside this pop-up window
            rosterView.updateRoster();

            // For now, we'll close the window after a player is created.
            this.close();
        });

        Scene scene = new Scene(root);
        setScene(scene);
    }
}
