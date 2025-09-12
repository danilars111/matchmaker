package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.tables.PlayerRosterTableView;
import org.poolen.frontend.gui.components.views.GroupDisplayView;
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
    private final PlayerRosterTableView rosterView;
    private boolean isPlayerRosterVisible = false;

    private final Map<UUID, Player> newPartyMap;
    private final Map<UUID, Player> attendingPlayers;
    private final List<Group> groups = new ArrayList<>();
    private final GroupDisplayView groupDisplayView;

    public GroupManagementTab(Map<UUID, Player> attendingPlayers, Runnable onPlayerListChanged) {
        super("Group Management");

        this.root = new SplitPane();
        this.attendingPlayers = attendingPlayers;
        this.groupForm = new GroupFormView(attendingPlayers);
        this.groupDisplayView = new GroupDisplayView();
        this.rosterView = new PlayerRosterTableView(PlayerRosterTableView.RosterMode.GROUP_ASSIGNMENT, attendingPlayers, onPlayerListChanged);
        this.newPartyMap = new HashMap<>();

        cleanUp();

        root.getItems().addAll(groupForm, groupDisplayView);
        root.setDividerPositions(0.4);

        // --- Event Wiring ---
        groupForm.getShowPlayersButton().setOnAction(e -> toggleRosterView());

        groupForm.setOnDmSelection(rosterView::setDmForNewGroup);

        groupForm.getCancelButton().setOnAction(e -> cleanUp());

        groupForm.getActionButton().setOnAction(e -> handleGroupAction());

        groupDisplayView.setOnGroupEdit(this::prepareForEdit);

        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) groupForm.updateDmList(attendingPlayers);
        });

        this.setContent(root);
    }

    private void toggleRosterView() {
        isPlayerRosterVisible = !isPlayerRosterVisible;
        if (isPlayerRosterVisible) {
            root.getItems().set(1, rosterView);
            groupForm.getShowPlayersButton().setText("Hide Players");
        } else {
            groupDisplayView.updateGroups(groups);
            root.getItems().set(1, groupDisplayView);
            groupForm.getShowPlayersButton().setText("Show Players");
        }
    }

    private void handleGroupAction() {
        Group groupToEdit = groupForm.getGroupBeingEdited();
        if (groupToEdit == null) { // Creating a new group
            groups.add(groupFactory.create(groupForm.getSelectedDm(), groupForm.getSelectedHouses(), LocalDate.now(), newPartyMap.values().stream().toList()));
        } else { // Updating an existing group
            groupToEdit.setDungeonMaster(groupForm.getSelectedDm());
            groupToEdit.setHouses(groupForm.getSelectedHouses());
            // The party is already updated in real-time by the checkboxes
        }
        cleanUp();
    }

    private void prepareForEdit(Group groupToEdit) {
        groupForm.populateForm(groupToEdit);
        rosterView.displayForGroup(groupToEdit); // Tell roster to use the group's real party map
        if (!isPlayerRosterVisible) {
            toggleRosterView();
        }
    }

    private void cleanUp() {
        if (isPlayerRosterVisible) {
            toggleRosterView();
        }
        groupForm.clearForm();
        newPartyMap.clear();
        rosterView.setPartyForNewGroup(newPartyMap); // Reset roster to use the new party map
        rosterView.setDmForNewGroup(null);
        groupDisplayView.updateGroups(groups);
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list in Group Management...");
        groupForm.updateDmList(attendingPlayers);
    }
}

