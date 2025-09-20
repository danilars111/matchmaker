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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

/**
 * A dedicated tab for creating, viewing, and managing characters.
 */
public class CharacterManagementTab extends Tab {

    private final CharacterFormView characterForm;
    private final CharacterRosterTableView rosterView;
    private final Runnable onListChanged;

    private static final CharacterStore characterStore = CharacterStore.getInstance();
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private static final CharacterFactory characterFactory = CharacterFactory.getInstance();

    public CharacterManagementTab(Runnable onListChanged) {
        super("Character Management");
        this.onListChanged = onListChanged;

        // Fetch all players to pass to the form and roster
        var allPlayers = new ArrayList<>(playerStore.getAllPlayers());
        allPlayers.sort(Comparator.comparing(Player::getName));

        this.characterForm = new CharacterFormView(allPlayers);
        this.rosterView = new CharacterRosterTableView();

        SplitPane root = new SplitPane(characterForm, rosterView);
        root.setDividerPositions(0.3);
        characterForm.setMinWidth(50);
        characterForm.setMaxWidth(310);

        // --- Event Wiring ---
        rosterView.setOnCharacterDoubleClick(characterForm::populateForm);
        characterForm.getCancelButton().setOnAction(e -> characterForm.clearForm());
        characterForm.getActionButton().setOnAction(e -> handleCharacterAction());
        characterForm.getDeleteButton().setOnAction(e -> handleDelete());

        // When the main player list changes, update the dropdown in our form.
        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) {
                var updatedPlayers = new ArrayList<>(playerStore.getAllPlayers());
                characterForm.updatePlayerList(updatedPlayers);
            }
        });


        setContent(root);
    }

    private void handleCharacterAction() {
        // --- Input Validation ---
        if (characterForm.getCharacterName().isBlank() || characterForm.getSelectedPlayer() == null || characterForm.getSelectedHouse() == null) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Please fill in all fields: Name, Player, and House.");
            error.initOwner(this.getTabPane().getScene().getWindow());
            error.showAndWait();
            return;
        }

        Character characterToEdit = characterForm.getCharacterBeingEdited();
        if (characterToEdit == null) {
            // --- Creating a new character ---
            try {
                characterFactory.create(
                        characterForm.getSelectedPlayer(),
                        characterForm.getCharacterName(),
                        characterForm.getSelectedHouse(),
                        characterForm.isMain()
                );
                // The factory now handles adding to the store and player
            } catch (IllegalArgumentException e) {
                Alert error = new Alert(Alert.AlertType.ERROR, e.getMessage());
                error.initOwner(this.getTabPane().getScene().getWindow());
                error.showAndWait();
                return;
            }

        } else {
            // --- Updating an existing character ---
            characterToEdit.setName(characterForm.getCharacterName());
            characterToEdit.setHouse(characterForm.getSelectedHouse());
            characterToEdit.setPlayer(characterForm.getSelectedPlayer()); // Handle player change
            characterToEdit.setMain(characterForm.isMain());
            characterToEdit.setRetired(characterForm.isRetired());
            characterStore.addCharacter(characterToEdit); // This also updates the player link
        }

        rosterView.updateRoster();
        characterForm.clearForm();
        onListChanged.run(); // Notify other tabs that data may have changed
    }

    private void handleDelete() {
        Character charToDelete = characterForm.getCharacterBeingEdited();
        if (charToDelete != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to delete " + charToDelete.getName() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    Player owner = charToDelete.getPlayer();
                    characterStore.removeCharacter(charToDelete);
                    if (owner != null) {
                        owner.getCharacters().remove(charToDelete);
                        playerStore.addPlayer(owner); // Persist the change
                    }
                    rosterView.updateRoster();
                    characterForm.clearForm();
                    onListChanged.run();
                }
            });
        }
    }
}

