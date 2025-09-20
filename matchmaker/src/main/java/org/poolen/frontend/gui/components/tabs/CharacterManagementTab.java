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
        characterForm.setMinWidth(50);
        characterForm.setMaxWidth(310);
        SplitPane.setResizableWithParent(characterForm, false);

        // --- Event Wiring ---
        rosterView.setOnItemDoubleClick(characterForm::populateForm);
        characterForm.getCancelButton().setOnAction(e -> characterForm.clearForm());
        characterForm.getActionButton().setOnAction(e -> handleCharacterAction());
        characterForm.getRetireButton().setOnAction(e -> handleRetire());
        characterForm.getDeleteButton().setOnAction(e -> handleDelete());
        characterForm.getUnretireButton().setOnAction(e -> handleUnretire());

        this.setContent(root);
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
                characterToEdit.setName(characterForm.getCharacterName());
                characterToEdit.setHouse(characterForm.getSelectedHouse());
                characterToEdit.setMain(characterForm.isMainCharacter());
                characterStore.addCharacter(characterToEdit);
            }
            onListChanged.run();
            rosterView.updateRoster();
            characterForm.clearForm();
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
            }
        }
    }
}

