package org.poolen.frontend.gui.components.tabs;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.stage.Window;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.backend.db.interfaces.store.PlayerStoreProvider;
import org.poolen.backend.db.store.PlayerStore;
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
import org.poolen.frontend.util.services.GroupPersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Updated GroupManagementTab to handle maxSize and locked properties during group actions.
 */
public class GroupManagementTab extends Tab implements PlayerUpdateListener {

    private static final Logger logger = LoggerFactory.getLogger(GroupManagementTab.class);
    private final GroupFactory groupFactory;

    private GroupFormView groupForm;
    private SplitPane root;
    private GroupAssignmentRosterTableView rosterView;
    private boolean isPlayerRosterVisible = false;

    private Map<UUID, Player> newPartyMap;
    private Runnable onPlayerListChanged;
    private List<Group> groups = new ArrayList<>();
    private GroupDisplayView groupDisplayView;
    private LocalDate eventDate;

    private final Map<Group, Player> dmsToReassignAsDm = new HashMap<>();
    private final Map<Group, Player> playersToPromoteToDm = new HashMap<>();
    private final Map<Group, Player> dmsToReassignAsPlayer = new HashMap<>();
    private final Matchmaker matchmaker;
    private final StageProvider stageProvider;
    private final CoreProvider coreProvider;
    private final PlayerStore playerStore;
    private final UiTaskExecutor uiTaskExecutor;
    private final GroupPersistenceService groupPersistenceService;

    public GroupManagementTab(CoreProvider coreProvider, ViewProvider viewProvider, StageProvider stageProvider,
                              PlayerStoreProvider playerStoreProvider, UiTaskExecutor uiTaskExecutor,
                              Matchmaker matchmaker, GroupFactory groupFactory, GroupPersistenceService groupPersistenceService) {
        super("Group Management");
        this.matchmaker = matchmaker;
        this.stageProvider = stageProvider;
        this.playerStore = playerStoreProvider.getPlayerStore();
        this.coreProvider = coreProvider;
        this.uiTaskExecutor = uiTaskExecutor;
        this.groupFactory = groupFactory;
        this.groupPersistenceService = groupPersistenceService;
        this.root = new SplitPane();
        this.groupForm = viewProvider.getGroupFormView();
        this.groupDisplayView = viewProvider.getGroupDisplayView();
        this.rosterView = viewProvider.getGroupAssignmentRosterTableView();
    }

    public void init(Runnable onPlayerListChanged) {
        this.onPlayerListChanged = onPlayerListChanged;
        rosterView.init(onPlayerListChanged);
    }

