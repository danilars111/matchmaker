package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.forms.GroupFormView;
import org.poolen.frontend.gui.components.tables.PlayerRosterTableView;
import org.poolen.frontend.gui.listeners.PlayerUpdateListener;

import java.util.Map;
import java.util.UUID;

/**
 * A dedicated tab for creating, viewing, and managing groups that listens for player updates.
 */
public class GroupManagementTab extends Tab implements PlayerUpdateListener {

    private final GroupFormView groupForm;
    private final Map<UUID, Player> attendingPlayers;
    private final SplitPane root;
    private boolean isPlayerRosterVisible = false;

    public GroupManagementTab(Map<UUID, Player> attendingPlayers, Runnable notifyPlayerUpdateListeners) {
        super("Group Management");
        this.attendingPlayers = attendingPlayers;

        this.root = new SplitPane();
        this.groupForm = new GroupFormView(attendingPlayers);

        // The right side starts empty, waiting for its grand entrance!
        VBox rightPane = new VBox(10);
        rightPane.getChildren().add(new Label("A beautiful list of groups or players will go here!"));

        root.getItems().addAll(groupForm, rightPane);
        root.setDividerPositions(0.4);

        // --- The new button logic! ---
        groupForm.getShowPlayersButton().setOnAction(e -> {
            isPlayerRosterVisible = !isPlayerRosterVisible;
            if (isPlayerRosterVisible) {
                // When we show the roster, we create a fresh instance of it.
                PlayerRosterTableView rosterView = new PlayerRosterTableView(attendingPlayers, notifyPlayerUpdateListeners);
                // We replace the placeholder with our beautiful table.
                root.getItems().set(1, rosterView);
                groupForm.getShowPlayersButton().setText("Hide Players");
            } else {
                // When we hide it, we put the placeholder back.
                root.getItems().set(1, rightPane);
                groupForm.getShowPlayersButton().setText("Show Players");
            }
        });
        // -----------------------------

        this.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                groupForm.updateDmList(attendingPlayers);
            }
        });

        this.setContent(root);
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list in Group Management...");
        groupForm.updateDmList(attendingPlayers);
    }
}

