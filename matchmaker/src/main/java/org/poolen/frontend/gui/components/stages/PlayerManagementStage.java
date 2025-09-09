package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
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

    private static final PlayerStore playerStore = PlayerStore.getInstance();

    public PlayerManagementStage(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Player Management");

        SplitPane root = new SplitPane();

        PlayerFormView formView = new PlayerFormView();
        PlayerRosterTableView rosterView = new PlayerRosterTableView();

        root.getItems().addAll(formView, rosterView);
        // We can even set the initial position of the draggable "seam"!
        root.setDividerPositions(0.35);

        // --- Event Handlers ---

        // Listen for double-clicks from our beautiful roster view
        rosterView.setOnPlayerDoubleClick(player -> {
            formView.populateForm(player);
        });

        formView.getCancelButton().setOnAction(e -> formView.clearForm());

        // The action button is now super-powered!
        formView.getActionButton().setOnAction(e -> {
            String playerName = formView.getPlayerName();
            if (playerName.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Player must have a name.");
                alert.showAndWait();
                return;
            }

            Player playerToProcess = formView.getPlayerBeingEdited();

            if (playerToProcess == null) {
                // --- CREATE MODE ---
                Player newPlayer = new Player(playerName, formView.isDungeonMaster());
                playerStore.addPlayer(newPlayer);

            } else {
                // --- UPDATE MODE ---
                playerToProcess.setName(playerName);
                playerToProcess.setDungeonMaster(formView.isDungeonMaster());
            }

            onUpdate.run();
            rosterView.updateRoster();
            formView.clearForm();
        });

        Scene scene = new Scene(root, 800, 500);
        setScene(scene);
    }
}

