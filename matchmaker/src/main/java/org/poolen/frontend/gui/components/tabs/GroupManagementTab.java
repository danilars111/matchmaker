package org.poolen.frontend.gui.components.tabs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.backend.engine.GroupSuggester;
import org.poolen.backend.engine.Matchmaker;
import org.poolen.frontend.gui.components.dialogs.BaseDialog;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.stages.ExportGroupsStage;
import org.poolen.frontend.gui.components.views.GroupDisplayView;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.tables.rosters.GroupAssignmentRosterTableView;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.StageProvider;
import org.poolen.frontend.util.interfaces.providers.ViewProvider;
import org.poolen.web.google.SheetsServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A dedicated tab for creating, viewing, and managing groups that listens for player updates.
 */
public class GroupManagementTab extends Tab implements PlayerUpdateListener {

    private static final Logger logger = LoggerFactory.getLogger(GroupManagementTab.class);
    private static final GroupFactory groupFactory = GroupFactory.getInstance();

    private GroupFormView groupForm;
    private SplitPane root;
    private GroupAssignmentRosterTableView rosterView;
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
    private final Matchmaker matchmaker;
    private final StageProvider stageProvider;
    private final CoreProvider coreProvider;

    public GroupManagementTab(CoreProvider coreProvider, ViewProvider viewProvider, StageProvider stageProvider,
                              SheetsServiceManager sheetsServiceManager, Matchmaker matchmaker) {
        super("Group Management");

        this.sheetsServiceManager = sheetsServiceManager;
        this.matchmaker = matchmaker;
        this.stageProvider = stageProvider;
        this.coreProvider = coreProvider;

        this.root = new SplitPane();
        this.groupForm = viewProvider.getGroupFormView();
        this.groupDisplayView = viewProvider.getGroupDisplayView();
        this.rosterView = viewProvider.getGroupAssignmentRosterTableView();
    }
    public void init(Map<UUID, Player> attendingPlayers, Map<UUID, Player> dmingPlayers, Runnable onPlayerListChanged) {
        this.attendingPlayers = attendingPlayers;
        this.dmingPlayers = dmingPlayers;
        this.onPlayerListChanged = onPlayerListChanged;
        rosterView.init(attendingPlayers, dmingPlayers, onPlayerListChanged);
    }

