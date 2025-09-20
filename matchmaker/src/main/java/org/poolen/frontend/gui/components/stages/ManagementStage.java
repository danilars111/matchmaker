package org.poolen.frontend.gui.components.stages;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dedicated pop-up window for all management tasks, organized into detachable tabs.
 */
public class ManagementStage extends Stage {

    private static final Map<Tab, Stage> detachedTabs = new HashMap<>();
    private final List<PlayerUpdateListener> playerUpdateListeners = new ArrayList<>();
    private final Map<UUID, Player> dmingPlayers;
    private final Map<UUID, Player> attendingPlayers;

    public ManagementStage() {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Management");

        this.dmingPlayers = new HashMap<>();
        this.attendingPlayers = new HashMap<>();

        TabPane tabPane = new TabPane();

        PlayerManagementTab playerTab = new PlayerManagementTab(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);
        GroupManagementTab groupTab = new GroupManagementTab(attendingPlayers, dmingPlayers, this::notifyPlayerUpdateListeners);
        addPlayerUpdateListener(groupTab);

        CharacterManagementTab characterTab = new CharacterManagementTab(this::notifyPlayerUpdateListeners);
        Tab settingsTab = new SettingsTab();
        Tab persistenceTab = new Tab("Persistence");
        persistenceTab.setContent(new Label("Persistence will go here!"));

        makeTabDetachable(playerTab);
        makeTabDetachable(characterTab);
        makeTabDetachable(groupTab);
        makeTabDetachable(settingsTab);
        makeTabDetachable(persistenceTab);

        tabPane.getTabs().addAll(playerTab, characterTab, groupTab, settingsTab, persistenceTab);

        // --- Our new wiring logic! ---
        characterTab.getCharacterForm().setOnOpenPlayerRequestHandler(player -> {
            if (detachedTabs.containsKey(playerTab)) {
                // The tab has been detached, so focus its window
                Stage detachedPlayerStage = detachedTabs.get(playerTab);
                detachedPlayerStage.toFront();
                detachedPlayerStage.requestFocus();
            } else {
                // The tab is still in the main pane, so select it
                tabPane.getSelectionModel().select(playerTab);
            }
            // In either case, tell the player tab to edit the player
            playerTab.editPlayer(player);
        });

        this.setOnCloseRequest(event -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to close the management window? This will close all detached tabs as well.",
                    ButtonType.YES, ButtonType.CANCEL);
            confirmation.initOwner(this);
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    new ArrayList<>(detachedTabs.values()).forEach(Stage::close);
                } else {
                    event.consume();
                }
            });
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
        tab.setText(null);

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
                detachedTabs.put(tab, detachedStage);
                detachedStage.setOnCloseRequest(closeEvent -> {
                    detachedTabs.remove(tab);
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

