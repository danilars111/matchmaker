package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
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
    private final Runnable onPlayerListChanged;
    private static final CharacterStore characterStore = CharacterStore.getInstance();
    private static final CharacterFactory characterFactory = CharacterFactory.getInstance();
    private static final PlayerStore playerStore = PlayerStore.getInstance();


    public CharacterManagementTab(Runnable onPlayerListChanged) {
        super("Character Management");
        this.onPlayerListChanged = onPlayerListChanged;

        this.root = new SplitPane();
        this.characterForm = new CharacterFormView();
        this.rosterView = new CharacterRosterTableView();

        // --- Layout ---
        root.getItems().addAll(characterForm, rosterView);
        root.setDividerPositions(0.3);
        characterForm.setMinWidth(50);
        characterForm.setMaxWidth(310);

        // --- Event Wiring ---
        rosterView.setOnCharacterDoubleClick(characterForm::populateForm);
        characterForm.getCancelButton().setOnAction(e -> characterForm.clearForm());
        characterForm.getActionButton().setOnAction(e -> handleCharacterAction());
        characterForm.getRetireButton().setOnAction(e -> handleRetire());
        characterForm.getUnRetireButton().setOnAction(e -> handleUnRetire());
        characterForm.getDeleteButton().setOnAction(e -> handleDelete());


        this.setContent(root);
    }

    private void handleCharacterAction() {
        Character characterToEdit = characterForm.getCharacterBeingEdited();

        if (characterToEdit == null) {
            // --- Creating a new character ---
            try {
                characterFactory.create(
                        characterForm.getSelectedPlayer(),
                        characterForm.getCharacterName(),
                        characterForm.getSelectedHouse(),
                        characterForm.isMainCharacter()
                );
            } catch (IllegalArgumentException e) {
                showError("Creation Failed", e.getMessage());
                return;
            }
        } else {
            // --- Updating an existing character ---
            characterToEdit.setName(characterForm.getCharacterName());
            characterToEdit.setHouse(characterForm.getSelectedHouse());
            characterToEdit.setMain(characterForm.isMainCharacter());
            characterStore.addCharacter(characterToEdit);
        }

        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        characterForm.clearForm();
    }

    private void handleRetire() {
        Character characterToRetire = characterForm.getCharacterBeingEdited();
        if (characterToRetire == null) return;

        characterToRetire.setRetired(true);
        characterStore.addCharacter(characterToRetire);
        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        characterForm.clearForm();
    }

    private void handleUnRetire() {
        Character characterToUnRetire = characterForm.getCharacterBeingEdited();
        if (characterToUnRetire == null) return;

        // Business Rule Check: Player can't have more than 2 active characters
        Player owner = characterToUnRetire.getPlayer();
        if (owner != null) {
            long activeCharCount = owner.getCharacters().stream().filter(c -> !c.isRetired()).count();
            if (activeCharCount >= 2) {
                showError("Un-Retire Failed", "A player cannot have more than two active characters.");
                return;
            }
        }

        characterToUnRetire.setRetired(false);
        characterStore.addCharacter(characterToUnRetire);
        onPlayerListChanged.run(); // Notify everyone!
        rosterView.updateRoster();
        characterForm.clearForm();
    }


    private void handleDelete() {
        Character characterToDelete = characterForm.getCharacterBeingEdited();
        if (characterToDelete != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to permanently delete " + characterToDelete.getName() + "? This cannot be undone.",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                Player owner = characterToDelete.getPlayer();
                if (owner != null) {
                    owner.getCharacters().remove(characterToDelete);
                    playerStore.addPlayer(owner); // Persist the change to the player
                }
                characterStore.removeCharacter(characterToDelete);
                onPlayerListChanged.run(); // Notify everyone!
                rosterView.updateRoster();
                characterForm.clearForm();
            }
        }
    }

    private void showError(String title, String message) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.initOwner(this.getTabPane().getScene().getWindow());
        errorAlert.setTitle(title);
        errorAlert.setHeaderText(null);
        errorAlert.setContentText(message);
        errorAlert.showAndWait();
    }
}

