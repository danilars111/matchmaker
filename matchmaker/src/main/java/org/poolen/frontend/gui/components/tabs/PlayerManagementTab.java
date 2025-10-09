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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A dedicated tab for creating, viewing, and managing players.
 */
public class PlayerManagementTab extends Tab implements PlayerUpdateListener {

    private static final Logger logger = LoggerFactory.getLogger(PlayerManagementTab.class);

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
            logger.error("PlayerManagementTab.start() called before init(). Tab cannot be initialised.");
            throw new IllegalStateException("%s has not been initialized".formatted(this.getClass().getSimpleName()));
        }
        logger.info("Starting PlayerManagementTab.");

        // --- Layout ---
        root.getItems().addAll(playerForm, rosterView);
        root.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(playerForm, false);


        // --- Event Wiring ---
        rosterView.setOnItemDoubleClick(playerForm::populateForm);
        playerForm.getCancelButton().setOnAction(e -> {
            logger.debug("Cancel button clicked. Clearing form.");
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
        logger.info("PlayerManagementTab initialised successfully.");
    }

    public void editPlayer(Player player) {
        if (player != null) {
            logger.info("Request received to edit player: {}", player.getName());
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
            logger.info("Creating a new player with name '{}'.", playerForm.getPlayerName());
            // Creating a new player using the factory
            player = playerFactory.create(playerForm.getPlayerName(), playerForm.isDungeonMaster());
        } else {
            logger.info("Updating existing player '{}' (UUID: {}).", player.getName(), player.getUuid());
            // Updating an existing player
            player.setName(playerForm.getPlayerName());
            player.setDungeonMaster(playerForm.isDungeonMaster());
            playerStore.addPlayer(player);
        }
        uiPersistenceService.savePlayer(player, getTabPane().getScene().getWindow());
        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        playerForm.clearForm();
        logger.info("Player '{}' saved successfully.", player.getName());
    }

    private void handleDelete() {
        Player player = playerForm.getItemBeingEdited();
        if (player != null) {
            logger.debug("User initiated deletion for player '{}'. Showing confirmation dialog.", player.getName());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                    "Are you sure you want to delete " + player.getName() + "? This cannot be undone.",
                    this.getTabPane()
            );
            Optional<ButtonType> response = confirmation.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed deletion of player '{}'.", player.getName());
                playerStore.removePlayer(player);
                uiPersistenceService.deletePlayer(player, getTabPane().getScene().getWindow());
                onPlayerListChanged.run();
                playerForm.clearForm();
            } else {
                logger.info("User cancelled deletion of player '{}'.", player.getName());
            }
        } else {
            logger.warn("Delete action called but no player was being edited.");
        }
    }

    private void handleShowBlacklist() {
        Player editingPlayer =  playerForm.getItemBeingEdited();
        if (editingPlayer == null) {
            logger.warn("Attempted to show blacklist, but no player is being edited.");
            return;
        }

        isShowingBlacklist = !isShowingBlacklist;
        logger.info("Toggling blacklist view for player '{}'. New state: {}", editingPlayer.getName(), isShowingBlacklist ? "ON" : "OFF");
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
            logger.warn("Blacklist action failed: editingPlayer or selectedPlayer is null.");
            coreProvider.createDialog(DialogType.INFO, "Please select a player from the roster to blacklist.", this.getTabPane()).showAndWait();
            return;
        }

        if (editingPlayer.equals(selectedPlayer)) {
            logger.warn("User '{}' attempted to blacklist themselves.", editingPlayer.getName());
            coreProvider.createDialog(DialogType.INFO, "You cannot blacklist yourself, you silly goose!", this.getTabPane()).showAndWait();
            return;
        }

        if (isShowingBlacklist) { // In "remove from blacklist" mode
            logger.info("Removing player '{}' from '{}'s blacklist.", selectedPlayer.getName(), editingPlayer.getName());
            editingPlayer.unblacklist(selectedPlayer);
        } else { // In "add to blacklist" mode
            logger.info("Adding player '{}' to '{}'s blacklist.", selectedPlayer.getName(), editingPlayer.getName());
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
        logger.info("Received player update notification. Refreshing roster view.");
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
