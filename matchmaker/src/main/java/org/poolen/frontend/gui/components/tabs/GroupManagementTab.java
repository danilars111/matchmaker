package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.frontend.gui.components.forms.GroupFormView;
import org.poolen.frontend.gui.components.tables.PlayerRosterTableView;
import org.poolen.frontend.gui.listeners.PlayerUpdateListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dedicated tab for creating, viewing, and managing groups that listens for player updates.
 */
public class GroupManagementTab extends Tab implements PlayerUpdateListener {

    private static final GroupFactory groupFactory = GroupFactory.getInstance();
    private final GroupFormView groupForm;
    private final SplitPane root;
    private final PlayerRosterTableView rosterView; // Our one and only roster view!
    private boolean isPlayerRosterVisible = false;

    // --- A beautiful map just for our new party, as you wanted! ---
    private Map<UUID, Player> newPartyMap;
    private Map<UUID, Player> attendingPlayers;
    private List<Group> groups = new ArrayList<>();
    private List<Label> tmpGroupLabels = new ArrayList<>();

    public GroupManagementTab(Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super("Group Management");

        this.root = new SplitPane();
        this.attendingPlayers = attendingPlayers;
        this.groupForm = new GroupFormView(attendingPlayers);

        VBox rightPane = new VBox(10);
        groups.forEach(group -> rightPane.getChildren().add(new Label(group.toString())));
       // rightPane.getChildren().add(new Label("A beautiful list of groups or players will go here!"));

        // --- We create our roster once and for all! ---
        this.rosterView = new PlayerRosterTableView(PlayerRosterTableView.RosterMode.GROUP_ASSIGNMENT, attendingPlayers, onPlayerListChanged);

        // We still create a fresh map for our new party!
        this.newPartyMap = new HashMap<>();

        cleanUp(); // We start with a clean slate

        // We tell our persistent roster about the new map and the selected DM
        rosterView.setPartyForNewGroup(newPartyMap);
        rosterView.setDmForNewGroup(groupForm.getSelectedDm());

        root.getItems().addAll(groupForm, rightPane);
        root.setDividerPositions(0.4);

        groupForm.getShowPlayersButton().setOnAction(e -> {
            isPlayerRosterVisible = !isPlayerRosterVisible;
            if (isPlayerRosterVisible) {
                root.getItems().set(1, rosterView); // Show our beautiful roster
                groupForm.getShowPlayersButton().setText("Hide Players");
            } else {
                groups.forEach(group -> tmpGroupLabels.add(new Label(group.toString())));
                tmpGroupLabels.forEach(label -> rightPane.getChildren().add(label));
                root.getItems().set(1, rightPane);
                groupForm.getShowPlayersButton().setText("Show Players");
                tmpGroupLabels.clear();
            }
        });

        // When the DM changes, we tell our roster to update its filter.
        groupForm.setOnDmSelection(rosterView::setDmForNewGroup);

        // When the cancel button is clicked, we just clear the form.
        groupForm.getCancelButton().setOnAction(e ->  cleanUp() );

        groupForm.getActionButton().setOnAction(e -> {
            groups.add(groupFactory.create(groupForm.getSelectedDm(), groupForm.getSelectedHouses(), LocalDate.now(), newPartyMap.values().stream().toList()));
            cleanUp();
        });

        this.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                groupForm.updateDmList(attendingPlayers);
            }
        });

        this.setContent(root);
    }

    private void cleanUp() {
        groupForm.getShowPlayersButton().fire();
        groupForm.clearForm();
        newPartyMap.clear();
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list in Group Management...");
        groupForm.updateDmList(attendingPlayers);
    }
}

