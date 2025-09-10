package org.poolen.frontend.gui.components.stages;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;

import java.util.Map;
import java.util.UUID;

/**
 * A dedicated pop-up window for all management tasks, organized into tabs.
 */
public class ManagementStage extends Stage {

    public ManagementStage(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("League Management");

        TabPane tabPane = new TabPane();

        // Create our beautiful new Player Management Tab
        PlayerManagementTab playerTab = new PlayerManagementTab(attendingPlayers, onUpdate);

        // You can add other tabs here in the future!
        Tab settingsTab = new Tab("Settings");
        settingsTab.setClosable(false); // You probably don't want to close tabs
        Tab dmToolsTab = new Tab("DM Tools");
        dmToolsTab.setClosable(false);

        tabPane.getTabs().addAll(playerTab, settingsTab, dmToolsTab);

        // Prevent tabs from being closed by the user.
        playerTab.setClosable(false);

        Scene scene = new Scene(tabPane, 800, 500);
        setScene(scene);
    }
}
