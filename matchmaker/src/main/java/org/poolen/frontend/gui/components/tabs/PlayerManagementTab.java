package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.frontend.gui.components.views.forms.PlayerFormView;
import org.poolen.frontend.gui.components.views.tables.PlayerRosterTableView;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A dedicated tab for creating, viewing, and managing players.
 */
public class PlayerManagementTab extends Tab {

    private final PlayerFormView playerForm;
    private final PlayerRosterTableView rosterView;
    private final SplitPane root;
    private final Runnable onPlayerListChanged;
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private static final PlayerFactory playerFactory = PlayerFactory.getInstance();
    private boolean isShowingBlacklist = false;

    public PlayerManagementTab(Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        super("Player Management");

        this.onPlayerListChanged = onPlayerListChanged;
        this.root = new SplitPane();
        this.playerForm = new PlayerFormView();
        this.rosterView = new PlayerRosterTableView(PlayerRosterTableView.RosterMode.PLAYER_MANAGEMENT, attendingPlayers, dmingPlayers, onPlayerListChanged);

        // --- Layout ---
        root.getItems().addAll(playerForm, rosterView);
        root.setDividerPositions(0.3);
        playerForm.setMinWidth(50);
        playerForm.setMaxWidth(310);
        SplitPane.setResizableWithParent(playerForm, false);


        // --- Event Wiring ---
        rosterView.setOnItemDoubleClick(playerForm::populateForm);
        playerForm.getCancelButton().setOnAction(e -> {
            playerForm.clearForm();
            if (isShowingBlacklist) {
                handleShowBlacklist(); // This will toggle it off and reset the view
            }
        });
        playerForm.getActionButton().setOnAction(e -> handlePlayerAction());
        playerForm.getDeleteButton().setOnAction(e -> handleDelete());
        playerForm.getBlacklistButton().setOnAction(e -> handleBlacklistAction());
        playerForm.getShowBlacklistButton().setOnAction(e -> handleShowBlacklist());


        this.setContent(root);
    }

    private void handlePlayerAction() {
        Player playerToEdit = (Player) playerForm.getItemBeingEdited();
        if (playerToEdit == null) {
            // Creating a new player using the factory
            playerFactory.create(playerForm.getPlayerName(), playerForm.isDungeonMaster());
        } else {
            // Updating an existing player
            playerToEdit.setName(playerForm.getPlayerName());
            playerToEdit.setDungeonMaster(playerForm.isDungeonMaster());
            playerStore.addPlayer(playerToEdit);
        }
        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        playerForm.clearForm();
    }

    private void handleDelete() {
        Player playerToDelete = (Player) playerForm.getItemBeingEdited();
        if (playerToDelete != null) {
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    "Are you sure you want to delete " + playerToDelete.getName() + "? This cannot be undone.",
                    this.getTabPane()
            );
            Optional<ButtonType> response = confirmation.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.YES) {
                playerStore.removePlayer(playerToDelete);
                onPlayerListChanged.run();
                rosterView.updateRoster();
                playerForm.clearForm();
            }
        }
    }

    private void handleShowBlacklist() {
        Player editingPlayer = (Player) playerForm.getItemBeingEdited();
        if (editingPlayer == null) return;

        isShowingBlacklist = !isShowingBlacklist;
        playerForm.setBlacklistMode(isShowingBlacklist);

        if (isShowingBlacklist) {
            rosterView.showBlacklistedPlayers(editingPlayer);
        } else {
            rosterView.showAllPlayers();
        }
    }

    private void handleBlacklistAction() {
        Player editingPlayer = (Player) playerForm.getItemBeingEdited();
        Player selectedPlayer = rosterView.getSelectedItem();

        if (editingPlayer == null || selectedPlayer == null) {
            new InfoDialog("Please select a player from the roster to blacklist.", this.getTabPane()).showAndWait();
            return;
        }

        if (editingPlayer.equals(selectedPlayer)) {
            new InfoDialog("You cannot blacklist yourself, you silly goose!", this.getTabPane()).showAndWait();
            return;
        }

        if (isShowingBlacklist) { // In "remove from blacklist" mode
            editingPlayer.unblacklist(selectedPlayer);
        } else { // In "add to blacklist" mode
            editingPlayer.blacklist(selectedPlayer);
        }
        playerStore.addPlayer(editingPlayer);

        // Refresh the roster view to reflect the change
        if (isShowingBlacklist) {
            rosterView.showBlacklistedPlayers(editingPlayer);
        } else {
            rosterView.updateRoster();
        }
    }
}

