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
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;
import org.poolen.frontend.gui.components.tabs.SheetsTab;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.TabProvider;
import org.poolen.web.google.SheetsServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(ManagementStage.class);
    private static final int MIN_WIDTH = 1000;
    private static final int MIN_HEIGHT = 700;

    private final Map<Tab, Stage> detachedTabMap = new HashMap<>();
    private final List<PlayerUpdateListener> playerUpdateListeners = new ArrayList<>();
    private final Map<UUID, Player> dmingPlayers;
    private final Map<UUID, Player> attendingPlayers;

    // --- Look! We're saving this as a field now! ---
    private final SheetsTab sheetsTab;

    public ManagementStage(CoreProvider coreProvider, TabProvider tabProvider) {
        logger.info("Initialising ManagementStage...");
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Management");

        this.setMinWidth(MIN_WIDTH);
        this.setMinHeight(MIN_HEIGHT);

        this.dmingPlayers = new HashMap<>();
        this.attendingPlayers = new HashMap<>();

        PlayerManagementTab playerTab = tabProvider.getPlayerManagementTab();
        playerTab.init(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);

        CharacterManagementTab characterTab = tabProvider.getCharacterManagementTab();
        characterTab.init(this::notifyPlayerUpdateListeners);

        GroupManagementTab groupTab = tabProvider.getGroupManagementTab();
        groupTab.init(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);

        // --- See, my love? We save it to our field! ---
        this.sheetsTab = tabProvider.getSheetsTab();
        this.sheetsTab.init(this::handleImportedData); // <-- This line is now active!

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
        makeTabDetachable(sheetsTab); // We can still make it detachable!

        tabPane.getTabs().addAll(playerTab, characterTab, groupTab, sheetsTab, settingsTab);
        logger.debug("All management tabs have been initialised and added to the tab pane.");

        // --- Player <-> Character Navigation Wiring ---
        characterTab.getCharacterForm().setOnOpenPlayerRequestHandler(player -> {
            logger.info("Handling request to navigate to player '{}' from character tab.", player.getName());
            Stage detachedStage = detachedTabMap.get(playerTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                logger.debug("Player tab is detached; focusing its stage.");
                detachedStage.requestFocus();
            } else {
                logger.debug("Player tab is attached; selecting it in the main tab pane.");
                tabPane.getSelectionModel().select(playerTab);
            }
            playerTab.editPlayer(player);
        });

        playerTab.setOnShowCharactersRequestHandler(player -> {
            logger.info("Handling request to show characters for player '{}'.", player.getName());
            Stage detachedStage = detachedTabMap.get(characterTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                logger.debug("Character tab is detached; focusing its stage.");
                detachedStage.requestFocus();
            } else {
                logger.debug("Character tab is attached; selecting it in the main tab pane.");
                tabPane.getSelectionModel().select(characterTab);
            }
            characterTab.showCharactersForPlayer(player);

            // Auto-select their main or first character
            Character charToEdit = player.getMainCharacter();
            if (charToEdit == null && player.hasCharacters()) {
                charToEdit = player.getCharacters().stream().collect(Collectors.toList()).get(0);
            }
            if (charToEdit != null) {
                logger.debug("Auto-selecting character '{}' for editing.", charToEdit.getName());
                characterTab.getCharacterForm().populateForm(charToEdit);
            }
        });

        playerTab.setOnCreateCharacterRequestHandler(player -> {
            logger.info("Handling request to create a new character for player '{}'.", player.getName());
            Stage detachedStage = detachedTabMap.get(characterTab);
            if (detachedStage != null && detachedStage.isShowing()) {
                logger.debug("Character tab is detached; focusing its stage.");
                detachedStage.requestFocus();
            } else {
                logger.debug("Character tab is attached; selecting it in the main tab pane.");
                tabPane.getSelectionModel().select(characterTab);
            }
            characterTab.createCharacterForPlayer(player);
        });

        this.setOnCloseRequest(event -> {
            logger.info("ManagementStage close requested. Closing all detached tabs as well.");
            new ArrayList<>(detachedTabMap.values()).forEach(Stage::close);
        });

        Scene scene = new Scene(tabPane, MIN_WIDTH, MIN_HEIGHT);
        setScene(scene);
        logger.debug("ManagementStage scene created and set.");
    }

    /**
     * This is our new "remote control" method!
     * Any other part of the app (like an Auth tab) can call this
     * to tell the SheetsTab to re-check its auth status.
     */
    public void refreshAuthStatus() {
        logger.info("Auth status refresh requested for ManagementStage.");
        if (this.sheetsTab != null) {
            logger.debug("Forwarding auth refresh request to SheetsTab.");
            this.sheetsTab.checkAuthAndSetDisabled();
        } else {
            logger.warn("Auth status refresh requested, but sheetsTab instance is null.");
        }
    }

    public void addPlayerUpdateListener(PlayerUpdateListener listener) {
        logger.debug("Adding player update listener: {}", listener.getClass().getSimpleName());
        this.playerUpdateListeners.add(listener);
    }

    public void notifyPlayerUpdateListeners() {
        logger.info("Notifying {} player update listeners of a change.", playerUpdateListeners.size());
        // Tell all our listeners that something has changed!
        for (PlayerUpdateListener listener : playerUpdateListeners) {
            listener.onPlayerUpdate();
        }
    }

    /**
     * This is our new callback method!
     * It receives all the data from the SheetsTab after a successful import.
     * @param importedData The list of data records from the Google Sheet.
     */
    private void handleImportedData(List<SheetsServiceManager.PlayerData> importedData) {
        logger.info("Successfully received {} imported data entries from SheetsTab!", importedData.size());

        // TODO: This is where the magic happens, my darling!
        // 1. Loop through importedData
        // 2. Check for matching UUIDs in attendingPlayers / dmingPlayers
        // 3. If no UUID, use FuzzyStringMatcher to check for name matches
        // 4. Show dialogs to user to confirm matches or create new players
        // 5. Finally, call notifyPlayerUpdateListeners() after data is merged.

        // For now, let's just log them so you can see it's all working!
        importedData.forEach(data -> {
            logger.debug("Imported: PlayerUUID[{}], CharUUID[{}], Player[{}], Char[{}] from Tab[{}]",
                    data.playerUuid(), data.charUuid(), data.player(), data.character(), data.sourceTab());
        });

        // After you've processed the data (merged it, created new players, etc.),
        // you would then call this to make all the other tabs refresh!
        // notifyPlayerUpdateListeners();
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
                logger.info("Detaching tab: {}", title.getText());
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
                logger.debug("Tab '{}' is now in a separate stage.", title.getText());
                detachedStage.setOnCloseRequest(closeEvent -> {
                    logger.info("Re-attaching tab '{}' due to detached stage closing.", title.getText());
                    detachedTabMap.remove(tab);
                    if (!parent.getTabs().contains(tab)) {
                        int insertionIndex = Math.min(originalIndex, parent.getTabs().size());
                        parent.getTabs().add(insertionIndex, tab);
                        logger.debug("Tab re-inserted at index {}.", insertionIndex);
                    }
                });
                detachedStage.show();
            } else {
                logger.warn("Attempted to detach tab '{}', but it was the last tab remaining.", title.getText());
            }
        });
    }
}
