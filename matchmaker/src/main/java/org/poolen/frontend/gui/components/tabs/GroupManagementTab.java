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
import org.poolen.frontend.gui.interfaces.DmSelectRequestHandler;
import org.poolen.frontend.gui.interfaces.PlayerAddRequestHandler;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    // --- Beautiful maps to handle our reassignments! ---
    private final Map<Group, Player> dmsToReassignAsDm = new HashMap<>();
    private final Map<Group, Player> playersToPromoteToDm = new HashMap<>();
    private final Map<Group, Player> dmsToReassignAsPlayer = new HashMap<>();


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
        groupForm.setOnDmSelectionRequest(this::handleDmSelectionRequest);
        groupForm.getCancelButton().setOnAction(e -> cleanUp());
        groupForm.getActionButton().setOnAction(e -> handleGroupAction());
        groupForm.getDeleteButton().setOnAction(e -> handleDeleteFromForm());

        groupDisplayView.setOnGroupEdit(this::prepareForEdit);
        groupDisplayView.setOnGroupDelete(this::handleDeleteFromCard);
        groupDisplayView.setOnPlayerMove(this::handlePlayerMove);

        rosterView.setOnPlayerAddRequest(this::handlePlayerAddRequest);

        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) updateDmList();
        });

        this.setContent(root);
    }

    private void updateDmList() {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        Set<Player> unavailablePlayers = new HashSet<>();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getDungeonMaster() != null) {
                unavailablePlayers.add(group.getDungeonMaster());
            }
            unavailablePlayers.addAll(group.getParty().values());
        }
        groupForm.updateDmList(attendingPlayers, unavailablePlayers);
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
            // Handle DMs being demoted to players in our new group
            dmsToReassignAsPlayer.keySet().forEach(Group::removeDungeonMaster);
            // Handle players being promoted to DM of our new group
            playersToPromoteToDm.forEach((sourceGroup, player) -> sourceGroup.removePartyMember(player));
            // Handle DMs being reassigned as DM for our new group
            dmsToReassignAsDm.keySet().forEach(Group::removeDungeonMaster);
            // Handle regular players being moved from other parties
            new ArrayList<>(newPartyMap.values()).forEach(player -> {
                Group source = findGroupForPlayer(player);
                if (source != null) source.removePartyMember(player);
            });
            groups.add(groupFactory.create(groupForm.getSelectedDm(), groupForm.getSelectedHouses(), LocalDate.now(), new ArrayList<>(newPartyMap.values())));
        } else { // Updating an existing group
            dmsToReassignAsDm.forEach((source, dm) -> source.moveDungeonMasterTo(dm, groupToEdit));
            playersToPromoteToDm.forEach((source, player) -> {
                source.removePartyMember(player);
                groupToEdit.setDungeonMaster(player);
            });
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
            rosterView.setAllGroups(groups);
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

    private boolean handlePlayerAddRequest(Player player) {
        Group dmSourceGroup = findGroupDmForPlayer(player);
        if (dmSourceGroup != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    player.getName() + " is a DM for another group. Reassign them as a player to this group?",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                dmsToReassignAsPlayer.put(dmSourceGroup, player);
                Group targetGroup = groupForm.getGroupBeingEdited();
                if (targetGroup != null) targetGroup.addPartyMember(player);
                else newPartyMap.put(player.getUuid(), player);
                return true;
            } else {
                return false;
            }
        }

        Group playerSourceGroup = findGroupForPlayer(player);
        if (playerSourceGroup != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    player.getName() + " is already in another group. Reassign them?",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                Group targetGroup = groupForm.getGroupBeingEdited();
                if (targetGroup != null) {
                    playerSourceGroup.movePlayerTo(player, targetGroup);
                    rosterView.updateRoster();
                    groupDisplayView.updateGroups(groups);
                } else {
                    newPartyMap.put(player.getUuid(), player);
                }
                return true;
            } else {
                return false;
            }
        }

        Group targetGroup = groupForm.getGroupBeingEdited();
        if (targetGroup != null) targetGroup.addPartyMember(player);
        else newPartyMap.put(player.getUuid(), player);
        return true;
    }

    private boolean handleDmSelectionRequest(Player selectedDm) {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        Optional<Group> dmSourceGroupOpt = groups.stream()
                .filter(g -> selectedDm.equals(g.getDungeonMaster()) && !g.equals(groupBeingEdited))
                .findFirst();

        if (dmSourceGroupOpt.isPresent()) {
            Group sourceGroup = dmSourceGroupOpt.get();
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    selectedDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?",
                    ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                dmsToReassignAsDm.put(sourceGroup, selectedDm);
                return true;
            } else {
                return false;
            }
        }

        Optional<Group> playerSourceGroupOpt = groups.stream()
                .filter(g -> g.getParty().containsKey(selectedDm.getUuid()))
                .findFirst();

        if (playerSourceGroupOpt.isPresent()) {
            Group playerSourceGroup = playerSourceGroupOpt.get();
            String message;
            if (playerSourceGroup.equals(groupBeingEdited)) {
                message = selectedDm.getName() + " is in this group's party. Promote them to DM? (This will remove them from the party)";
            } else {
                message = selectedDm.getName() + " is in another group's party. Reassign them as DM for this group?";
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(this.getTabPane().getScene().getWindow());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                playersToPromoteToDm.put(playerSourceGroup, selectedDm);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private Group findGroupForPlayer(Player player) {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getParty().containsKey(player.getUuid())) {
                return group;
            }
        }
        return null;
    }

    private Group findGroupDmForPlayer(Player player) {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (player.equals(group.getDungeonMaster())) {
                return group;
            }
        }
        return null;
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
        dmsToReassignAsDm.clear();
        playersToPromoteToDm.clear();
        dmsToReassignAsPlayer.clear();
        rosterView.setPartyForNewGroup(newPartyMap);
        rosterView.setDmForNewGroup(null);
        rosterView.setAllGroups(groups);
        groupDisplayView.updateGroups(groups);
        updateDmList();
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list...");
        updateDmList();
    }
}

