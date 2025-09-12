package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.tables.PlayerRosterTableView;
import org.poolen.frontend.gui.components.views.GroupDisplayView;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        groupForm.getDeleteButton().setOnAction(e -> handleDeleteFromForm());

        groupDisplayView.setOnGroupEdit(this::prepareForEdit);
        groupDisplayView.setOnGroupDelete(this::handleDeleteFromCard);
        groupDisplayView.setOnPlayerMove(this::handlePlayerMove);

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
        if (groupToEdit == null) {
            groups.add(groupFactory.create(groupForm.getSelectedDm(), groupForm.getSelectedHouses(), LocalDate.now(), newPartyMap.values().stream().toList()));
        } else {
            groupToEdit.setDungeonMaster(groupForm.getSelectedDm());
            groupToEdit.setHouses(groupForm.getSelectedHouses());
        }
        cleanUp();
    }

    private void handlePlayerMove(UUID sourceGroupUuid, UUID playerUuid, Group targetGroup) {
        Optional<Group> sourceGroupOpt = groups.stream().filter(g -> g.getUuid().equals(sourceGroupUuid)).findFirst();
        if (sourceGroupOpt.isEmpty()) return;

        Group sourceGroup = sourceGroupOpt.get();
        Player playerToMove = sourceGroup.getParty().get(playerUuid);

        if (playerToMove != null) {
            sourceGroup.removePartyMember(playerToMove);
            targetGroup.addPartyMember(playerToMove);
            groupDisplayView.updateGroups(groups);
            rosterView.setAllGroups(groups); // Keep the roster informed!
        }
    }

    private void handleDeleteFromCard(Group groupToDelete) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this group? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirmation.initOwner(this.getTabPane().getScene().getWindow());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                groups.remove(groupToDelete);
                cleanUp();
            }
        });
    }

    private void handleDeleteFromForm() {
        Group groupToDelete = groupForm.getGroupBeingEdited();
        if (groupToDelete != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to delete this group? This cannot be undone.",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    groups.remove(groupToDelete);
                    cleanUp();
                }
            });
        }
    }

    private void prepareForEdit(Group groupToEdit) {
        groupForm.populateForm(groupToEdit);
        rosterView.displayForGroup(groupToEdit);
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
        rosterView.setPartyForNewGroup(newPartyMap);
        rosterView.setDmForNewGroup(null);
        rosterView.setAllGroups(groups); // Always keep the roster's knowledge of groups up to date!
        groupDisplayView.updateGroups(groups);
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list in Group Management...");
        groupForm.updateDmList(attendingPlayers);
    }
}

