package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.forms.PlayerFormView;
import org.poolen.frontend.gui.components.tables.PlayerRosterTableView;

import java.util.Map;
import java.util.UUID;

/**
 * A dedicated pop-up window for creating new players and viewing the current roster.
 */
public class PlayerManagementStage extends Stage {
    Runnable onUpdate;
    PlayerFormView playerFormView;
    PlayerRosterTableView playerRosterTableView;

    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private static final CharacterStore characterStore = CharacterStore.getInstance();

    public PlayerManagementStage(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Player Management");

        SplitPane root = new SplitPane();

        this.onUpdate = onUpdate;
        this.playerFormView = new PlayerFormView();
        this.playerRosterTableView = new PlayerRosterTableView(attendingPlayers);

        root.getItems().addAll(playerFormView, playerRosterTableView);
        root.setDividerPositions(0.35);


        // --- Event Handlers ---

        playerRosterTableView.setOnPlayerDoubleClick(player -> {
            playerFormView.populateForm(player);
        });

        playerFormView.getCancelButton().setOnAction(e -> playerFormView.clearForm());

        playerFormView.getActionButton().setOnAction(e -> {
            String playerName = playerFormView.getPlayerName();
            if (playerName.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Player must have a name.").showAndWait();
                return;
            }

            Player playerToProcess = playerFormView.getPlayerBeingEdited();

            if (playerToProcess == null) {
                Player newPlayer = new Player(playerName, playerFormView.isDungeonMaster());
                playerStore.addPlayer(newPlayer);
            } else {
                playerToProcess.setName(playerName);
                playerToProcess.setDungeonMaster(playerFormView.isDungeonMaster());
            }

            onUpdate.run();
            playerRosterTableView.updateRoster();
            playerFormView.clearForm();
        });

        playerFormView.getBlacklistButton().setOnAction(e -> {
            blacklistAction();
        });

        playerFormView.getDeleteButton().setOnAction(e -> {
            deleteAction();
        });
            Scene scene = new Scene(root, 800, 500);
            setScene(scene);
    }

    private void deleteAction() {
        Player editor = playerFormView.getPlayerBeingEdited();
        if (editor == null) {
            new Alert(Alert.AlertType.WARNING, "You must be editing a player (double-click one!) to use the blacklist function.").showAndWait();
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete %s along with all their characters??\n\nPlayer UUID: %s".formatted(editor.getName(), editor.getUuid()),
                ButtonType.YES, ButtonType.NO);

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                for(Character character : editor.getCharacters()) {
                    characterStore.removeCharacter(character);
                    System.out.println("Character %s (%s) has been deleted!".formatted(character.getName(), character.getUuid()));
                }
                playerStore.removePlayer(editor);
                System.out.println("Player %s (%s) has been deleted!".formatted(editor.getName(), editor.getUuid()));
                playerRosterTableView.updateRoster();
                playerFormView.clearForm();
                onUpdate.run(); // Make sure the main app knows about the change
            }
        });
    }

    private void blacklistAction() {
        Player editor = playerFormView.getPlayerBeingEdited();
        Player selected = playerRosterTableView.getSelectedPlayer();

        if (editor == null) {
            new Alert(Alert.AlertType.WARNING, "You must be editing a player (double-click one!) to use the blacklist function.").showAndWait();
            return;
        }
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a player from the roster to blacklist.").showAndWait();
            return;
        }
        if (editor.equals(selected)) {
            new Alert(Alert.AlertType.ERROR, "A player cannot blacklist themselves! A little self-love is important, darling.").showAndWait();
            return;
        }

        // A lovely confirmation pop-up
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want " + editor.getName() + " to blacklist " + selected.getName() + "?",
                ButtonType.YES, ButtonType.NO);

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                editor.blacklist(selected);
                System.out.println(editor.getName() + " has blacklisted " + selected.getName());
                onUpdate.run(); // Make sure the main app knows about the change
            }
        });
    }
}