    public void start() {
        if(onPlayerListChanged == null) {
            throw new IllegalStateException("%s has not been initialized".formatted(this.getClass().getSimpleName()));
        }
        this.newPartyMap = new HashMap<>();
        this.eventDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

        if (groupPersistenceService.hasSaveFile()) {
            groups = this.groupPersistenceService.loadGroups();
            if (groups != null && !groups.isEmpty()) {
                Platform.runLater(() -> {
                    ConfirmationDialog dialog = new ConfirmationDialog("Unpublished Session found! Do you want to recover it?", this.groupDisplayView);
                    dialog.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            LocalDate date = groups.get(0).getDate();
                            if (date == null) date = LocalDate.now();
                            handleDateChange(date);
                        }
                    });
                });
            }
        }

        cleanUp();
        root.getItems().addAll(groupForm, groupDisplayView);
        root.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(groupForm, false);

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
        groupDisplayView.setOnLocationUpdate(this::handleLocationUpdateFromCard);
        groupDisplayView.setOnLockedUpdate(this::handleLockedUpdateFromCard);
        groupDisplayView.setOnDateSelected(this::handleDateChange);
        groupDisplayView.setOnSuggestionRequest(() -> {
            GroupSuggester suggester = new GroupSuggester(playerStore.getAttendingPlayers().values(), playerStore.getDmingPlayers().values());
            Window parentWindow = (getTabPane() != null && getTabPane().getScene() != null) ? getTabPane().getScene().getWindow() : null;
            uiTaskExecutor.execute(parentWindow, "Suggesting groups...", "Group suggestions found..", (updater) -> suggester.suggestGroupThemes(updater), (result) -> groupDisplayView.displaySuggestions(result));
        });
        groupDisplayView.setOnSuggestedGroupsCreate(themes -> {
            for (var theme : themes) {
                groups.add(groupFactory.create(null, null, List.of(theme), eventDate, null, null, false, new ArrayList<>()));
            }
            cleanUp();
        });
        groupDisplayView.setOnAutoPopulate(this::handleAutoPopulate);
        groupDisplayView.setOnExportRequest(this::handleExportRequest);
        rosterView.setOnPlayerAddRequest(this::handlePlayerAddRequest);

        this.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) updateDmList();
        });

        this.setContent(root);
    }

    private void handleGroupAction() {
        Group groupToEdit = groupForm.getGroupBeingEdited();
        Player selectedDm = groupForm.getSelectedDm();
        String selectedLocation = groupForm.getSelectedLocation();
        Integer selectedMaxSize = groupForm.getSelectedMaxSize();
        boolean selectedLocked = groupForm.isLocked();

        if (groupToEdit == null) {
            logger.info("Creating a new group with maxSize {} and locked state {}.", selectedMaxSize, selectedLocked);
            dmsToReassignAsPlayer.keySet().forEach(Group::removeDungeonMaster);
            playersToPromoteToDm.forEach((sourceGroup, player) -> sourceGroup.removePartyMember(player));
            dmsToReassignAsDm.keySet().forEach(Group::removeDungeonMaster);
            new ArrayList<>(newPartyMap.values()).forEach(player -> {
                Group source = findGroupForPlayer(player);
                if (source != null) source.removePartyMember(player);
            });
            groups.add(groupFactory.create(null, selectedDm, groupForm.getSelectedHouses(), eventDate, selectedMaxSize,
                    selectedLocation, selectedLocked, new ArrayList<>(newPartyMap.values())));
        } else {
            logger.info("Updating existing group '{}'.", groupToEdit.getUuid());
            dmsToReassignAsDm.forEach((source, dm) -> source.moveDungeonMasterTo(dm, groupToEdit));
            playersToPromoteToDm.forEach((source, player) -> {
                source.removePartyMember(player);
                groupToEdit.setDungeonMaster(player);
            });
            if (selectedDm != null && !playersToPromoteToDm.containsValue(selectedDm) && !dmsToReassignAsDm.containsValue(selectedDm)) {
                groupToEdit.setDungeonMaster(selectedDm);
            }
            groupToEdit.setHouses(groupForm.getSelectedHouses());
            groupToEdit.setLocation(selectedLocation);
            groupToEdit.setMaxSize(selectedMaxSize);
            groupToEdit.isLocked(selectedLocked);
        }
        cleanUp();
    }

    private void handleDateChange(LocalDate newDate) {
        this.eventDate = newDate;
        for (Group group : groups) group.setDate(newDate);
        cleanUp();
    }

    private boolean handleDmUpdateRequestFromCard(Group groupToUpdate, Player newDm) {
        if (newDm != null && newDm.equals(groupToUpdate.getDungeonMaster())) return false;
        Optional<Group> dmSourceGroupOpt = groups.stream().filter(g -> newDm != null && newDm.equals(g.getDungeonMaster()) && !g.equals(groupToUpdate)).findFirst();
        if (dmSourceGroupOpt.isPresent()) {
            Group sourceGroup = dmSourceGroupOpt.get();
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION, newDm.getName() + " is already a DM for another group. Reassign them as the DM for this group?", this.getTabPane());
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                sourceGroup.removeDungeonMaster();
                groupToUpdate.setDungeonMaster(newDm);
                cleanUp();
                return true;
            }
            return false;
        }
        if (newDm != null) {
            Optional<Group> playerSourceGroupOpt = groups.stream().filter(g -> g.getParty().containsKey(newDm.getUuid())).findFirst();
            if (playerSourceGroupOpt.isPresent()) {
                Group playerSourceGroup = playerSourceGroupOpt.get();
                String message = playerSourceGroup.equals(groupToUpdate) ? newDm.getName() + " is in this group's party. Promote them to DM?" : newDm.getName() + " is in another group's party. Reassign them as DM?";
                ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION, message, this.getTabPane());
                if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                    playerSourceGroup.removePartyMember(newDm);
                    groupToUpdate.setDungeonMaster(newDm);
                    cleanUp();
                    return true;
                }
                return false;
            }
        }
        groupToUpdate.setDungeonMaster(newDm);
        cleanUp();
        return true;
    }

    private boolean handleLocationUpdateFromCard(Group groupToUpdate, String newLocation) {
        groupToUpdate.setLocation(newLocation);
        return true;
    }

    private boolean handleLockedUpdateFromCard(Group groupToUpdate, boolean locked) {
        logger.info("Updating locked status for group '{}' from card view to '{}'.", groupToUpdate.getUuid(), locked);
        groupToUpdate.isLocked(locked);
        return true;
    }

    private void updateDmList() {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        Set<Player> unavailablePlayers = new HashSet<>();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getDungeonMaster() != null) unavailablePlayers.add(group.getDungeonMaster());
        }
        groupForm.updateDmList(unavailablePlayers);
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
        if (groups.stream().anyMatch(g -> g.getDungeonMaster() == null)) {
            coreProvider.createDialog(DialogType.ERROR,"Please assign a DM to every group before auto-populating.", this.getTabPane()).showAndWait();
            return;
        }
        ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(DialogType.CONFIRMATION, "This will clear current party members for unlocked groups and generate new ones. Proceed?", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // We only clear groups that are NOT locked.
                for (Group group : groups) {
                    if (!group.isLocked()) {
                        new ArrayList<>(group.getParty().values()).forEach(group::removePartyMember);
                    }
                }
                matchmaker.setPlayers(playerStore.getAttendingPlayers().values().stream().filter(p -> !playerStore.getDmingPlayers().containsKey(p.getUuid())).collect(Collectors.toList()));
                matchmaker.setGroups(groups);
                Window parentWindow = (getTabPane() != null && getTabPane().getScene() != null) ? getTabPane().getScene().getWindow() : null;
                uiTaskExecutor.execute(parentWindow, "Matching Groups...", "Groups Matched Successfully..", (updater) -> matchmaker.match(), (result) -> {
                    this.groups = result;
                    cleanUp();
                });
            }
        });
    }

    private void handlePlayerMove(UUID sourceGroupUuid, UUID playerUuid, Group targetGroup) {
        groups.stream().filter(g -> g.getUuid().equals(sourceGroupUuid)).findFirst().ifPresent(sourceGroup -> {
            Player playerToMove = sourceGroup.getParty().get(playerUuid);
            if (playerToMove != null) {
                sourceGroup.removePartyMember(playerToMove);
                targetGroup.addPartyMember(playerToMove);
                groupDisplayView.updateGroups(groups, eventDate);
                rosterView.setAllGroups(groups);
            }
        });
    }

    private void handleDeleteFromCard(Group groupToDelete) {
        ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION, "Are you sure you want to delete this group?", this.getTabPane());
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                groups.remove(groupToDelete);
                cleanUp();
            }
        });
    }

    private void handleDeleteFromForm() {
        Group groupToDelete = groupForm.getGroupBeingEdited();
        if (groupToDelete != null) handleDeleteFromCard(groupToDelete);
    }

    private boolean handlePlayerAddRequest(Player player) {
        Group dmSourceGroup = findGroupDmForPlayer(player);
        if (dmSourceGroup != null) {
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION, player.getName() + " is a DM for another group. Reassign as a player?", this.getTabPane());
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                dmsToReassignAsPlayer.put(dmSourceGroup, player);
                Group targetGroup = groupForm.getGroupBeingEdited();
                if (targetGroup != null) targetGroup.addPartyMember(player);
                else newPartyMap.put(player.getUuid(), player);
                return true;
            }
            return false;
        }
        Group playerSourceGroup = findGroupForPlayer(player);
        if (playerSourceGroup != null) {
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION, player.getName() + " is already in another group. Reassign them?", this.getTabPane());
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                Group targetGroup = groupForm.getGroupBeingEdited();
                if (targetGroup != null) {
                    playerSourceGroup.movePlayerTo(player, targetGroup);
                    rosterView.updateRoster();
                    groupDisplayView.updateGroups(groups, eventDate);
                } else {
                    newPartyMap.put(player.getUuid(), player);
                }
                return true;
            }
            return false;
        }
        Group targetGroup = groupForm.getGroupBeingEdited();
        if (targetGroup != null) targetGroup.addPartyMember(player);
        else newPartyMap.put(player.getUuid(), player);
        return true;
    }

    private boolean handleDmSelectionRequest(Player selectedDm) {
        if (selectedDm == null) return true;
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        Optional<Group> dmSourceGroupOpt = groups.stream().filter(g -> selectedDm.equals(g.getDungeonMaster()) && !g.equals(groupBeingEdited)).findFirst();
        if (dmSourceGroupOpt.isPresent()) {
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION, selectedDm.getName() + " is already a DM for another group. Reassign?", this.getTabPane());
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                dmsToReassignAsDm.put(dmSourceGroupOpt.get(), selectedDm);
                return true;
            }
            return false;
        }
        Optional<Group> playerSourceGroupOpt = groups.stream().filter(g -> g.getParty().containsKey(selectedDm.getUuid())).findFirst();
        if (playerSourceGroupOpt.isPresent()) {
            String message = playerSourceGroupOpt.get().equals(groupBeingEdited) ? selectedDm.getName() + " is in this group's party. Promote to DM?" : selectedDm.getName() + " is in another group's party. Reassign as DM?";
            ConfirmationDialog confirmation = (ConfirmationDialog) coreProvider.createDialog(BaseDialog.DialogType.CONFIRMATION, message, this.getTabPane());
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                playersToPromoteToDm.put(playerSourceGroupOpt.get(), selectedDm);
                return true;
            }
            return false;
        }
        return true;
    }

    private void handleExportRequest() {
        if (groups.isEmpty()) {
            coreProvider.createDialog(BaseDialog.DialogType.INFO,"There are no groups to export.", this.getTabPane()).showAndWait();
            return;
        }
        ExportGroupsStage exportStage = stageProvider.getExportGroupsStage();
        exportStage.init(groups, getTabPane().getScene().getWindow());
        exportStage.start();
        exportStage.show();
    }

    private Group findGroupForPlayer(Player player) {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (group.getParty().containsKey(player.getUuid())) return group;
        }
        return null;
    }

    private Group findGroupDmForPlayer(Player player) {
        Group groupBeingEdited = groupForm.getGroupBeingEdited();
        for (Group group : groups) {
            if (group.equals(groupBeingEdited)) continue;
            if (player.equals(group.getDungeonMaster())) return group;
        }
        return null;
    }

    private void prepareForEdit(Group groupToEdit) {
        groupForm.populateForm(groupToEdit);
        rosterView.displayForGroup(groupToEdit);
        if (!isPlayerRosterVisible) toggleRosterView();
    }

    private void cleanUp() {
        if (isPlayerRosterVisible) toggleRosterView();
        groupForm.clearForm();
        newPartyMap.clear();
        dmsToReassignAsDm.clear();
        playersToPromoteToDm.clear();
        dmsToReassignAsPlayer.clear();
        rosterView.setPartyForNewGroup(newPartyMap);
        rosterView.setDmForNewGroup(null);
        rosterView.setAllGroups(groups);
        groupDisplayView.updateGroups(groups, eventDate);
        updateDmList();
    }

    @Override
    public void onPlayerUpdate() {
        updateDmList();
        rosterView.updateRoster();
    }

    public Runnable getOnPlayerListChanged() { return onPlayerListChanged; }
}