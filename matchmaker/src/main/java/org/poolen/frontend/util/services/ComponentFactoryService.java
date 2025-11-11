package org.poolen.frontend.util.services;

import javafx.scene.Node;
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
import org.poolen.frontend.gui.components.stages.ImportMatcherStage;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.gui.components.stages.email.AccessRequestStage;
import org.poolen.frontend.gui.components.stages.email.BugReportStage;
import org.poolen.frontend.gui.components.stages.email.CrashReportStage;
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.SheetsTab;
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
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class ComponentFactoryService implements CoreProvider, StageProvider, TabProvider, ViewProvider {
    private static final Logger logger = LoggerFactory.getLogger(ComponentFactoryService.class);
    // Beans
    private final Store store;
    private final UiPersistenceService uiPersistenceService;
    private final CharacterFactory characterFactory;
    private final PlayerFactory playerFactory;
    private final SheetsServiceManager sheetsServiceManager;
    private final Matchmaker matchmaker;
    private final ApplicationScriptService applicationScriptService;
    private final GoogleAuthManager authManager;
    private final UiGoogleTaskService uiGoogleTaskService;
    private final UiTaskExecutor uiTaskExecutor;

    // Singleton components
    private ManagementStage managementStage;
    private  ExportGroupsStage exportGroupsStage;
    private ImportMatcherStage importMatcherStage;
    private CharacterManagementTab characterManagementTab;
    private GroupManagementTab groupManagementTab;
    private SheetsTab sheetsTab;
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
    private final ConfigurableApplicationContext springContext;

    @Autowired
    public ComponentFactoryService(Store store, UiPersistenceService uiPersistenceService,
                                   CharacterFactory characterFactory, PlayerFactory playerFactory,
                                   SheetsServiceManager sheetsServiceManager, Matchmaker matchmaker,
                                   ApplicationScriptService applicationScriptService,
                                   GoogleAuthManager authManager,
                                   UiGoogleTaskService uiGoogleTaskService,
                                   UiTaskExecutor uiTaskExecutor,
                                   ConfigurableApplicationContext springContext) {
        this.store = store;
        this.uiPersistenceService = uiPersistenceService;
        this.characterFactory = characterFactory;
        this.playerFactory = playerFactory;
        this.sheetsServiceManager = sheetsServiceManager;
        this.matchmaker = matchmaker;
        this.applicationScriptService = applicationScriptService;
        this.authManager = authManager;
        this.uiGoogleTaskService = uiGoogleTaskService;
        this.uiTaskExecutor = uiTaskExecutor;
        this.springContext = springContext;
        logger.info("ComponentFactoryService initialised with all required beans.");
    }

    /*******************************************************************************************
     ** Multi-ton Creators                                    **
     *******************************************************************************************/
    public SetupStage createSetupStage() {
        logger.debug("Creating new SetupStage instance.");
        return new SetupStage(null, null);
    }
    public LoadingOverlay createLoadingOverlay() {
        logger.debug("Creating new LoadingOverlay instance.");
        return new LoadingOverlay();
    }
    public SetupStage createSetupStage(String errorMessage) {
        logger.debug("Creating new SetupStage instance.");
        return new SetupStage(applicationScriptService, errorMessage);
    }
    public BaseDialog createDialog(DialogType type, String content, Node owner) {
        logger.debug("Creating new dialog of type: {}", type);
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
                logger.error("Attempted to create a dialog with an unknown type: {}", type);
                throw new IllegalArgumentException("Unknown DialogType: " + type);
        }
    }
    public AccessRequestStage createAccessRequestStage() {
        logger.debug("Creating new AccessRequestStage instance.");
        return new AccessRequestStage(springContext);
    }

    public BugReportStage createBugReportStage() {
        logger.debug("Creating new BugReportStage instance.");
        return new BugReportStage(springContext);
    }

    public CrashReportStage createCrashReportStage(Throwable e) {
        logger.debug("Creating new CrashReportStage instance for exception.");
        return new CrashReportStage(e, springContext);
    }



    /*******************************************************************************************
     ** Singleton Getters                                      **
     ********************************************************************************************/
    /************
     ** Stages **
     ************/
    public ManagementStage getManagementStage() {
        if (this.managementStage == null) {
            logger.info("Creating singleton instance of ManagementStage.");
            this.managementStage = new ManagementStage(this,this, this);
        }
        return this.managementStage;
    }
    public ExportGroupsStage getExportGroupsStage() {
        if(this.exportGroupsStage == null) {
            logger.info("Creating singleton instance of ExportGroupsStage.");
            this.exportGroupsStage = new ExportGroupsStage(this, sheetsServiceManager, store, authManager);
        }
        return this.exportGroupsStage;
    }

    public ImportMatcherStage getImportMatcherStage() {
        if (this.importMatcherStage == null) {
            logger.info("Creating singleton instance of ImportMatcherStage.");
            // We pass it all the things it needs, which this factory has!
            this.importMatcherStage = new ImportMatcherStage(this, store, playerFactory, characterFactory,
                    uiPersistenceService, uiTaskExecutor, sheetsServiceManager);
        }
        return this.importMatcherStage;
    }

    /**********
     ** Tabs **
     **********/
    public CharacterManagementTab getCharacterManagementTab() {
        if (this.characterManagementTab == null) {
            logger.info("Creating singleton instance of CharacterManagementTab.");
            this.characterManagementTab = new CharacterManagementTab(this, store, store, this,
                    uiPersistenceService, characterFactory);
        }
        return this.characterManagementTab;
    }
    public GroupManagementTab getGroupManagementTab() {
        if (this.groupManagementTab == null) {
            logger.info("Creating singleton instance of GroupManagementTab.");
            this.groupManagementTab = new GroupManagementTab(this,this, this,
                    sheetsServiceManager, uiTaskExecutor,matchmaker);
        }
        return this.groupManagementTab;
    }
    public SheetsTab getSheetsTab() {
        if (this.sheetsTab == null) {
            logger.info("Creating singleton instance of PersistenceTab.");
            this.sheetsTab = new SheetsTab(this, authManager, store, sheetsServiceManager, uiTaskExecutor);
        }
        return this.sheetsTab;
    }
    public PlayerManagementTab getPlayerManagementTab() {
        if (this.playerManagementTab == null) {
            logger.info("Creating singleton instance of PlayerManagementTab.");
            this.playerManagementTab = new PlayerManagementTab(this, store, this, uiPersistenceService, playerFactory);
        }
        return this.playerManagementTab;
    }
    public SettingsTab getSettingsTab() {
        if (this.settingsTab == null) {
            logger.info("Creating singleton instance of SettingsTab.");
            this.settingsTab = new SettingsTab(this, uiPersistenceService, store,
                    this, authManager, uiTaskExecutor, uiGoogleTaskService);
        }
        return this.settingsTab;
    }
    /***********
     ** Views **
     ***********/
    public CharacterFormView getCharacterFormView() {
        if (this.characterFormView == null) {
            logger.info("Creating singleton instance of CharacterFormView.");
            this.characterFormView = new CharacterFormView(store);
        }
        return this.characterFormView;
    }
    public GroupFormView getGroupFormView() {
        if (this.groupFormView == null) {
            logger.info("Creating singleton instance of GroupFormView.");
            this.groupFormView = new GroupFormView();
        }
        return this.groupFormView;
    }
    public PlayerFormView getPlayerFormView() {
        if (this.playerFormView == null) {
            logger.info("Creating singleton instance of PlayerFormView.");
            this.playerFormView = new PlayerFormView();
        }
        return this.playerFormView;
    }
    public CharacterRosterTableView getCharacterRosterTableView() {
        if (this.characterRosterTableView == null) {
            logger.info("Creating singleton instance of CharacterRosterTableView.");
            this.characterRosterTableView = new CharacterRosterTableView(store);
        }
        return this.characterRosterTableView;
    }
    public GroupAssignmentRosterTableView getGroupAssignmentRosterTableView() {
        if (this.groupAssignmentRosterTableView == null) {
            logger.info("Creating singleton instance of GroupAssignmentRosterTableView.");
            this.groupAssignmentRosterTableView = new GroupAssignmentRosterTableView();
        }
        return this.groupAssignmentRosterTableView;
    }
    public PlayerManagementRosterTableView getPlayerManagementRosterTableView() {
        if (this.playerManagementRosterTableView == null) {
            logger.info("Creating singleton instance of PlayerManagementRosterTableView.");
            this.playerManagementRosterTableView = new PlayerManagementRosterTableView(store);
        }
        return this.playerManagementRosterTableView;
    }
    public GroupTableView getGroupTableView() {
        if (groupTableView == null) {
            logger.info("Creating singleton instance of GroupTableView.");
            this.groupTableView = new GroupTableView();
        }
        return groupTableView;
    }
    public GroupDisplayView getGroupDisplayView() {
        if (this.groupDisplayView == null) {
            logger.info("Creating singleton instance of GroupDisplayView.");
            this.groupDisplayView = new GroupDisplayView();
        }
        return this.groupDisplayView;
    }
}
