package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.interfaces.store.PlayerStoreProvider;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.views.forms.PlayerFormView;
import org.poolen.frontend.gui.components.views.tables.rosters.PlayerManagementRosterTableView;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.ViewProvider;
import org.poolen.frontend.util.services.UiPersistenceService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A dedicated tab for creating, viewing, and managing players.
 */
public class PlayerManagementTab extends Tab implements PlayerUpdateListener {

    private final PlayerFormView playerForm;
    private PlayerManagementRosterTableView rosterView;
    private final SplitPane root;
    private Runnable onPlayerListChanged;
    private Map<UUID, Player> attendingPlayers;
    private Map<UUID, Player> dmingPlayers;
    private final PlayerStore playerStore;
    private final PlayerFactory playerFactory;
    private boolean isShowingBlacklist = false;
    private final UiPersistenceService uiPersistenceService;
    private final CoreProvider coreProvider;

    public PlayerManagementTab(CoreProvider coreProvider, PlayerStoreProvider storeProvider, ViewProvider viewProvider,
                               UiPersistenceService uiPersistenceService, PlayerFactory playerFactory) {
        super("Player Management");

        this.uiPersistenceService = uiPersistenceService;
        this.playerStore = storeProvider.getPlayerStore();
        this.playerFactory = playerFactory;
        this.coreProvider = coreProvider;
        this.root = new SplitPane();
        this.playerForm = viewProvider.getPlayerFormView();
        this.rosterView = viewProvider.getPlayerManagementRosterTableView();
    }
    public void init(Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;
        this.onPlayerListChanged = onPlayerListChanged;
        this.rosterView.init(attendingPlayers, dmingPlayers, onPlayerListChanged);
    }

    public void start() {
        if(attendingPlayers == null || dmingPlayers == null || onPlayerListChanged == null) {
            throw new IllegalStateException("%s has not been initialized".formatted(this.getClass().getSimpleName()));
        }

        // --- Layout ---
        root.getItems().addAll(playerForm, rosterView);
        root.setDividerPositions(0.3);
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

    public void editPlayer(Player player) {
        if (player != null) {
            playerForm.populateForm(player);
            rosterView.selectItem(player);
        }
    }

    public void setOnShowCharactersRequestHandler(Consumer<Player> handler) {
        playerForm.setOnShowCharactersRequestHandler(handler);
    }

    public void setOnCreateCharacterRequestHandler(Consumer<Player> handler) {
        playerForm.setOnCreateCharacterRequestHandler(handler);
    }

    /**
     * This is our new helper method so the ManagementStage can find our roster view.
     * @return The PlayerRosterTableView instance used by this tab.
     */
    public PlayerManagementRosterTableView getRosterView() {
        return rosterView;
    }

    private void handlePlayerAction() {
        Player player = (Player) playerForm.getItemBeingEdited();
        if (player == null) {
            // Creating a new player using the factory
            player = playerFactory.create(playerForm.getPlayerName(), playerForm.isDungeonMaster());
        } else {
            // Updating an existing player
            player.setName(playerForm.getPlayerName());
            player.setDungeonMaster(playerForm.isDungeonMaster());
            playerStore.addPlayer(player);
        }
        uiPersistenceService.savePlayer(player, getTabPane().getScene().getWindow());
        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        playerForm.clearForm();
    }

    private void handleDelete() {
        Player player = playerForm.getItemBeingEdited();
        if (player != null) {
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                    "Are you sure you want to delete " + player.getName() + "? This cannot be undone.",
                    this.getTabPane()
            );
            Optional<ButtonType> response = confirmation.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.YES) {
                playerStore.removePlayer(player);
                uiPersistenceService.deletePlayer(player, getTabPane().getScene().getWindow());
                onPlayerListChanged.run();
                playerForm.clearForm();
            }
        }
    }

    private void handleShowBlacklist() {
        Player editingPlayer =  playerForm.getItemBeingEdited();
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
        Player editingPlayer = playerForm.getItemBeingEdited();
        Player selectedPlayer = rosterView.getSelectedItem();

        if (editingPlayer == null || selectedPlayer == null) {
            coreProvider.createDialog(DialogType.INFO, "Please select a player from the roster to blacklist.", this.getTabPane()).showAndWait();
            return;
        }

        if (editingPlayer.equals(selectedPlayer)) {
            coreProvider.createDialog(DialogType.INFO, "You cannot blacklist yourself, you silly goose!", this.getTabPane()).showAndWait();
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

    @Override
    public void onPlayerUpdate() {
        System.out.println("PlayerManagement Tab heard a player update! Refreshing DM list and roster...");
        rosterView.updateRoster();
    }

    public Runnable getOnPlayerListChanged() {
        return onPlayerListChanged;
    }

    public void setOnPlayerListChanged(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
    }

    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }

    public Map<UUID, Player> getDmingPlayers() {
        return dmingPlayers;
    }
}

