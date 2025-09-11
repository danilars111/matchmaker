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
    private PlayerRosterTableView rosterView; // Keep a reference to our lovely table

    public GroupManagementTab(Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super("Group Management");
        this.attendingPlayers = attendingPlayers;

        this.root = new SplitPane();
        this.groupForm = new GroupFormView(attendingPlayers);

        VBox rightPanePlaceholder = new VBox(10);
        rightPanePlaceholder.getChildren().add(new Label("A beautiful list of groups or players will go here!"));

        root.getItems().addAll(groupForm, rightPanePlaceholder);
        root.setDividerPositions(0.4);

        groupForm.getShowPlayersButton().setOnAction(e -> {
            if (rosterView == null) {
                // Create and show the roster
                rosterView = new PlayerRosterTableView(PlayerRosterTableView.RosterMode.GROUP_ASSIGNMENT, attendingPlayers, onPlayerListChanged);
                rosterView.setDmForNewGroup(groupForm.getSelectedDm()); // Set initial DM
                root.getItems().set(1, rosterView);
                groupForm.getShowPlayersButton().setText("Hide Players");
            } else {
                // Hide the roster and put back the placeholder
                root.getItems().set(1, rightPanePlaceholder);
                groupForm.getShowPlayersButton().setText("Show Players");
                rosterView = null;
            }
        });

        // Add our beautiful new listener!
        groupForm.getDmComboBox().valueProperty().addListener((obs, oldDm, newDm) -> {
            if (rosterView != null) {
                rosterView.setDmForNewGroup(newDm);
            }
        });


        this.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                groupForm.updateDmList(attendingPlayers);
            }
        });

        this.setContent(root);
    }

    /**
     * This is the heart of our beautiful real-time update system!
     */
    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list in Group Management...");
        groupForm.updateDmList(attendingPlayers);
    }
}