    public void start() {
        if(attendingPlayers == null || dmingPlayers == null || onPlayerListChanged == null) {
            logger.error("GroupManagementTab.start() called before init(). Tab cannot be initialised.");
            throw new IllegalStateException("%s has not been initialized".formatted(this.getClass().getSimpleName()));
        }
        logger.info("Starting GroupManagementTab.");

        this.newPartyMap = new HashMap<>();
        // Default the event date to the nearest upcoming Friday.
        this.eventDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        logger.debug("Default event date set to: {}", eventDate);

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
            logger.debug("User requested group theme suggestions.");
            GroupSuggester suggester = new GroupSuggester(attendingPlayers.values(), dmingPlayers.values());
            List<House> suggestions = suggester.suggestGroupThemes();
            groupDisplayView.displaySuggestions(suggestions);
        });
        groupDisplayView.setOnSuggestedGroupsCreate(this::handleCreateSuggestedGroups);
        groupDisplayView.setOnAutoPopulate(this::handleAutoPopulate);
        groupDisplayView.setOnExportRequest(this::handleExportRequest);

        rosterView.setOnPlayerAddRequest(this::handlePlayerAddRequest);

        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) {
                logger.trace("GroupManagementTab selected. Updating DM list.");
                updateDmList();
            }
        });

        this.setContent(root);
        logger.info("GroupManagementTab initialised successfully.");
    }

    private void handleDateChange(LocalDate newDate) {
        logger.info("Event date changed to {}. Updating all groups and cleaning UI.", newDate);
        this.eventDate = newDate;
        for (Group group : groups) {
            group.setDate(newDate);
        }
        cleanUp();
    }

    private boolean handleDmUpdateRequestFromCard(Group groupToUpdate, Player newDm) {
        logger.info("Handling DM update request for group '{}' to new DM '{}'.", groupToUpdate.getUuid(), newDm != null ? newDm.getName() : "None");
        if (newDm != null && newDm.equals(groupToUpdate.getDungeonMaster())) {
            logger.debug("New DM is the same as the current DM. No action taken.");
            return false;
        }

        // --- Check if the selected player is already a DM in another group ---
        Optional<Group> dmSourceGroupOpt = groups.stream()
                .filter(g -> newDm != null && newDm.equals(g.getDungeonMaster()) && !g.equals(groupToUpdate))
                .findFirst();

        if (dmSourceGroupOpt.isPresent()) {
            Group sourceGroup = dmSourceGroupOpt.get();
            logger.debug("'{}' is already a DM for group '{}'. Prompting user for reassignment.", newDm.getName(), sourceGroup.getUuid());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                    newDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?",
                    this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed reassigning DM '{}' from group '{}' to group '{}'.", newDm.getName(), sourceGroup.getUuid(), groupToUpdate.getUuid());
                sourceGroup.removeDungeonMaster();
                groupToUpdate.setDungeonMaster(newDm);
                cleanUp();
                return true;
            } else {
                logger.info("User cancelled DM reassignment.");
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
                logger.debug("'{}' is a player in group '{}'. Prompting user for promotion/reassignment.", newDm.getName(), playerSourceGroup.getUuid());
                String message;
                if (playerSourceGroup.equals(groupToUpdate)) {
                    message = newDm.getName() + " is in this group's party. Promote them to DM? (This will remove them from the party)";
                } else {
                    message = newDm.getName() + " is in another group's party. Reassign them as DM for this group?";
                }

                ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                        message, this.getTabPane());
                Optional<ButtonType> response = confirmation.showAndWait();

                if (response.isPresent() && response.get() == ButtonType.YES) {
                    logger.info("User confirmed promoting/reassigning player '{}' to DM for group '{}'.", newDm.getName(), groupToUpdate.getUuid());
                    playerSourceGroup.removePartyMember(newDm);
                    groupToUpdate.setDungeonMaster(newDm);
                    cleanUp();
                    return true;
                } else {
                    logger.info("User cancelled player promotion/reassignment to DM.");
                    return false;
                }
            }
        }

        logger.info("Assigning '{}' as DM for group '{}'. No conflicts found.", newDm != null ? newDm.getName() : "None", groupToUpdate.getUuid());
        groupToUpdate.setDungeonMaster(newDm);
        cleanUp();
        return true;
    }


    private void updateDmList() {
        Group groupBeingEdited = groupForm.getItemBeingEdited();
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
        logger.debug("Toggling roster view. New visibility: {}", isPlayerRosterVisible);
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
        logger.info("User initiated auto-populate action.");
        boolean anyGroupWithoutDm = groups.stream().anyMatch(g -> g.getDungeonMaster() == null);
        if (anyGroupWithoutDm) {
            logger.warn("Auto-populate blocked: One or more groups are missing a DM.");
            coreProvider.createDialog(DialogType.ERROR,"Please assign a Dungeon Master to every group before auto-populating.", this.getTabPane()).showAndWait();
            return;
        }

        ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION,
                "This will clear all current party members and generate new ones. Are you sure?", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                logger.info("User confirmed auto-population. Clearing existing parties and running matchmaker.");
                // Clear all existing party members for a clean slate.
                for (Group group : groups) {
                    new ArrayList<>(group.getParty().values()).forEach(group::removePartyMember);
                }
                matchmaker.setPlayers(attendingPlayers.values().stream().filter(
                        player -> !dmingPlayers.containsKey(player.getUuid())).collect(Collectors.toList()));
                matchmaker.setGroups(groups);
                this.groups = matchmaker.match(); // The matchmaker returns the populated list.
                logger.info("Matchmaker finished. {} groups populated.", groups.size());
                cleanUp();
            } else {
                logger.info("User cancelled auto-population.");
            }
        });
    }

    private void handleCreateSuggestedGroups(List<House> themes) {
        logger.info("Creating {} suggested groups based on themes.", themes.size());
        for (House theme : themes) {
            groups.add(groupFactory.create(null, List.of(theme), eventDate, new ArrayList<>()));
        }
        cleanUp();
    }

    private void handleGroupAction() {
        Group groupToEdit = (Group) groupForm.getItemBeingEdited();
        Player selectedDm = groupForm.getSelectedDm();
        if (groupToEdit == null) {
            logger.info("Creating a new group with DM '{}' and {} party members.", selectedDm != null ? selectedDm.getName() : "None", newPartyMap.size());
            // Logic for applying pending changes before creation
            dmsToReassignAsPlayer.keySet().forEach(Group::removeDungeonMaster);
            playersToPromoteToDm.forEach((sourceGroup, player) -> sourceGroup.removePartyMember(player));
            dmsToReassignAsDm.keySet().forEach(Group::removeDungeonMaster);
            new ArrayList<>(newPartyMap.values()).forEach(player -> {
                Group source = findGroupForPlayer(player);
                if (source != null) source.removePartyMember(player);
            });
            groups.add(groupFactory.create(selectedDm, groupForm.getSelectedHouses(), eventDate, new ArrayList<>(newPartyMap.values())));
        } else {
            logger.info("Updating existing group '{}'.", groupToEdit.getUuid());
            // Logic for applying pending changes during update
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
        if (sourceGroupOpt.isEmpty()) {
            logger.warn("Attempted to move player from a non-existent source group with UUID: {}", sourceGroupUuid);
            return;
        }
        Group sourceGroup = sourceGroupOpt.get();
        Player playerToMove = sourceGroup.getParty().get(playerUuid);
        if (playerToMove != null) {
            logger.info("Moving player '{}' from group '{}' to group '{}'.", playerToMove.getName(), sourceGroup.getUuid(), targetGroup.getUuid());
            sourceGroup.removePartyMember(playerToMove);
            targetGroup.addPartyMember(playerToMove);
            groupDisplayView.updateGroups(groups, dmingPlayers, getAllAssignedDms(), eventDate);
            rosterView.setAllGroups(groups);
        } else {
            logger.warn("Attempted to move a non-existent player with UUID: {} from group {}", playerUuid, sourceGroupUuid);
        }
    }

    private void handleDeleteFromCard(Group groupToDelete) {
        logger.debug("User initiated delete for group '{}' from a group card. Showing confirmation.", groupToDelete.getUuid());
        ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,
                "Are you sure you want to delete this group? This cannot be undone.", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                logger.info("User confirmed deletion of group '{}'.", groupToDelete.getUuid());
                groups.remove(groupToDelete);
                cleanUp();
            } else {
                logger.info("User cancelled deletion of group '{}'.", groupToDelete.getUuid());
            }
        });
    }

    private void handleDeleteFromForm() {
        Group groupToDelete = (Group) groupForm.getItemBeingEdited();
        if (groupToDelete != null) {
            logger.debug("User initiated delete for group '{}' from the form. Showing confirmation.", groupToDelete.getUuid());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,
                    "Are you sure you want to delete this group? This cannot be undone.", this.getTabPane());
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    logger.info("User confirmed deletion of group '{}'.", groupToDelete.getUuid());
                    groups.remove(groupToDelete);
                    cleanUp();
                } else {
                    logger.info("User cancelled deletion of group '{}'.", groupToDelete.getUuid());
                }
            });
        } else {
            logger.warn("Delete from form called, but no group was being edited.");
        }
    }

    private boolean handlePlayerAddRequest(Player player) {
        Group dmSourceGroup = findGroupDmForPlayer(player);
        if (dmSourceGroup != null) {
            logger.debug("Player '{}' is a DM for group '{}'. Prompting user to reassign as player.", player.getName(), dmSourceGroup.getUuid());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,
                    player.getName() + " is a DM for another group. Reassign them as a player to this group?",
                    this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed reassigning DM '{}' as a player.", player.getName());
                dmsToReassignAsPlayer.put(dmSourceGroup, player);
                Group targetGroup = (Group) groupForm.getItemBeingEdited();
                if (targetGroup != null) targetGroup.addPartyMember(player);
                else newPartyMap.put(player.getUuid(), player);
                return true;
            } else {
                logger.info("User cancelled reassigning DM as player.");
                return false;
            }
        }

        Group playerSourceGroup = findGroupForPlayer(player);
        if (playerSourceGroup != null) {
            logger.debug("Player '{}' is already in group '{}'. Prompting user to reassign.", player.getName(), playerSourceGroup.getUuid());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,
                    player.getName() + " is already in another group. Reassign them?", this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed reassigning player '{}' from group '{}'.", player.getName(), playerSourceGroup.getUuid());
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
                logger.info("User cancelled reassigning player.");
                return false;
            }
        }
        logger.debug("Adding player '{}' to new/edited group. No conflicts found.", player.getName());
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
            logger.debug("Selected DM '{}' is already DM for group '{}'. Prompting for reassignment.", selectedDm.getName(), sourceGroup.getUuid());
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,
                    selectedDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?",
                    this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed reassigning DM '{}' from group '{}'. This will be applied on save.", selectedDm.getName(), sourceGroup.getUuid());
                dmsToReassignAsDm.put(sourceGroup, selectedDm);
                return true;
            } else {
                logger.info("User cancelled DM reassignment.");
                return false;
            }
        }

        Optional<Group> playerSourceGroupOpt = groups.stream()
                .filter(g -> g.getParty().containsKey(selectedDm.getUuid()))
                .findFirst();

        if (playerSourceGroupOpt.isPresent()) {
            Group playerSourceGroup = playerSourceGroupOpt.get();
            logger.debug("Selected DM '{}' is currently a player in group '{}'. Prompting for promotion.", selectedDm.getName(), playerSourceGroup.getUuid());
            String message;
            if (playerSourceGroup.equals(groupBeingEdited)) {
                message = selectedDm.getName() + " is in this group's party. Promote them to DM? (This will remove them from the party)";
            } else {
                message = selectedDm.getName() + " is in another group's party. Reassign them as DM for this group?";
            }

            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION,message, this.getTabPane());
            Optional<ButtonType> response = confirmation.showAndWait();

            if (response.isPresent() && response.get() == ButtonType.YES) {
                logger.info("User confirmed promoting player '{}' to DM. This will be applied on save.", selectedDm.getName());
                playersToPromoteToDm.put(playerSourceGroup, selectedDm);
                return true;
            } else {
                logger.info("User cancelled player promotion to DM.");
                return false;
            }
        }
        return true;
    }

    private void handleExportRequest() {
        if (groups.isEmpty()) {
            logger.warn("Export requested, but no groups exist.");
            coreProvider.createDialog(BaseDialog.DialogType.INFO,"There are no groups to export.", this.getTabPane()).showAndWait();
            return;
        }
        logger.info("Exporting {} groups.", groups.size());
        ExportGroupsStage exportStage = stageProvider.getExportGroupsStage();
        exportStage.init(groups, getTabPane().getScene().getWindow());
        exportStage.start();
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
        logger.info("Preparing form to edit group with UUID: {}", groupToEdit.getUuid());
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
        logger.debug("Running cleanup operation: clearing form, resetting selections, and updating views.");
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
        logger.info("Received player update notification. Refreshing DM list and roster view.");
        updateDmList();
        rosterView.updateRoster();
    }
    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }
    public Map<UUID, Player> getDmingPlayers() {
        return dmingPlayers;
    }
    public Runnable getOnPlayerListChanged() {
        return onPlayerListChanged;
    }
}
