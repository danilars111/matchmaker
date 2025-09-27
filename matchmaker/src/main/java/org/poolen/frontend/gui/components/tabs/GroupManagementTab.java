package org.poolen.frontend.gui.components.tabs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.engine.GroupSuggester;
import org.poolen.backend.engine.Matchmaker;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.frontend.gui.components.stages.ExportGroupsStage;
import org.poolen.frontend.gui.components.views.GroupDisplayView;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.tables.PlayerRosterTableView;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;
import org.poolen.web.google.SheetsServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.poolen.frontend.gui.components.views.tables.PlayerRosterTableView.RosterMode.GROUP_ASSIGNMENT;

/**
 * A dedicated tab for creating, viewing, and managing groups that listens for player updates.
 */

@Component
@Lazy
public class GroupManagementTab extends Tab implements PlayerUpdateListener {

    private static final GroupFactory groupFactory = GroupFactory.getInstance();
    private GroupFormView groupForm;
    private SplitPane root;
    private PlayerRosterTableView rosterView;
    private boolean isPlayerRosterVisible = false;

    private Map<UUID, Player> newPartyMap;
    private  Map<UUID, Player> attendingPlayers;
    private  Map<UUID, Player> dmingPlayers;
    private  Runnable onPlayerListChanged;
    private SheetsServiceManager sheetsServiceManager;
    private List<Group> groups = new ArrayList<>();
    private GroupDisplayView groupDisplayView;
    private LocalDate eventDate;

    private final Map<Group, Player> dmsToReassignAsDm = new HashMap<>();
    private final Map<Group, Player> playersToPromoteToDm = new HashMap<>();
    private final Map<Group, Player> dmsToReassignAsPlayer = new HashMap<>();
    private final PlayerStore playerStore;
    private final SettingsStore settingsStore;
    private final Matchmaker matchmaker;


    @Autowired
    private GroupManagementTab(SheetsServiceManager sheetsServiceManager, PlayerStore playerStore,
                               Matchmaker matchmaker, SettingsStore settingsStore) {
        super("Group Management");

        this.sheetsServiceManager = sheetsServiceManager;
        this.playerStore = playerStore;
        this.settingsStore = settingsStore;
        this.matchmaker = matchmaker;
    }

