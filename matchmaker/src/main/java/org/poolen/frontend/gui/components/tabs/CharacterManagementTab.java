package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.interfaces.store.CharacterStoreProvider;
import org.poolen.backend.db.interfaces.store.PlayerStoreProvider;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.UnsavedChangesDialog;
import org.poolen.frontend.gui.components.views.forms.CharacterFormView;
import org.poolen.frontend.gui.components.views.tables.rosters.CharacterRosterTableView;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.ViewProvider;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A dedicated tab for creating, viewing, and managing characters.
 */
public class CharacterManagementTab extends Tab {

    private static final Logger logger = LoggerFactory.getLogger(CharacterManagementTab.class);

    private final CharacterFormView characterForm;
    private final CharacterRosterTableView rosterView;
    private final SplitPane root;
    private Runnable onPlayerListChanged;
    private final CharacterFactory characterFactory;
    private final CharacterStore characterStore;
    private final PlayerStore playerStore;
    private final UiPersistenceService uiPersistenceService;
    private final CoreProvider coreProvider;
    public CharacterManagementTab(CoreProvider coreProvider, CharacterStoreProvider characterStoreProvider, PlayerStoreProvider playerStoreProvider,
                                  ViewProvider viewProvider, UiPersistenceService uiPersistenceService,
                                  CharacterFactory characterFactory) {
        super("Character Management");
        this.characterFactory = characterFactory;
        this.characterStore = characterStoreProvider.getCharacterStore();
        this.playerStore = playerStoreProvider.getPlayerStore();
        this.uiPersistenceService = uiPersistenceService;
        this.coreProvider = coreProvider;

        this.root = new SplitPane();
        this.characterForm = viewProvider.getCharacterFormView();
        this.rosterView = viewProvider.getCharacterRosterTableView();

        // --- Layout ---
        root.getItems().addAll(characterForm, rosterView);
        root.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(characterForm, false);

        // --- Event Wiring ---
        rosterView.setOnItemDoubleClick(characterForm::populateForm);
        characterForm.getCancelButton().setOnAction(e -> {
            Player filteredPlayer = rosterView.getFilteredPlayer();
            if (filteredPlayer != null) {
                logger.debug("Cancel clicked with player filter active. Creating new character form for player '{}'.", filteredPlayer.getName());
                // A filter is active, so "cancel" means "start new for this player"
                characterForm.createNewCharacterForPlayer(filteredPlayer);
            } else {
                logger.debug("Cancel clicked with no filter active. Clearing form and roster filter.");
                // No filter is active, so "cancel" means clear everything
                characterForm.clearForm();
                rosterView.filterByPlayer(null);
            }
        });
        characterForm.getActionButton().setOnAction(e -> handleCharacterAction());
        characterForm.getRetireButton().setOnAction(e -> handleRetire());
        characterForm.getDeleteButton().setOnAction(e -> handleDelete());
        characterForm.getUnretireButton().setOnAction(e -> handleUnretire());
        characterForm.setOnCreateSecondCharacterRequestHandler(this::handleCreateSecondCharacter);

        this.setContent(root);
        logger.info("CharacterManagementTab initialised.");
    }
    public void init(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
    }

    public CharacterFormView getCharacterForm() {
        return this.characterForm;
    }

    public void showCharactersForPlayer(Player player) {
        logger.info("Filtering character roster to show characters for player: {}", player.getName());
        rosterView.filterByPlayer(player);
    }

    public void createCharacterForPlayer(Player player) {
        logger.info("Setting up character form to create a new character for player: {}", player.getName());
        characterForm.createNewCharacterForPlayer(player);
    }

    private void handleCreateSecondCharacter(Player player) {
        if (characterForm.hasUnsavedChanges()) {
            logger.debug("User tried to create a second character but has unsaved changes. Showing dialog.");
            UnsavedChangesDialog dialog = (UnsavedChangesDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                    "You have unsaved changes to the current character. How would you like to proceed?",
                    this.getTabPane()
            );
            Optional<ButtonType> response = dialog.showAndWait();
            if (response.isPresent()) {
                if (response.get() == UnsavedChangesDialog.UPDATE_AND_CONTINUE) {
                    logger.info("User chose to 'Update and Continue'. Saving current character first.");
                    handleCharacterAction(); // This will save, then clear the form.
                    createCharacterForPlayer(player); // This sets up the cleared form for the new character.
                } else if (response.get() == UnsavedChangesDialog.DISCARD_AND_CONTINUE) {
                    logger.info("User chose to 'Discard and Continue'. Clearing form.");
                    createCharacterForPlayer(player); // This clears the form and sets it up.
                } else {
                    logger.debug("User cancelled the 'create second character' action.");
                }
                // If CANCEL, do nothing.
            }
        } else {
            logger.debug("No unsaved changes detected. Proceeding to create new character for player '{}'.", player.getName());
            // No unsaved changes, just proceed to create the new character.
            createCharacterForPlayer(player);
        }
    }


