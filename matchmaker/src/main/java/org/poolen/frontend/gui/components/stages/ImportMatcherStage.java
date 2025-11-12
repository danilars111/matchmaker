package org.poolen.frontend.gui.components.stages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Pagination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.util.FuzzyStringMatcher;
import org.poolen.frontend.gui.components.dialogs.BaseDialog;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.frontend.util.services.UiPersistenceService;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.web.google.SheetsServiceManager;
import org.poolen.web.google.SheetsServiceManager.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * A modal stage that guides the user through matching imported sheet data
 * with existing database entries.
 *
 * REFACTORED: Now uses a Pagination control for page navigation.
 * Supports re-processing of already completed or skipped items with "undo" logic.
 * Includes filtering to hide processed items.
 * Completion screen is now the final page of the Pagination control.
 */
public class ImportMatcherStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(ImportMatcherStage.class);

    private final CoreProvider coreProvider;
    private final PlayerStore playerStore;
    private final CharacterStore characterStore;
    private final PlayerFactory playerFactory;
    private final CharacterFactory characterFactory;
    private final UiPersistenceService uiPersistenceService;
    private final UiTaskExecutor uiTaskExecutor;
    private final SheetsServiceManager sheetsServiceManager;
    private final SettingsStore settingsStore;

    private List<ImportMatchTask> fullProcessingQueue; // Holds ALL items
    private List<ImportMatchTask> filteredProcessingQueue; // Holds items for pagination
    private int currentIndex; // Index in the FULL list
    private Runnable onFinishedCallback;

    private BorderPane root;
    private Label titleLabel;
    private Label progressLabel;
    private CheckBox filterCheckBox;
    private VBox importBox;
    private Label importPlayerLabel;
    private Label importCharLabel;
    private Label actionLabel;
    private Label statusLabel;

    private VBox matchingBox;
    private VBox choiceBox;
    private ListView<MatchWrapper<?>> choiceListView;
    private Button btnUseSelected;
    private Button btnCreateNew;
    private HBox nameChoiceBox;
    private Button btnUseImportedName;
    private Button btnUseExistingName;

    private VBox confirmationBox;
    private Label confirmationLabel;
    private Button btnConfirmCreate;
    private Button btnCancelCreate;

    private VBox completionBox;
    private Button btnSave;
    private Button btnExport;
    private Button btnClose;

    private StackPane centerContainer;
    private Pagination pagination;
    private VBox pageContentContainer;

    private Button btnSkip;
    private Button btnUndo;


    /**
     * A stateful class holding the matching task data.
     */
    public class ImportMatchTask {
        public enum Status { PENDING, COMPLETED, SKIPPED }

        private final PlayerData originalData;
        private Player resolvedPlayer = null;
        private Character resolvedCharacter = null;
        private String finalPlayerName = null;
        private String finalCharName = null;
        private Status status = Status.PENDING;

        private boolean wasPlayerCreated = false;
        private boolean wasCharacterCreated = false;


        public ImportMatchTask(PlayerData originalData) {
            this.originalData = originalData;
            this.finalPlayerName = originalData.player();
            this.finalCharName = originalData.character();
        }

        public PlayerData getOriginalData() { return originalData; }
        public Player getResolvedPlayer() { return resolvedPlayer; }
        public Character getResolvedCharacter() { return resolvedCharacter; }
        public String getFinalPlayerName() { return finalPlayerName; }
        public String getFinalCharName() { return finalCharName; }
        public Status getStatus() { return status; }
        public boolean wasPlayerCreated() { return wasPlayerCreated; }
        public boolean wasCharacterCreated() { return wasCharacterCreated; }

        public void setResolvedPlayer(Player p) { this.resolvedPlayer = p; }
        public void setResolvedCharacter(Character c) { this.resolvedCharacter = c; }
        public void setFinalPlayerName(String name) { this.finalPlayerName = name; }
        public void setFinalCharName(String name) { this.finalCharName = name; }
        public void setStatus(Status s) { this.status = s; }
        public void setWasPlayerCreated(boolean wasPlayerCreated) { this.wasPlayerCreated = wasPlayerCreated; }
        public void setWasCharacterCreated(boolean wasCharacterCreated) { this.wasCharacterCreated = wasCharacterCreated; }
    }


    public ImportMatcherStage(CoreProvider coreProvider, Store store,
                              PlayerFactory playerFactory, CharacterFactory characterFactory,
                              UiPersistenceService uiPersistenceService,
                              UiTaskExecutor uiTaskExecutor,
                              SheetsServiceManager sheetsServiceManager) {
        this.coreProvider = coreProvider;
        this.playerStore = store.getPlayerStore();
        this.characterStore = store.getCharacterStore();
        this.settingsStore = store.getSettingsStore();
        this.playerFactory = playerFactory;
        this.characterFactory = characterFactory;
        this.uiPersistenceService = uiPersistenceService;
        this.uiTaskExecutor = uiTaskExecutor;
        this.sheetsServiceManager = sheetsServiceManager;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Import Data Matcher");

        buildUi();
    }

    private void buildUi() {
        this.root = new BorderPane();
        root.setPadding(new Insets(20));

        titleLabel = new Label("Processing Imported Data");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        progressLabel = new Label("Item 0 of 0");
        filterCheckBox = new CheckBox("Hide Processed Items");
        filterCheckBox.setSelected(false); // Default to showing all
        VBox titleBox = new VBox(10, titleLabel, progressLabel, filterCheckBox);
        titleBox.setAlignment(Pos.CENTER);
        root.setTop(titleBox);

        // --- New Center Layout ---
        centerContainer = new StackPane();
        pagination = new Pagination();
        pagination.setStyle("-fx-page-information-visible: false;");
        centerContainer.getChildren().add(pagination); // Only pagination is in the StackPane
        root.setCenter(centerContainer);

        // --- Page Content (re-used) ---
        pageContentContainer = new VBox(20);
        pageContentContainer.setAlignment(Pos.CENTER_LEFT);
        pageContentContainer.setPadding(new Insets(20, 0, 20, 0));

        importPlayerLabel = new Label();
        importCharLabel = new Label();
        statusLabel = new Label("Status: PENDING");
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        importBox = new VBox(5, new Label("Importing:"), importPlayerLabel, importCharLabel, statusBox);
        importBox.setStyle("-fx-border-color: #4285F4; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #f8f9fa;");

        actionLabel = new Label("Starting process...");
        actionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        choiceListView = new ListView<>();
        choiceListView.setPrefHeight(150);
        btnUseSelected = new Button("Use Selected Match");
        btnCreateNew = new Button("Create New Entry");
        btnUseSelected.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        btnCreateNew.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");
        HBox choiceButtonBox = new HBox(10, btnUseSelected, btnCreateNew);
        choiceButtonBox.setAlignment(Pos.CENTER_LEFT);
        choiceBox = new VBox(10, choiceListView, choiceButtonBox);

        btnUseImportedName = new Button("Use Imported Name");
        btnUseExistingName = new Button("Use Existing Name");
        btnUseImportedName.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        btnUseExistingName.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        nameChoiceBox = new HBox(10, btnUseImportedName, btnUseExistingName);
        nameChoiceBox.setAlignment(Pos.CENTER_LEFT);

        confirmationLabel = new Label("An entry with this exact name already exists. Are you sure?");
        confirmationLabel.setWrapText(true);
        btnConfirmCreate = new Button("Yes, Create New");
        btnCancelCreate = new Button("No, Go Back");
        btnConfirmCreate.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");
        btnCancelCreate.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #cccccc;");
        HBox confirmButtonBox = new HBox(10, btnConfirmCreate, btnCancelCreate);
        confirmButtonBox.setAlignment(Pos.CENTER_LEFT);
        confirmationBox = new VBox(10, confirmationLabel, confirmButtonBox);

        matchingBox = new VBox(10, actionLabel, choiceBox, nameChoiceBox, confirmationBox);

        btnSkip = new Button("Skip This Item");
        btnUndo = new Button("Undo This Item");
        btnUndo.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomButtonBox = new HBox(10, spacer, btnUndo, btnSkip);
        bottomButtonBox.setAlignment(Pos.CENTER_RIGHT);

        pageContentContainer.getChildren().addAll(importBox, matchingBox, bottomButtonBox);

        // --- Completion Screen (Now returned by Page Factory) ---
        btnSave = new Button("Save Changes to DB");
        btnExport = new Button("Export UUIDs & Names to Sheet");
        btnClose = new Button("Close");
        btnSave.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        btnExport.setStyle("-fx-background-color: #4285F4; -fx-text-fill: white;");
        completionBox = new VBox(10, new Label("Matching complete! What's next?"), btnSave, btnExport, btnClose);
        completionBox.setAlignment(Pos.CENTER_LEFT);
        completionBox.setPadding(new Insets(20));
        completionBox.setMaxWidth(400);
        completionBox.setVisible(true); // Is visible by default
        completionBox.setManaged(true);


        // --- Actions ---
        filterCheckBox.setOnAction(e -> updatePagination(true)); // MODIFIED

        btnSkip.setOnAction(e -> {
            fullProcessingQueue.get(currentIndex).setStatus(ImportMatchTask.Status.SKIPPED);
            goToNextPage();
        });

        btnUndo.setOnAction(e -> handleUndo());

        btnClose.setOnAction(e -> this.close());
        btnSave.setOnAction(e -> handleSave());
        btnExport.setOnAction(e -> handleExport());
        btnUseSelected.disableProperty().bind(choiceListView.getSelectionModel().selectedItemProperty().isNull());

        setScene(new Scene(this.root, 600, 600));
    }

    /**
     * The Page Factory for the Pagination control.
     * This is called whenever a new page is navigated to.
     */
    private Node createPage(int pageIndex) {
        // --- NEW: Check if this is the completion page ---
        if (pageIndex == filteredProcessingQueue.size()) {
            logger.debug("Creating completion page.");
            // It is! Configure and return the completion box.
            long completeCount = fullProcessingQueue.stream().filter(t -> t.getStatus() == ImportMatchTask.Status.COMPLETED).count();
            long skippedCount = fullProcessingQueue.stream().filter(t -> t.getStatus() == ImportMatchTask.Status.SKIPPED).count();
            progressLabel.setText(String.format("Processing Complete! (%d completed, %d skipped)", completeCount, skippedCount));
            titleLabel.setText("Processing Complete!");
            return completionBox;
        }

        // --- It's a normal item page ---
        logger.debug("Creating page for filtered index: {}", pageIndex);

        // Reset title/progress in case we came from completion page
        titleLabel.setText("Processing Imported Data");
        // (progressLabel will be set in configurePageForItem)

        if (pageIndex >= filteredProcessingQueue.size()) {
            logger.warn("Page factory requested index {} which is out of bounds for filtered list.", pageIndex);
            return pageContentContainer;
        }

        ImportMatchTask currentTask = filteredProcessingQueue.get(pageIndex);
        this.currentIndex = fullProcessingQueue.indexOf(currentTask);

        if (this.currentIndex == -1) {
            logger.error("Could not find task in full list! This is a bug.");
            return pageContentContainer;
        }

        configurePageForItem(currentTask); // Set up the UI for this item

        // Only auto-process if it's pending
        if (currentTask.getStatus() == ImportMatchTask.Status.PENDING) {
            processPlayer(currentTask);
        }

        return pageContentContainer; // Return the re-usable container
    }

    /**
     * Configures the shared UI components for the given task.
     * This is the logic from the old `showItem` method.
     */
    private void configurePageForItem(ImportMatchTask currentTask) {
        // Update labels
        PlayerData item = currentTask.getOriginalData();
        progressLabel.setText(String.format("Item %d of %d (Full List)", currentIndex + 1, fullProcessingQueue.size()));
        importPlayerLabel.setText(String.format("Player: '%s' (UUID: %s)", item.player(), item.playerUuid().isEmpty() ? "N/A" : item.playerUuid()));
        importCharLabel.setText(String.format("Character: '%s' (UUID: %s) (House: %s)",
                item.character().isEmpty() ? "N/A" : item.character(),
                item.charUuid().isEmpty() ? "N/A" : item.charUuid(),
                item.house()));

        // --- NEW: Status-based UI switching ---
        ImportMatchTask.Status status = currentTask.getStatus();
        statusLabel.setText("Status: " + status.name());

        switch (status) {
            case PENDING:
                statusLabel.setTextFill(Color.BLUE);
                actionLabel.setText("Processing Player...");
                matchingBox.setVisible(true);
                matchingBox.setManaged(true);
                showChoiceUI(false);
                showNameChoiceUI(false);
                showConfirmationUI(false);
                btnSkip.setVisible(true);
                btnUndo.setVisible(false);
                break;
            case COMPLETED:
                statusLabel.setTextFill(Color.GREEN);
                actionLabel.setText("This item has been matched.");
                matchingBox.setVisible(false);
                matchingBox.setManaged(false);
                btnSkip.setVisible(false);
                btnUndo.setVisible(true);
                break;
            case SKIPPED:
                statusLabel.setTextFill(Color.ORANGERED);
                actionLabel.setText("This item was skipped.");
                matchingBox.setVisible(false);
                matchingBox.setManaged(false);
                btnSkip.setVisible(false);
                btnUndo.setVisible(true);
                break;
        }
    }

    /**
     * NEW: Undoes a completed/skipped item, resetting it to PENDING.
     */
    private void handleUndo() {
        ImportMatchTask currentTask = fullProcessingQueue.get(currentIndex);
        if (currentTask.getStatus() == ImportMatchTask.Status.PENDING) {
            return; // Should not happen
        }

        logger.info("User initiated UNDO for item {}. Resetting status from {} to PENDING.",
                currentIndex + 1, currentTask.getStatus());

        // --- CRITICAL: Undo creation in correct order ---
        if (currentTask.wasCharacterCreated() && currentTask.getResolvedCharacter() != null) {
            logger.debug("Undoing character creation for: {}", currentTask.getResolvedCharacter().getName());
            characterStore.removeCharacter(currentTask.getResolvedCharacter());
        }
        if (currentTask.wasPlayerCreated() && currentTask.getResolvedPlayer() != null) {
            logger.debug("Undoing player creation for: {}", currentTask.getResolvedPlayer().getName());
            playerStore.removePlayer(currentTask.getResolvedPlayer());
        }
        // --- END CRITICAL ---

        // Reset task state
        currentTask.setStatus(ImportMatchTask.Status.PENDING);
        currentTask.setResolvedPlayer(null);
        currentTask.setResolvedCharacter(null);
        currentTask.setFinalPlayerName(currentTask.getOriginalData().player());
        currentTask.setFinalCharName(currentTask.getOriginalData().character());
        currentTask.setWasPlayerCreated(false); // Reset flags
        currentTask.setWasCharacterCreated(false); // Reset flags

        // Refresh the filter (if it's on, this item will now be visible)
        updatePagination(true); // MODIFIED

        // Re-configure the page, which will now show as PENDING
        // This will be triggered by updatePagination setting the page factory
        // or by us setting the current page index.
        int currentPage = pagination.getCurrentPageIndex();
        if (currentPage == filteredProcessingQueue.size()) { // Was on completion page
            pagination.setCurrentPageIndex(currentPage - 1);
        } else {
            configurePageForItem(currentTask);
            processPlayer(currentTask);
        }
    }


    /**
     * Helper to programmatically advance the page or show completion screen.
     */
    private void goToNextPage() {
        int oldFilteredPageIndex = pagination.getCurrentPageIndex();
        boolean filterWasOn = filterCheckBox.isSelected();
        int oldFilteredSize = filteredProcessingQueue.size();

        // Re-build the filtered list (the item we just completed might disappear)
        updatePagination(false); // MODIFIED: Tell it NOT to restore the page index

        // If updatePagination found we are done, it will set page count to 1
        // (completion page) and we don't need to do anything else.
        if (pagination.getPageCount() == 1 && filteredProcessingQueue.isEmpty()) {
            logger.debug("goToNextPage: All items processed, showing completion page.");
            pagination.setCurrentPageIndex(0); // Go to completion page
            return;
        }

        int newPageIndex;
        if (filterWasOn) {
            // Filter is ON. The next item (or completion page) is at the *same* index.
            newPageIndex = Math.min(oldFilteredPageIndex, filteredProcessingQueue.size()); // .size() is completion page
        } else {
            // Filter is OFF. The next item is at the *next* index.
            newPageIndex = Math.min(oldFilteredPageIndex + 1, filteredProcessingQueue.size()); // .size() is completion page
        }

        pagination.setCurrentPageIndex(newPageIndex);

        // If the index *didn't* change (e.g. filter on, but not last item), force reload.
        // --- MODIFIED: This check was the problem! ---
        // We now force a reload if the filter was on AND the lists are different
        if (newPageIndex == oldFilteredPageIndex && filteredProcessingQueue.size() != oldFilteredSize) {
            createPage(newPageIndex);
        }

        // --- DELETED ---
        // if (newPageIndex == oldFilteredPageIndex && filteredProcessingQueue.size() != oldFilteredSize) {
        //    createPage(newPageIndex);
        // }
    }

    /**
     * Toggles visibility between the Pagination and Completion screen.
     * -- NO LONGER NEEDED --
     */
    // private void showPagination(boolean show) { ... }

    private void showChoiceUI(boolean show) {
        choiceBox.setVisible(show);
        choiceBox.setManaged(show);
    }

    private void showNameChoiceUI(boolean show) {
        nameChoiceBox.setVisible(show);
        nameChoiceBox.setManaged(show);
    }

    private void showConfirmationUI(boolean show) {
        confirmationBox.setVisible(show);
        confirmationBox.setManaged(show);
    }

    /**
     * Starts the import matching process.
     */
    public void startImport(List<PlayerData> data, Runnable onFinished) {
        logger.info("Starting import matching process for {} items.", data.size());
        this.onFinishedCallback = onFinished;
        this.currentIndex = 0;
        this.fullProcessingQueue = preProcessAndFilterData(data); // Load full list

        // If the full queue is empty, no review is needed.
        if (fullProcessingQueue.isEmpty()) {
            logger.info("No items require review. Closing matcher.");
            if (data.isEmpty()) {
                Platform.runLater(() -> {
                    coreProvider.createDialog(BaseDialog.DialogType.INFO,
                            "No data was imported to process.",
                            root).showAndWait();
                });
            }
            Platform.runLater(this::close);
            return;
        }

        // Initial pagination setup
        updatePagination(true); // MODIFIED

        if (!this.isShowing()) {
            this.showAndWait();
        } else {
            logger.warn("Matcher stage was already visible? This shouldn't happen.");
            this.requestFocus();
        }
    }

    /**
     * NEW: Re-builds the filtered list and resets the pagination control
     */
    private void updatePagination(boolean restorePageIndex) { // MODIFIED
        int oldPage = pagination.getCurrentPageIndex();

        if (filterCheckBox.isSelected()) {
            filteredProcessingQueue = fullProcessingQueue.stream()
                    .filter(t -> t.getStatus() == ImportMatchTask.Status.PENDING)
                    .collect(Collectors.toList());
        } else {
            filteredProcessingQueue = new ArrayList<>(fullProcessingQueue);
        }

        int newPageCount = filteredProcessingQueue.size() + 1; // +1 for completion page

        // This handles the "no items" case from the start
        if (newPageCount == 1 && fullProcessingQueue.isEmpty()) {
            pagination.setPageCount(1);
            pagination.setPageFactory(i -> {
                titleLabel.setText("Processing Complete!");
                progressLabel.setText("No items to process.");
                return new VBox(new Label("No data was found to process."));
            });
            return;
        }

        pagination.setPageCount(newPageCount);
        pagination.setPageFactory(this::createPage);

        if (restorePageIndex) { // <-- NEW Check
            // Try to stay on the same page
            if (oldPage < newPageCount) {
                pagination.setCurrentPageIndex(oldPage);
            } else {
                pagination.setCurrentPageIndex(Math.max(0, newPageCount - 1));
            }
        }
    }

    @Override
    public void showAndWait() {
        // The Pagination control will automatically call createPage(0) when it's shown.
        super.showAndWait();
    }

    /**
     * NEW: Pre-filters the data list to remove items that are already "perfect matches".
     */
    private List<ImportMatchTask> preProcessAndFilterData(List<PlayerData> data) {
        List<ImportMatchTask> tasksThatNeedReview = new ArrayList<>();
        int perfectMatchCount = 0;

        for (PlayerData item : data) {
            boolean playerIsPerfect = false;
            boolean charIsPerfect = false;
            Player matchedPlayer = null;

            if (!item.playerUuid().isEmpty()) {
                try {
                    Player p = playerStore.getPlayerByUuid(UUID.fromString(item.playerUuid()));
                    if (p != null && p.getName().equals(item.player())) {
                        playerIsPerfect = true;
                        matchedPlayer = p;
                    }
                } catch (Exception e) { /* Not a perfect match */ }
            }

            if (!item.charUuid().isEmpty()) {
                try {
                    Character c = characterStore.getCharacterByUuid(UUID.fromString(item.charUuid()));
                    if (c != null && c.getName().equals(item.character())) {
                        charIsPerfect = true;
                        if (playerIsPerfect && matchedPlayer != null && c.getPlayer() != null && !c.getPlayer().equals(matchedPlayer)) {
                            logger.debug("Silently updating owner for perfect match char '{}' to '{}'", c.getName(), matchedPlayer.getName());
                            c.setPlayer(matchedPlayer);
                        }
                    }
                } catch (Exception e) { /* Not a perfect match */ }
            }

            if (playerIsPerfect && charIsPerfect) {
                logger.info("Pre-skipping perfect match: Player '{}' | Char '{}'", item.player(), item.character());
                perfectMatchCount++;
            } else {
                tasksThatNeedReview.add(new ImportMatchTask(item));
            }
        }

        logger.info("Pre-processing complete. Found {} perfect matches. {} items require review.",
                perfectMatchCount, tasksThatNeedReview.size());

        if (tasksThatNeedReview.isEmpty() && perfectMatchCount > 0) {
            int finalPerfectMatchCount = perfectMatchCount;
            Platform.runLater(() -> {
                coreProvider.createDialog(BaseDialog.DialogType.INFO,
                        "All " + finalPerfectMatchCount + " imported items were perfect matches and have been skipped. No review is needed.",
                        root).showAndWait();
            });
        }

        return tasksThatNeedReview;
    }


    /**
     * Step 1: Find or create the Player.
     */
    private void processPlayer(ImportMatchTask currentTask) {
        PlayerData item = currentTask.getOriginalData();
        actionLabel.setText("Match Player: '" + item.player() + "'");

        if (!item.playerUuid().isEmpty()) {
            try {
                Player p = playerStore.getPlayerByUuid(UUID.fromString(item.playerUuid()));
                if (p != null) {
                    logger.debug("Found player by UUID: {}", p.getName());
                    currentTask.setResolvedPlayer(p);
                    if (!p.getName().equals(item.player())) {
                        promptNameChoice("Player Name Mismatch",
                                "Found player by UUID, but names differ:",
                                item.player(), p.getName(),
                                (chosenName) -> {
                                    p.setName(chosenName);
                                    currentTask.setFinalPlayerName(chosenName);
                                    processCharacter(currentTask);
                                });
                    } else {
                        currentTask.setFinalPlayerName(p.getName());
                        processCharacter(currentTask);
                    }
                    return;
                }
            } catch (Exception e) {
                logger.warn("Imported Player UUID '{}' was invalid.", item.playerUuid());
            }
        }

        List<Player> allPlayers = new ArrayList<>(playerStore.getAllPlayers());
        promptChoice(
                "Match Player: '" + item.player() + "'",
                "Select the correct match from the list, sorted by relevance.",
                allPlayers,
                item.player(),
                (chosenPlayerOpt) -> {
                    if (chosenPlayerOpt.isPresent()) {
                        Player match = chosenPlayerOpt.get();
                        logger.debug("User matched '{}' to '{}'", item.player(), match.getName());
                        currentTask.setResolvedPlayer(match);
                        currentTask.setWasPlayerCreated(false);
                        if (!match.getName().equals(item.player())) {
                            promptNameChoice("Confirm Player Name",
                                    "You've matched these players. Which name should be used?",
                                    item.player(), match.getName(),
                                    (chosenName) -> {
                                        match.setName(chosenName);
                                        currentTask.setFinalPlayerName(chosenName);
                                        processCharacter(currentTask);
                                    });
                        } else {
                            currentTask.setFinalPlayerName(match.getName());
                            processCharacter(currentTask);
                        }
                    } else {
                        logger.debug("User selected 'Create New' for player '{}'", item.player());
                        Player newPlayer = createNewPlayer(item);
                        currentTask.setResolvedPlayer(newPlayer);
                        currentTask.setFinalPlayerName(newPlayer.getName());
                        currentTask.setWasPlayerCreated(true);
                        processCharacter(currentTask);
                    }
                }
        );
    }

    /**
     * Step 2: Find or create the Character (we now have a resolved Player)
     */
    private void processCharacter(ImportMatchTask currentTask) {
        PlayerData item = currentTask.getOriginalData();
        Player owner = currentTask.getResolvedPlayer();
        actionLabel.setText("Match Character: '" + item.character() + "'");


        if (!item.charUuid().isEmpty()) {
            try {
                Character c = characterStore.getCharacterByUuid(UUID.fromString(item.charUuid()));
                if (c != null) {
                    logger.debug("Found character by UUID: {}", c.getName());
                    currentTask.setResolvedCharacter(c);
                    currentTask.setWasCharacterCreated(false);
                    if (!c.getName().equals(item.character())) {
                        promptNameChoice("Character Name Mismatch",
                                "Found character by UUID, but names differ:",
                                item.character(), c.getName(),
                                (chosenName) -> {
                                    c.setName(chosenName);
                                    c.setPlayer(owner);
                                    currentTask.setFinalCharName(chosenName);
                                    currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                                    goToNextPage();
                                });
                    } else {
                        c.setPlayer(owner);
                        currentTask.setFinalCharName(c.getName());
                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                        goToNextPage();
                    }
                    return;
                }
            } catch (Exception e) {
                logger.warn("Imported Character UUID '{}' was invalid.", item.charUuid());
            }
        }

        List<Character> allPlayerChars = new ArrayList<>(owner.getCharacters());
        promptChoice(
                "Match Character: '" + item.character() + "'",
                "Select match for player '" + owner.getName() + "'. Sorted by relevance.",
                allPlayerChars,
                item.character(),
                (chosenCharOpt) -> {
                    if (chosenCharOpt.isPresent()) {
                        Character match = chosenCharOpt.get();
                        logger.debug("User matched '{}' to '{}'", item.character(), match.getName());
                        currentTask.setResolvedCharacter(match);
                        currentTask.setWasCharacterCreated(false);
                        if (!match.getName().equals(item.character())) {
                            promptNameChoice("Confirm Character Name",
                                    "You've matched these characters. Which name should be used?",
                                    item.character(), match.getName(),
                                    (chosenName) -> {
                                        match.setName(chosenName);
                                        currentTask.setFinalCharName(chosenName);
                                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                                        goToNextPage();
                                    });
                        } else {
                            currentTask.setFinalCharName(match.getName());
                            currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                            goToNextPage();
                        }
                    } else {
                        logger.debug("User selected 'Create New' for character '{}'", item.character());
                        Character newChar = createNewCharacter(item, owner);
                        currentTask.setResolvedCharacter(newChar);
                        currentTask.setWasCharacterCreated(true);
                        if (newChar != null) {
                            currentTask.setFinalCharName(newChar.getName());
                        }
                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                        goToNextPage();
                    }
                }
        );
    }

    /**
     * Handles the "Save All" button click.
     */
    private void handleSave() {
        logger.info("User initiated 'Save All' from matcher stage.");
        uiPersistenceService.saveAll(
                this.getScene().getWindow(),
                onFinishedCallback
        );
    }

    /**
     * Handles the "Export" button click.
     */
    private void handleExport() {
        logger.info("User initiated 'Export' from matcher stage.");

        String localSheetId;
        try {
            localSheetId = (String) settingsStore.getSetting(Settings.PersistenceSettings.SHEETS_ID).getSettingValue();
            if (localSheetId == null || localSheetId.isEmpty()) {
                throw new IllegalStateException("SHEETS_ID setting is missing or empty.");
            }
        } catch (Exception e) {
            logger.error("Failed to get SHEETS_ID from settings!", e);
            coreProvider.createDialog(BaseDialog.DialogType.ERROR, "Could not get Sheet ID from settings. Cannot export.", root).showAndWait();
            return;
        }

        List<SheetsServiceManager.ExportData> exportDataList = fullProcessingQueue.stream() // Use FULL list
                .filter(t -> t.getStatus() == ImportMatchTask.Status.COMPLETED)
                .map(t -> new SheetsServiceManager.ExportData(
                        t.getOriginalData().row(),
                        t.getOriginalData().house(),
                        t.getFinalPlayerName(),
                        (t.getResolvedPlayer() != null) ? t.getResolvedPlayer().getUuid().toString() : null,
                        t.getFinalCharName(),
                        (t.getResolvedCharacter() != null) ? t.getResolvedCharacter().getUuid().toString() : null
                ))
                .collect(Collectors.toList());

        if (exportDataList.isEmpty()) {
            coreProvider.createDialog(BaseDialog.DialogType.INFO, "No completed items to export.", root).showAndWait();
            return;
        }

        uiTaskExecutor.execute(
                this.getScene().getWindow(),
                "Exporting matched data...",
                "Successfully exported data to Google Sheets!",
                (updater) -> {
                    updater.updateStatus("Updating sheet with new names and UUIDs...");
                    sheetsServiceManager.exportMatchedData(localSheetId, exportDataList, settingsStore);
                    return "unused";
                },
                (result) -> {
                    logger.info("Export complete.");
                },
                (error) -> {
                    logger.error("Export failed!", error);
                }
        );
    }

    // --- Helper Methods ---

    private static class MatchWrapper<T> implements Comparable<MatchWrapper<T>> {
        final T item;
        final int distance;
        final String name;

        MatchWrapper(T item, String name, int distance) {
            this.item = item;
            this.name = name;
            this.distance = distance;
        }

        @Override
        public int compareTo(MatchWrapper<T> o) {
            return Integer.compare(this.distance, o.distance);
        }

        @Override
        public String toString() {
            return String.format("'%s' (Dist: %d)", this.name, this.distance);
        }
    }

    private <T> void promptChoice(String title, String header, List<T> allItems, String importedName, Consumer<Optional<T>> onChosen) {
        Platform.runLater(() -> {
            actionLabel.setText(header);

            List<MatchWrapper<T>> sortedMatches = allItems.stream()
                    .map(item -> {
                        String name = (item instanceof Player) ? ((Player) item).getName() : ((Character) item).getName();
                        int dist = FuzzyStringMatcher.getLevenshteinDistance(importedName, name);
                        return new MatchWrapper<>(item, name, dist);
                    })
                    .sorted()
                    .collect(Collectors.toList());

            choiceListView.setItems(FXCollections.observableArrayList(sortedMatches));
            if (!sortedMatches.isEmpty()) {
                choiceListView.getSelectionModel().selectFirst();
            }

            showChoiceUI(true);

            btnUseSelected.setOnAction(e -> {
                MatchWrapper<?> selected = choiceListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onChosen.accept(Optional.of((T) selected.item));
                }
            });

            btnCreateNew.setOnAction(e -> {
                boolean hasPerfectMatch = sortedMatches.stream().anyMatch(match -> match.distance == 0);

                if (hasPerfectMatch) {
                    promptConfirmation(
                            "Exact Name Match Found",
                            "An entry with the exact name '" + importedName + "' already exists. Are you sure you want to create a new one?",
                            header,
                            () -> {
                                onChosen.accept(Optional.empty());
                            }
                    );
                } else {
                    onChosen.accept(Optional.empty());
                }
            });
        });
    }


    private Player createNewPlayer(PlayerData item) {
        logger.info("Creating new player: {}", item.player());
        UUID uuid = item.playerUuid().isEmpty() ? null : UUID.fromString(item.playerUuid());
        return playerFactory.create(uuid, item.player(), false);
    }

    private Character createNewCharacter(PlayerData item, Player owner) {
        logger.info("Creating new character: {}", item.character());
        UUID uuid = item.charUuid().isEmpty() ? null : UUID.fromString(item.charUuid());
        try {
            return characterFactory.create(uuid, owner, item.character(), item.house(), false);
        } catch (IllegalArgumentException e) {
            logger.warn("Could not create new character (likely business rule violation): {}", e.getMessage());
            Platform.runLater(() -> {
                coreProvider.createDialog(BaseDialog.DialogType.ERROR,
                        "Could not create character '" + item.character() + "': " + e.getMessage(),
                        this.root).show();
            });
            return null;
        }
    }

    private void promptConfirmation(String title, String content, String originalListHeader, Runnable onConfirmed) {
        Platform.runLater(() -> {
            showChoiceUI(false);

            actionLabel.setText(title);
            confirmationLabel.setText(content);
            showConfirmationUI(true);

            btnConfirmCreate.setOnAction(e -> {
                showConfirmationUI(false);
                onConfirmed.run();
            });

            btnCancelCreate.setOnAction(e -> {
                showConfirmationUI(false);
                actionLabel.setText(originalListHeader);
                showChoiceUI(true); // Fixed typo here (was s showChoiceUI)
            });
        });
    }

    private void promptNameChoice(String title, String content, String name1, String name2, Consumer<String> onChosen) {
        Platform.runLater(() -> {
            showChoiceUI(false);

            actionLabel.setText(content);
            btnUseImportedName.setText(String.format("Use Imported: '%s'", name1));
            btnUseExistingName.setText(String.format("Use Existing: '%s'", name2));
            showNameChoiceUI(true);

            btnUseImportedName.setOnAction(e -> {
                showNameChoiceUI(false);
                onChosen.accept(name1);
            });
            btnUseExistingName.setOnAction(e -> {
                showNameChoiceUI(false);
                onChosen.accept(name2);
            });
        });
    }
}
