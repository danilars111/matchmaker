package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.forms.PlayerFormView;
import org.poolen.frontend.gui.components.tables.PlayerRosterTableView;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A dedicated tab for creating and updating players.
 */
public class PlayerManagementTab extends Tab {

    private boolean isShowingBlacklist = false;
    private static final PlayerStore playerStore = PlayerStore.getInstance();

    public PlayerManagementTab(Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super("Player Management");

        SplitPane root = new SplitPane();

        PlayerFormView formView = new PlayerFormView();
        PlayerRosterTableView rosterView = new PlayerRosterTableView(attendingPlayers, onPlayerListChanged);

        root.getItems().addAll(formView, rosterView);
        root.setDividerPositions(0.35);

        rosterView.setOnPlayerDoubleClick(player -> {
            if (isShowingBlacklist) {
                formView.getBlacklistButton().fire();
            } else {
                formView.populateForm(player);
                formView.setBlacklistMode(false);
            }
        });

        formView.getCancelButton().setOnAction(e -> formView.clearForm());

        formView.getActionButton().setOnAction(e -> {
            String playerName = formView.getPlayerName();
            if (playerName.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Player must have a name.").showAndWait();
                return;
            }

            Player playerToProcess = formView.getPlayerBeingEdited();

            if (playerToProcess == null) {
                Player newPlayer = new Player(playerName, formView.isDungeonMaster());
                playerStore.addPlayer(newPlayer);
            } else {
                playerToProcess.setName(playerName);
                playerToProcess.setDungeonMaster(formView.isDungeonMaster());
            }

            onPlayerListChanged.run(); // Announce the news!
            rosterView.updateRoster();
            formView.clearForm();
        });

        formView.getDeleteButton().setOnAction(e -> {
            Player playerToDelete = formView.getPlayerBeingEdited();
            if (playerToDelete != null) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to delete " + playerToDelete.getName() + "? This cannot be undone.",
                        ButtonType.YES, ButtonType.NO);

                confirmation.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        playerStore.removePlayer(playerToDelete);
                        attendingPlayers.remove(playerToDelete.getUuid());
                        onPlayerListChanged.run(); // Announce the news!
                        rosterView.updateRoster();
                        formView.clearForm();
                    }
                });
            }
        });

        formView.getShowBlacklistButton().setOnAction(e -> {
            Player editor = formView.getPlayerBeingEdited();
            if (editor == null) {
                new Alert(Alert.AlertType.WARNING, "You must be editing a player to view their blacklist.").showAndWait();
                return;
            }
            isShowingBlacklist = !isShowingBlacklist;
            formView.setBlacklistMode(isShowingBlacklist);
            if (isShowingBlacklist) {
                rosterView.showBlacklistedPlayers(editor);
            } else {
                rosterView.showAllPlayers();
            }
        });

        formView.getBlacklistButton().setOnAction(e -> {
            Player editor = formView.getPlayerBeingEdited();
            Player selected = rosterView.getSelectedPlayer();
            if (editor == null) {
                new Alert(Alert.AlertType.WARNING, "Double-click a player to edit them before managing their blacklist.").showAndWait();
                return;
            }
            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "Please select a player from the roster.").showAndWait();
                return;
            }
            if (editor.equals(selected)) {
                new Alert(Alert.AlertType.ERROR, "A player cannot blacklist themselves!").showAndWait();
                return;
            }
            if (isShowingBlacklist) {
                Optional<ButtonType> response = new Alert(Alert.AlertType.CONFIRMATION,
                        "Remove " + selected.getName() + " from " + editor.getName() + "'s blacklist?",
                        ButtonType.YES, ButtonType.NO).showAndWait();
                if (response.isPresent() && response.get() == ButtonType.YES) {
                    editor.unblacklist(selected);
                    onPlayerListChanged.run(); // Announce the news!
                    rosterView.showBlacklistedPlayers(editor);
                }
            } else {
                Optional<ButtonType> response = new Alert(Alert.AlertType.CONFIRMATION,
                        "Are you sure you want " + editor.getName() + " to blacklist " + selected.getName() + "?",
                        ButtonType.YES, ButtonType.NO).showAndWait();
                if (response.isPresent() && response.get() == ButtonType.YES) {
                    editor.blacklist(selected);
                    onPlayerListChanged.run(); // Announce the news!
                }
            }
        });

        this.setContent(root);
    }
}