    private void handleCharacterAction() {
        Character character = characterForm.getItemBeingEdited();
        if (characterForm.getSelectedPlayer() == null) {
            logger.warn("Character action aborted: No player was selected for the character.");
            coreProvider.createDialog(DialogType.INFO, "A character must belong to a player!", this.getTabPane()).showAndWait();
            return;
        }

        try {
            if (character == null) {
                // Creating a new character
                logger.info("Attempting to create a new character '{}' for player '{}'.", characterForm.getCharacterName(), characterForm.getSelectedPlayer().getName());
                character = characterFactory.create(
                        characterForm.getSelectedPlayer(),
                        characterForm.getCharacterName(),
                        characterForm.getSelectedHouse(),
                        characterForm.isMainCharacter()
                );
            } else {
                // Updating an existing character
                logger.info("Attempting to update character '{}'.", character.getName());
                boolean isBecomingMain = characterForm.isMainCharacter();
                Player owner = character.getPlayer();
                Character currentMain = owner.getMainCharacter();

                if (isBecomingMain && currentMain != null && !currentMain.equals(character)) {
                    logger.debug("User is making '{}' the new main character, replacing '{}'. Showing confirmation.", character.getName(), currentMain.getName());
                    ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                            owner.getName() + " already has a main character: " + currentMain.getName() + ".\n\n" +
                                    "Do you want to make " + characterForm.getCharacterName() + " their new main character?",
                            this.getTabPane()
                    );
                    Optional<ButtonType> response = confirmation.showAndWait();
                    if (response.isEmpty() || response.get() != ButtonType.YES) {
                        logger.info("User cancelled the 'change main character' action.");
                        characterForm.populateForm(character); // Revert UI change
                        return; // Stop the action
                    }
                    logger.info("User confirmed changing main character.");
                }
                character.setPlayer(characterForm.getSelectedPlayer());
                character.setName(characterForm.getCharacterName());
                character.setHouse(characterForm.getSelectedHouse());
                character.setMain(characterForm.isMainCharacter());
                characterStore.addCharacter(character);
            }
            logger.debug("Character data is valid. Proceeding with persistence service.");
            uiPersistenceService.saveCharacters(character.getPlayer(), getTabPane().getScene().getWindow());
            onPlayerListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null); // Also clear filter after a successful action
            logger.info("Successfully created/updated character '{}'.", character.getName());
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create/update character due to an validation error.", e);
            coreProvider.createDialog(DialogType.ERROR,e.getMessage(), this.getTabPane()).showAndWait();
        }
    }

    private void handleRetire() {
        Character character = characterForm.getItemBeingEdited();
        if (character != null) {
            logger.info("Retiring character '{}'.", character.getName());
            character.setRetired(true);
            characterStore.addCharacter(character);
            uiPersistenceService.saveCharacters(character.getPlayer(), getTabPane().getScene().getWindow());
            onPlayerListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null);
        } else {
            logger.warn("Retire action called but no character was being edited.");
        }
    }



    private void handleUnretire() {
        Character character = characterForm.getItemBeingEdited();
        if (character != null) {
            logger.info("Un-retiring character '{}'.", character.getName());
            character.setRetired(false);
            characterStore.addCharacter(character);
            uiPersistenceService.saveCharacters(character.getPlayer(), getTabPane().getScene().getWindow());
            onPlayerListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null);
        } else {
            logger.warn("Un-retire action called but no character was being edited.");
        }
    }

    private void handleDelete() {
        Character characterToDelete = characterForm.getItemBeingEdited();
        if (characterToDelete != null) {
            logger.debug("User initiated deletion for character '{}'. Showing confirmation dialog.", characterToDelete.getName());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                    "Are you sure you want to permanently delete " + characterToDelete.getName() + "? This cannot be undone.",
                    this.getTabPane()
            );

            Optional<ButtonType> response = confirmation.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed deletion of character '{}'.", characterToDelete.getName());
                // We must also remove the character from its owner!
                Player owner = characterToDelete.getPlayer();
                if (owner != null) {
                    logger.debug("Removing character from owner '{}'.", owner.getName());
                    owner.removeCharacter(characterToDelete);
                    playerStore.addPlayer(owner);
                }
                characterStore.removeCharacter(characterToDelete);
                uiPersistenceService.deleteCharacter(characterToDelete, getTabPane().getScene().getWindow());
                onPlayerListChanged.run();
                rosterView.updateRoster();
                characterForm.clearForm();
                rosterView.filterByPlayer(null);
                logger.info("Character '{}' successfully deleted.", characterToDelete.getName());
            } else {
                logger.info("User cancelled the deletion of character '{}'.", characterToDelete.getName());
            }
        } else {
            logger.warn("Delete action called but no character was being edited.");
        }
    }

    public void setOnPlayerListChanged(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
    }
}