    public void start() {

        this.root = new SplitPane();
        this.groupForm = new GroupFormView();
        this.groupDisplayView = new GroupDisplayView();
        this.newPartyMap = new HashMap<>();
        // Default the event date to the nearest upcoming Friday.
        this.eventDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

        this.rosterView = new PlayerRosterTableView(GROUP_ASSIGNMENT, attendingPlayers, dmingPlayers, playerStore);

        cleanUp();

        root.getItems().addAll(groupForm, groupDisplayView);
        root.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(groupForm, false);

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
        groupDisplayView.setOnDmUpdateRequest(this::handleDmUpdateRequestFromCard);
        groupDisplayView.setOnDateSelected(this::handleDateChange);
        groupDisplayView.setOnSuggestionRequest(() -> {
            GroupSuggester suggester = new GroupSuggester(attendingPlayers.values(), dmingPlayers.values());
            List<House> suggestions = suggester.suggestGroupThemes();
            groupDisplayView.displaySuggestions(suggestions);
        });
        groupDisplayView.setOnSuggestedGroupsCreate(this::handleCreateSuggestedGroups);
        groupDisplayView.setOnAutoPopulate(this::handleAutoPopulate);
        groupDisplayView.setOnExportRequest(this::handleExportRequest);

        rosterView.setOnPlayerAddRequest(this::handlePlayerAddRequest);

        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) updateDmList();
        });

        this.setContent(root);
    }

    private void handleDateChange(LocalDate newDate) {
        this.eventDate = newDate;
        for (Group group : groups) {
            group.setDate(newDate);
        }
        cleanUp();
    }

    private boolean handleDmUpdateRequestFromCard(Group groupToUpdate, Player newDm) {
        if (newDm != null && newDm.equals(groupToUpdate.getDungeonMaster())) {
            return false;
        }

        // --- Check if the selected player is already a DM in another group ---
        Optional<Group> dmSourceGroupOpt = groups.stream()
                .filter(g -> newDm != null && newDm.equals(g.getDungeonMaster()) && !g.equals(groupToUpdate))
                .findFirst();

        if (dmSourceGroupOpt.isPresent()) {
            Group sourceGroup = dmSourceGroupOpt.get();
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    newDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?",
                    this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                sourceGroup.removeDungeonMaster();
                groupToUpdate.setDungeonMaster(newDm);
                cleanUp();
                return true;
            } else {
                return false;
            }
        }

        // --- Check if the selected player is a party member in any group ---
        if (newDm != null) {
            Optional<Group> playerSourceGroupOpt = groups.stream()
                    .filter(g -> g.getParty().containsKey(newDm.getUuid()))
                    .findFirst();

            if (playerSourceGroupOpt.isPresent()) {
                Group playerSourceGroup = playerSourceGroupOpt.get();
                String message;
                if (playerSourceGroup.equals(groupToUpdate)) {
                    message = newDm.getName() + " is in this group's party. Promote them to DM? (This will remove them from the party)";
                } else {
                    message = newDm.getName() + " is in another group's party. Reassign them as DM for this group?";
                }

                ConfirmationDialog confirmation = new ConfirmationDialog(message, this.getTabPane());
                Optional<ButtonType> response = confirmation.showAndWait();

                if (response.isPresent() && response.get() == ButtonType.YES) {
                    playerSourceGroup.removePartyMember(newDm);
                    groupToUpdate.setDungeonMaster(newDm);
                    cleanUp();
                    return true;
                } else {
                    return false;
                }
            }
        }

        groupToUpdate.setDungeonMaster(newDm);
        cleanUp();
        return true;
    }


    private void updateDmList() {
        Group groupBeingEdited = (Group) groupForm.getItemBeingEdited();
        Set<Player> unavailablePlayers = new HashSet<>();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getDungeonMaster() != null) {
                unavailablePlayers.add(group.getDungeonMaster());
            }
        }
        groupForm.updateDmList(dmingPlayers, unavailablePlayers);
    }

    private void toggleRosterView() {
        isPlayerRosterVisible = !isPlayerRosterVisible;
        if (isPlayerRosterVisible) {
            root.getItems().set(1, rosterView);
            groupForm.getShowPlayersButton().setText("Hide Players");
        } else {
            cleanUp();
            root.getItems().set(1, groupDisplayView);
            groupForm.getShowPlayersButton().setText("Show Players");
        }
    }

    private void handleAutoPopulate() {
        boolean anyGroupWithoutDm = groups.stream().anyMatch(g -> g.getDungeonMaster() == null);
        if (anyGroupWithoutDm) {
            new ErrorDialog("Please assign a Dungeon Master to every group before auto-populating.", this.getTabPane()).showAndWait();
            return;
        }

        ConfirmationDialog confirmation = new ConfirmationDialog(
                "This will clear all current party members and generate new ones. Are you sure?", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Clear all existing party members for a clean slate.
                for (Group group : groups) {
                    new ArrayList<>(group.getParty().values()).forEach(group::removePartyMember);
                }
                this.groups = matchmaker.match(); // The matchmaker returns the populated list.
                cleanUp();
            }
        });
    }

    private void handleCreateSuggestedGroups(List<House> themes) {
        for (House theme : themes) {
            groups.add(groupFactory.create(null, List.of(theme), eventDate, new ArrayList<>()));
        }
        cleanUp();
    }

    private void handleGroupAction() {
        Group groupToEdit = (Group) groupForm.getItemBeingEdited();
        Player selectedDm = groupForm.getSelectedDm();
        if (groupToEdit == null) {
            dmsToReassignAsPlayer.keySet().forEach(Group::removeDungeonMaster);
            playersToPromoteToDm.forEach((sourceGroup, player) -> sourceGroup.removePartyMember(player));
            dmsToReassignAsDm.keySet().forEach(Group::removeDungeonMaster);
            new ArrayList<>(newPartyMap.values()).forEach(player -> {
                Group source = findGroupForPlayer(player);
                if (source != null) source.removePartyMember(player);
            });
            groups.add(groupFactory.create(selectedDm, groupForm.getSelectedHouses(), eventDate, new ArrayList<>(newPartyMap.values())));
        } else {
            dmsToReassignAsDm.forEach((source, dm) -> source.moveDungeonMasterTo(dm, groupToEdit));
            playersToPromoteToDm.forEach((source, player) -> {
                source.removePartyMember(player);
                groupToEdit.setDungeonMaster(player);
            });
            if (selectedDm != null && !playersToPromoteToDm.containsValue(selectedDm) && !dmsToReassignAsDm.containsValue(selectedDm)) {
                groupToEdit.setDungeonMaster(selectedDm);
            }
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
            groupDisplayView.updateGroups(groups, dmingPlayers, getAllAssignedDms(), eventDate);
            rosterView.setAllGroups(groups);
        }
    }

    private void handleDeleteFromCard(Group groupToDelete) {
        ConfirmationDialog confirmation = new ConfirmationDialog(
                "Are you sure you want to delete this group? This cannot be undone.", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                groups.remove(groupToDelete);
                cleanUp();
            }
        });
    }

    private void handleDeleteFromForm() {
        Group groupToDelete = (Group) groupForm.getItemBeingEdited();
        if (groupToDelete != null) {
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    "Are you sure you want to delete this group? This cannot be undone.", this.getTabPane());
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
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    player.getName() + " is a DM for another group. Reassign them as a player to this group?",
                    this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                dmsToReassignAsPlayer.put(dmSourceGroup, player);
                Group targetGroup = (Group) groupForm.getItemBeingEdited();
                if (targetGroup != null) targetGroup.addPartyMember(player);
                else newPartyMap.put(player.getUuid(), player);
                return true;
            } else {
                return false;
            }
        }

        Group playerSourceGroup = findGroupForPlayer(player);
        if (playerSourceGroup != null) {
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    player.getName() + " is already in another group. Reassign them?", this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                Group targetGroup = (Group) groupForm.getItemBeingEdited();
                if (targetGroup != null) {
                    playerSourceGroup.movePlayerTo(player, targetGroup);
                    rosterView.updateRoster();
                    groupDisplayView.updateGroups(groups, dmingPlayers, getAllAssignedDms(), eventDate);
                } else {
                    newPartyMap.put(player.getUuid(), player);
                }
                return true;
            } else {
                return false;
            }
        }

        Group targetGroup = (Group) groupForm.getItemBeingEdited();
        if (targetGroup != null) targetGroup.addPartyMember(player);
        else newPartyMap.put(player.getUuid(), player);
        return true;
    }

    private boolean handleDmSelectionRequest(Player selectedDm) {
        if (selectedDm == null) return true; // It's okay to unassign a DM

        Group groupBeingEdited = (Group) groupForm.getItemBeingEdited();
        Optional<Group> dmSourceGroupOpt = groups.stream()
                .filter(g -> selectedDm.equals(g.getDungeonMaster()) && !g.equals(groupBeingEdited))
                .findFirst();

        if (dmSourceGroupOpt.isPresent()) {
            Group sourceGroup = dmSourceGroupOpt.get();
            ConfirmationDialog confirmation = new ConfirmationDialog(
                    selectedDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?",
                    this.getTabPane());
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

            ConfirmationDialog confirmation = new ConfirmationDialog(message, this.getTabPane());
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

    private void handleExportRequest() {
        if (groups.isEmpty()) {
            new InfoDialog("There are no groups to export.", this.getTabPane()).showAndWait();
            return;
        }

        ExportGroupsStage exportStage = new ExportGroupsStage(groups, getTabPane().getScene().getWindow(), sheetsServiceManager, settingsStore);
        exportStage.show();
    }


    private Group findGroupForPlayer(Player player) {
        Group groupBeingEdited = (Group) groupForm.getItemBeingEdited();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getParty().containsKey(player.getUuid())) {
                return group;
            }
        }
        return null;
    }

    private Group findGroupDmForPlayer(Player player) {
        Group groupBeingEdited = (Group) groupForm.getItemBeingEdited();
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

    private Set<Player> getAllAssignedDms() {
        return groups.stream()
                .map(Group::getDungeonMaster)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
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
        groupDisplayView.updateGroups(groups, dmingPlayers, getAllAssignedDms(), eventDate);
        updateDmList();
    }

    @Override
    public void onPlayerUpdate() {
        System.out.println("Heard a player update! Refreshing DM list and roster...");
        updateDmList();
        rosterView.updateRoster();
    }

    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }

    public void setAttendingPlayers(Map<UUID, Player> attendingPlayers) {
        this.attendingPlayers = attendingPlayers;
    }

    public Map<UUID, Player> getDmingPlayers() {
        return dmingPlayers;
    }

    public void setDmingPlayers(Map<UUID, Player> dmingPlayers) {
        this.dmingPlayers = dmingPlayers;
    }

    public Runnable getOnPlayerListChanged() {
        return onPlayerListChanged;
    }

    public void setOnPlayerListChanged(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
    }
}

