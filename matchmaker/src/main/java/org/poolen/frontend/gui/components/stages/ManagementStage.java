package org.poolen.frontend.gui.components.stages;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.LoginApplication;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.PersistenceTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.TabProvider;
import org.poolen.web.google.GoogleAuthManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A dedicated pop-up window for all management tasks, organized into detachable tabs.
 */
public class ManagementStage extends Stage {

    private final Map<Tab, Stage> detachedTabMap = new HashMap<>();
    private final List<PlayerUpdateListener> playerUpdateListeners = new ArrayList<>();
    private final Map<UUID, Player> dmingPlayers;
    private final Map<UUID, Player> attendingPlayers;

    public ManagementStage(CoreProvider coreProvider, TabProvider tabProvider) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Management");

        this.dmingPlayers = new HashMap<>();
        this.attendingPlayers = new HashMap<>();

        PlayerManagementTab playerTab = tabProvider.getPlayerManagementTab();
        playerTab.init(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);

        CharacterManagementTab characterTab = tabProvider.getCharacterManagementTab();
        characterTab.init(this::notifyPlayerUpdateListeners);

        GroupManagementTab groupTab = tabProvider.getGroupManagementTab();
        groupTab.init(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);

        PersistenceTab persistenceTab = tabProvider.getPersistenceTab();
        persistenceTab.init(this::notifyPlayerUpdateListeners);

        SettingsTab settingsTab = tabProvider.getSettingsTab();

        TabPane tabPane = new TabPane();
        playerTab.start();
        groupTab.start();

        addPlayerUpdateListener(groupTab);
        addPlayerUpdateListener(playerTab);


        makeTabDetachable(playerTab);
        makeTabDetachable(characterTab);
        makeTabDetachable(groupTab);
        makeTabDetachable(settingsTab);
        makeTabDetachable(persistenceTab);

        tabPane.getTabs().addAll(playerTab, characterTab, groupTab, persistenceTab, settingsTab);

        // --- Player <-> Character Navigation Wiring ---
        characterTab.getCharacterForm().setOnOpenPlayerRequestHandler(player -> {
            Stage detachedStage = detachedTabMap.get(playerTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                detachedStage.requestFocus();
            } else {
                tabPane.getSelectionModel().select(playerTab);
            }
            playerTab.editPlayer(player);
        });

        playerTab.setOnShowCharactersRequestHandler(player -> {
            Stage detachedStage = detachedTabMap.get(characterTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                detachedStage.requestFocus();
            } else {
                tabPane.getSelectionModel().select(characterTab);
            }
            characterTab.showCharactersForPlayer(player);

            // Auto-select their main or first character
            Character charToEdit = player.getMainCharacter();
            if (charToEdit == null && player.hasCharacters()) {
                charToEdit = player.getCharacters().stream().collect(Collectors.toList()).get(0);
            }
            if (charToEdit != null) {
                characterTab.getCharacterForm().populateForm(charToEdit);
            }
        });

        playerTab.setOnCreateCharacterRequestHandler(player -> {
            Stage detachedStage = detachedTabMap.get(characterTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                detachedStage.requestFocus();
            } else {
                tabPane.getSelectionModel().select(characterTab);
            }
            characterTab.createCharacterForPlayer(player);
        });

        // --- Persistence Wiring ---
        persistenceTab.setOnLogoutRequestHandler(() -> {
            try {
                GoogleAuthManager.logout();
                this.close(); // Close the management stage
                new ArrayList<>(detachedTabMap.values()).forEach(Stage::close); // Close all detached tabs

                // Restart the login application
                LoginApplication loginApp = new LoginApplication();
                Stage loginStage = new Stage();
                loginApp.start(loginStage);

            } catch (Exception e) {
                coreProvider.createDialog(DialogType.ERROR,"Failed to logout: " + e.getMessage(), tabPane).showAndWait();
            }
        });


        this.setOnCloseRequest(event -> {
            new ArrayList<>(detachedTabMap.values()).forEach(Stage::close);
        });

        Scene scene = new Scene(tabPane, 800, 500);
        setScene(scene);
    }

    public void addPlayerUpdateListener(PlayerUpdateListener listener) {
        this.playerUpdateListeners.add(listener);
    }

    public void notifyPlayerUpdateListeners() {
        // Tell all our listeners that something has changed!
        for (PlayerUpdateListener listener : playerUpdateListeners) {
            listener.onPlayerUpdate();
        }
    }


    private void makeTabDetachable(Tab tab) {
        tab.setClosable(false);
        Label title = new Label(tab.getText());
        Button detachButton = new Button("↗");
        detachButton.setStyle("-fx-font-size: 8px; -fx-padding: 2 4 2 4;");
        HBox header = new HBox(5, title, detachButton);
        header.setAlignment(Pos.CENTER_LEFT);

        tab.tabPaneProperty().addListener((obs, oldTabPane, newTabPane) -> {
            if (newTabPane != null) {
                detachButton.visibleProperty().bind(Bindings.size(newTabPane.getTabs()).greaterThan(1));
            } else {
                detachButton.visibleProperty().unbind();
            }
        });
        tab.setGraphic(header);
        // We set the tab text to null because our custom graphic now contains the title.
        // If you want both, you can remove this line.
        if (!tab.getText().isEmpty()) {
            tab.setText(null);
        }

        detachButton.setOnAction(event -> {
            TabPane parent = tab.getTabPane();
            if (parent != null && parent.getTabs().size() > 1) {
                int originalIndex = parent.getTabs().indexOf(tab);
                parent.getTabs().remove(tab);
                Stage detachedStage = new Stage();
                detachedStage.setTitle(title.getText());
                StackPane contentPane = new StackPane();
                if (tab.getContent() != null) {
                    contentPane.getChildren().add(tab.getContent());
                }
                detachedStage.setScene(new Scene(contentPane, 800, 500));
                double parentX = ManagementStage.this.getX();
                double parentY = ManagementStage.this.getY();
                detachedStage.setX(parentX + 30);
                detachedStage.setY(parentY + 60);
                detachedTabMap.put(tab, detachedStage);
                detachedStage.setOnCloseRequest(closeEvent -> {
                    detachedTabMap.remove(tab);
                    if (!parent.getTabs().contains(tab)) {
                        int insertionIndex = Math.min(originalIndex, parent.getTabs().size());
                        parent.getTabs().add(insertionIndex, tab);
                    }
                });
                detachedStage.show();
            }
        });
    }
}

