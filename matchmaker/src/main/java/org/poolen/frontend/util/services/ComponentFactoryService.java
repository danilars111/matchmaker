package org.poolen.frontend.util.services;

import jakarta.annotation.PostConstruct;
import javafx.scene.Node;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.engine.Matchmaker;
import org.poolen.frontend.gui.components.dialogs.BaseDialog;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.frontend.gui.components.dialogs.UnsavedChangesDialog;
import org.poolen.frontend.gui.components.overlays.LoadingOverlay;
import org.poolen.frontend.gui.components.stages.ExportGroupsStage;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.PersistenceTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;
import org.poolen.frontend.gui.components.views.GroupDisplayView;
import org.poolen.frontend.gui.components.views.forms.CharacterFormView;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.forms.PlayerFormView;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.CharacterRosterTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.GroupAssignmentRosterTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.PlayerManagementRosterTableView;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.interfaces.providers.StageProvider;
import org.poolen.frontend.util.interfaces.providers.TabProvider;
import org.poolen.frontend.util.interfaces.providers.ViewProvider;
import org.poolen.web.google.SheetsServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ComponentFactoryService implements CoreProvider, StageProvider, TabProvider, ViewProvider {
    // Beans
    private final Store store;
    private final UiPersistenceService uiPersistenceService;
    private final CharacterFactory characterFactory;
    private final PlayerFactory playerFactory;
    private final SheetsServiceManager sheetsServiceManager;
    private final Matchmaker matchmaker;

    // Singleton components
    private ManagementStage managementStage;
    private  ExportGroupsStage exportGroupsStage;
    private CharacterManagementTab characterManagementTab;
    private GroupManagementTab groupManagementTab;
    private PersistenceTab persistenceTab;
    private PlayerManagementTab playerManagementTab;
    private SettingsTab settingsTab;
    private CharacterFormView characterFormView;
    private GroupFormView groupFormView;
    private PlayerFormView playerFormView;
    private CharacterRosterTableView characterRosterTableView;
    private GroupAssignmentRosterTableView groupAssignmentRosterTableView;
    private PlayerManagementRosterTableView playerManagementRosterTableView;
    private GroupTableView groupTableView;
    private GroupDisplayView groupDisplayView;

    @Autowired
    public ComponentFactoryService(Store store, UiPersistenceService uiPersistenceService, CharacterFactory characterFactory, PlayerFactory playerFactory, SheetsServiceManager sheetsServiceManager, Matchmaker matchmaker) {
        this.store = store;
        this.uiPersistenceService = uiPersistenceService;
        this.characterFactory = characterFactory;
        this.playerFactory = playerFactory;
        this.sheetsServiceManager = sheetsServiceManager;
        this.matchmaker = matchmaker;
    }

    /*******************************************************************************************
     **                                 Multi-ton Creators                                    **
     *******************************************************************************************/
    public LoadingOverlay createLoadingOverlay() {
        return new LoadingOverlay();
    }
    public SetupStage createSetupStage() {
        return new SetupStage(null, null);
    }
    public BaseDialog createDialog(DialogType type, String content, Node owner) {
        switch (type) {
            case CONFIRMATION:
                return new ConfirmationDialog(content, owner);
            case ERROR:
                return new ErrorDialog(content, owner);
            case INFO:
                return new InfoDialog(content, owner);
            case UNSAVED_CHANGES:
                return new UnsavedChangesDialog(content, owner);
            default:
                throw new IllegalArgumentException();
        }
    }


    /*******************************************************************************************
    **                                 Singleton Getters                                      **
    ********************************************************************************************/
    /************
     ** Stages **
     ************/
    public ManagementStage getManagementStage() {
        if (this.managementStage == null) {
            this.managementStage = new ManagementStage(this,this);
        }
        return this.managementStage;
    }
    public ExportGroupsStage getExportGroupsStage() {
        if(this.exportGroupsStage == null) {
            this.exportGroupsStage = new ExportGroupsStage(this, sheetsServiceManager, store);
        }
        return this.exportGroupsStage;
    }
    /**********
     ** Tabs **
     **********/
    public CharacterManagementTab getCharacterManagementTab() {
        if (this.characterManagementTab == null) {
            this.characterManagementTab = new CharacterManagementTab(this, store, store, this,
                    uiPersistenceService, characterFactory);
        }
        return this.characterManagementTab;
    }
    public GroupManagementTab getGroupManagementTab() {
        if (this.groupManagementTab == null) {
            this.groupManagementTab = new GroupManagementTab(this,this, this, sheetsServiceManager, matchmaker);
        }
        return this.groupManagementTab;
    }
    public PersistenceTab getPersistenceTab() {
        if (this.persistenceTab == null) {
            this.persistenceTab = new PersistenceTab(this, uiPersistenceService);
        }
        return this.persistenceTab;
    }
    public PlayerManagementTab getPlayerManagementTab() {
        if (this.playerManagementTab == null) {
            this.playerManagementTab = new PlayerManagementTab(this, store, this, uiPersistenceService, playerFactory);
        }
        return this.playerManagementTab;
    }
    public SettingsTab getSettingsTab() {
        if (this.settingsTab == null) {
            this.settingsTab = new SettingsTab(this, uiPersistenceService, store);
        }
        return this.settingsTab;
    }
    /***********
     ** Views **
     ***********/
    public CharacterFormView getCharacterFormView() {
        if (this.characterFormView == null) {
            this.characterFormView = new CharacterFormView(store);
        }
        return this.characterFormView;
    }
    public GroupFormView getGroupFormView() {
        if (this.groupFormView == null) {
            this.groupFormView = new GroupFormView();
        }
        return this.groupFormView;
    }
    public PlayerFormView getPlayerFormView() {
        if (this.playerFormView == null) {
            this.playerFormView = new PlayerFormView();
        }
        return this.playerFormView;
    }
    public CharacterRosterTableView getCharacterRosterTableView() {
        if (this.characterRosterTableView == null) {
            this.characterRosterTableView = new CharacterRosterTableView(store);
        }
        return this.characterRosterTableView;
    }
    public GroupAssignmentRosterTableView getGroupAssignmentRosterTableView() {
        if (this.groupAssignmentRosterTableView == null) {
            this.groupAssignmentRosterTableView = new GroupAssignmentRosterTableView();
        }
        return this.groupAssignmentRosterTableView;
    }
    public PlayerManagementRosterTableView getPlayerManagementRosterTableView() {
        if (this.playerManagementRosterTableView == null) {
            this.playerManagementRosterTableView = new PlayerManagementRosterTableView(store);
        }
        return this.playerManagementRosterTableView;
    }
    public GroupTableView getGroupTableView() {
        if (groupTableView == null) {
            this.groupTableView = new GroupTableView();
        }
        return groupTableView;
    }
    public GroupDisplayView getGroupDisplayView() {
        if (this.groupDisplayView == null) {
            this.groupDisplayView = new GroupDisplayView();
        }
        return this.groupDisplayView;
    }
}
