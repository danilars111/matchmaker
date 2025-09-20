package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.frontend.gui.components.dialogs.UnsavedChangesDialog;
import org.poolen.frontend.gui.components.views.forms.CharacterFormView;
import org.poolen.frontend.gui.components.views.tables.CharacterRosterTableView;

import java.util.Optional;

/**
 * A dedicated tab for creating, viewing, and managing characters.
 */
public class CharacterManagementTab extends Tab {

    private final CharacterFormView characterForm;
    private final CharacterRosterTableView rosterView;
    private final SplitPane root;
    private final Runnable onListChanged;
    private final CharacterFactory characterFactory = CharacterFactory.getInstance();
    private final CharacterStore characterStore = CharacterStore.getInstance();
    private final PlayerStore playerStore = PlayerStore.getInstance();

    public CharacterManagementTab(Runnable onListChanged) {
        super("Character Management");
        this.onListChanged = onListChanged;

        this.root = new SplitPane();
        this.characterForm = new CharacterFormView();
        this.rosterView = new CharacterRosterTableView();

        // --- Layout ---
        root.getItems().addAll(characterForm, rosterView);
        root.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(characterForm, false);

        // --- Event Wiring ---
        rosterView.setOnItemDoubleClick(characterForm::populateForm);
        characterForm.getCancelButton().setOnAction(e -> {
            Player filteredPlayer = rosterView.getFilteredPlayer();
            if (filteredPlayer != null) {
                // A filter is active, so "cancel" means "start new for this player"
                characterForm.createNewCharacterForPlayer(filteredPlayer);
            } else {
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
    }

    public CharacterFormView getCharacterForm() {
        return this.characterForm;
    }

    public void showCharactersForPlayer(Player player) {
        rosterView.filterByPlayer(player);
    }

    public void createCharacterForPlayer(Player player) {
        characterForm.createNewCharacterForPlayer(player);
    }

    private void handleCreateSecondCharacter(Player player) {
        if (characterForm.hasUnsavedChanges()) {
            UnsavedChangesDialog dialog = new UnsavedChangesDialog(
                    "You have unsaved changes to the current character. How would you like to proceed?",
                    this.getTabPane()
            );
            Optional<ButtonType> response = dialog.showAndWait();
            if (response.isPresent()) {
                if (response.get() == UnsavedChangesDialog.UPDATE_AND_CONTINUE) {
                    handleCharacterAction(); // This will save, then clear the form.
                    createCharacterForPlayer(player); // This sets up the cleared form for the new character.
                } else if (response.get() == UnsavedChangesDialog.DISCARD_AND_CONTINUE) {
                    createCharacterForPlayer(player); // This clears the form and sets it up.
                }
                // If CANCEL, do nothing.
            }
        } else {
            // No unsaved changes, just proceed to create the new character.
            createCharacterForPlayer(player);
        }
    }


    private void handleCharacterAction() {
        Character characterToEdit = (Character) characterForm.getItemBeingEdited();
        if (characterForm.getSelectedPlayer() == null) {
            new InfoDialog("A character must belong to a player, darling!", this.getTabPane()).showAndWait();
            return;
        }

        try {
            if (characterToEdit == null) {
                // Creating a new character
                characterFactory.create(
                        characterForm.getSelectedPlayer(),
                        characterForm.getCharacterName(),
                        characterForm.getSelectedHouse(),
                        characterForm.isMainCharacter()
                );
            } else {
                // Updating an existing character
                boolean isBecomingMain = characterForm.isMainCharacter();
                Player owner = characterToEdit.getPlayer();
                Character currentMain = owner.getMainCharacter();

                if (isBecomingMain && currentMain != null && !currentMain.equals(characterToEdit)) {
                    ConfirmationDialog confirmation = new ConfirmationDialog(
                            owner.getName() + " already has a main character: " + currentMain.getName() + ".\n\n" +
                                    "Do you want to make " + characterForm.getCharacterName() + " their new main character?",
                            this.getTabPane()
                    );
                    Optional<ButtonType> response = confirmation.showAndWait();
                    if (response.isEmpty() || response.get() != ButtonType.YES) {
                        characterForm.populateForm(characterToEdit); // Revert the checkbox change visually
                        return; // Stop the action
                    }
                }

                characterToEdit.setName(characterForm.getCharacterName());
                characterToEdit.setHouse(characterForm.getSelectedHouse());
                characterToEdit.setMain(characterForm.isMainCharacter());
                characterStore.addCharacter(characterToEdit);
            }
            onListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null); // Also clear filter after a successful action
        } catch (IllegalArgumentException e) {
            new ErrorDialog(e.getMessage(), this.getTabPane()).showAndWait();
        }
    }

    private void handleRetire() {
        Character character = (Character) characterForm.getItemBeingEdited();
        if (character != null) {
            character.setRetired(true);
            characterStore.addCharacter(character);
            onListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null);
        }
    }

    private void handleUnretire() {
        Character character = (Character) characterForm.getItemBeingEdited();
        if (character != null) {
            character.setRetired(false);
            characterStore.addCharacter(character);
            onListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
            rosterView.filterByPlayer(null);
        }
    }

    private void handleDelete() {
        Character characterToDelete = (Character) characterForm.getItemBeingEdited();
        if (characterToDelete != null) {
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    "Are you sure you want to permanently delete " + characterToDelete.getName() + "? This cannot be undone.",
                    this.getTabPane()
            );
            Optional<ButtonType> response = confirmation.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.YES) {
                // We must also remove the character from its owner!
                Player owner = characterToDelete.getPlayer();
                if (owner != null) {
                    owner.removeCharacter(characterToDelete);
                    playerStore.addPlayer(owner); // Persist the change to the player
                }
                characterStore.removeCharacter(characterToDelete);
                onListChanged.run();
                rosterView.updateRoster();
                characterForm.clearForm();
                rosterView.filterByPlayer(null);
            }
        }
    }
}

