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
 * Uses a Pagination control for page navigation.
 * Supports re-processing of already completed or skipped items with "undo" logic.
 * Includes filtering to hide processed items.
 * Completion screen is the final page of the Pagination control.
 * Added Save/Cancel confirmation logic.
 * "Export/Sync" logic is merged into "Save & Close".
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
    private boolean isCancelling = false; // Flag to prevent close-loop

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
    private Button btnSaveAndClose;
    private Button btnCancel;
    private VBox finalConfirmationBox;
    private Label finalConfirmationLabel;
    private Button btnConfirmFinal;
    private Button btnCancelFinal;

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

        // Handle the 'X' button press
        this.setOnCloseRequest(event -> {
            if (isCancelling) {
                // This close was triggered by our performCancel() method.
                logger.debug("Programmatic close detected, allowing window to close.");
                return;
            }

            // This is a user-initiated 'X' press.
            event.consume();

            logger.debug("User 'X' press intercepted. Triggering handleCancel().");
            handleCancel();
        });
    }

    private void buildUi() {
        this.root = new BorderPane();
        root.setPadding(new Insets(20));

        titleLabel = new Label("Processing Imported Data");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        progressLabel = new Label("Item 0 of 0");
        filterCheckBox = new CheckBox("Hide Processed Items");
        filterCheckBox.setSelected(false);
        VBox titleBox = new VBox(10, titleLabel, progressLabel, filterCheckBox);
        titleBox.setAlignment(Pos.CENTER);
        root.setTop(titleBox);

        centerContainer = new StackPane();
        pagination = new Pagination();
        pagination.setStyle("-fx-page-information-visible: false;");
        centerContainer.getChildren().add(pagination);
        root.setCenter(centerContainer);

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

        btnSaveAndClose = new Button("Save, Sync & Close");
        btnCancel = new Button("Cancel & Undo All");
        btnSaveAndClose.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        btnCancel.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");

        finalConfirmationLabel = new Label("Are you sure?");
        finalConfirmationLabel.setWrapText(true);
        btnConfirmFinal = new Button("Yes");
        btnCancelFinal = new Button("No, Go Back");
        HBox finalConfirmButtons = new HBox(10, btnConfirmFinal, btnCancelFinal);
        finalConfirmButtons.setAlignment(Pos.CENTER_LEFT);
        finalConfirmationBox = new VBox(10, finalConfirmationLabel, finalConfirmButtons);
        finalConfirmationBox.setVisible(false); // Hidden by default

        HBox finalActionButtons = new HBox(10, btnSaveAndClose, btnCancel);
        finalActionButtons.setAlignment(Pos.CENTER_LEFT);

        completionBox = new VBox(15, new Label("Matching complete! What's next?"), finalActionButtons, finalConfirmationBox);
        completionBox.setAlignment(Pos.CENTER_LEFT);
        completionBox.setPadding(new Insets(20));
        completionBox.setMaxWidth(400);
        completionBox.setVisible(true);
        completionBox.setManaged(true);


        filterCheckBox.setOnAction(e -> updatePagination(true));

        btnSkip.setOnAction(e -> {
            fullProcessingQueue.get(currentIndex).setStatus(ImportMatchTask.Status.SKIPPED);
            goToNextPage();
        });

        btnUndo.setOnAction(e -> handleUndo());

        btnSaveAndClose.setOnAction(e -> handleSaveAndClose());
        btnCancel.setOnAction(e -> handleCancel());
        btnCancelFinal.setOnAction(e -> finalConfirmationBox.setVisible(false));

        btnUseSelected.disableProperty().bind(choiceListView.getSelectionModel().selectedItemProperty().isNull());

        setScene(new Scene(this.root, 600, 600));
    }

    /**
     * The Page Factory for the Pagination control.
     */
    private Node createPage(int pageIndex) {
        // Check if this is the completion page
        if (pageIndex == filteredProcessingQueue.size()) {
            logger.debug("Creating completion page.");
            long completeCount = fullProcessingQueue.stream().filter(t -> t.getStatus() == ImportMatchTask.Status.COMPLETED).count();
            long skippedCount = fullProcessingQueue.stream().filter(t -> t.getStatus() == ImportMatchTask.Status.SKIPPED).count();
            progressLabel.setText(String.format("Processing Complete! (%d completed, %d skipped)", completeCount, skippedCount));
            titleLabel.setText("Processing Complete!");
            finalConfirmationBox.setVisible(false);
            return completionBox;
        }

        logger.debug("Creating page for filtered index: {}", pageIndex);

        titleLabel.setText("Processing Imported Data");

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

        configurePageForItem(currentTask);

        if (currentTask.getStatus() == ImportMatchTask.Status.PENDING) {
            processPlayer(currentTask);
        }

        return pageContentContainer;
    }

    /**
     * Configures the shared UI components for the given task.
     */
    private void configurePageForItem(ImportMatchTask currentTask) {
        PlayerData item = currentTask.getOriginalData();
        progressLabel.setText(String.format("Item %d of %d (Full List)", currentIndex + 1, fullProcessingQueue.size()));
        importPlayerLabel.setText(String.format("Player: '%s' (UUID: %s)", item.player(), item.playerUuid().isEmpty() ? "N/A" : item.playerUuid()));
        importCharLabel.setText(String.format("Character: '%s' (UUID: %s) (House: %s)",
                item.character().isEmpty() ? "N/A" : item.character(),
                item.charUuid().isEmpty() ? "N/A" : item.charUuid(),
                item.house()));

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
     * Undoes a completed/skipped item, resetting it to PENDING.
     */
    private void handleUndo() {
        ImportMatchTask currentTask = fullProcessingQueue.get(currentIndex);
        if (currentTask.getStatus() == ImportMatchTask.Status.PENDING) {
            return;
        }

        logger.info("User initiated UNDO for item {}. Resetting status from {} to PENDING.",
                currentIndex + 1, currentTask.getStatus());

        if (currentTask.wasCharacterCreated() && currentTask.getResolvedCharacter() != null) {
            logger.debug("Undoing character creation for: {}", currentTask.getResolvedCharacter().getName());
            characterStore.removeCharacter(currentTask.getResolvedCharacter());
        }
        if (currentTask.wasPlayerCreated() && currentTask.getResolvedPlayer() != null) {
            logger.debug("Undoing player creation for: {}", currentTask.getResolvedPlayer().getName());
            playerStore.removePlayer(currentTask.getResolvedPlayer());
        }

        currentTask.setStatus(ImportMatchTask.Status.PENDING);
        currentTask.setResolvedPlayer(null);
        currentTask.setResolvedCharacter(null);
        currentTask.setFinalPlayerName(currentTask.getOriginalData().player());
        currentTask.setFinalCharName(currentTask.getOriginalData().character());
        currentTask.setWasPlayerCreated(false);
        currentTask.setWasCharacterCreated(false);

        updatePagination(true);

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

        updatePagination(false); // Tell it NOT to restore the page index

        if (pagination.getPageCount() == 1 && filteredProcessingQueue.isEmpty()) {
            logger.debug("goToNextPage: All items processed, showing completion page.");
            pagination.setCurrentPageIndex(0); // Go to completion page
            return;
        }

        int newPageIndex;
        if (filterWasOn) {
            newPageIndex = Math.min(oldFilteredPageIndex, filteredProcessingQueue.size()); // .size() is completion page
        } else {
            newPageIndex = Math.min(oldFilteredPageIndex + 1, filteredProcessingQueue.size()); // .size() is completion page
        }

        pagination.setCurrentPageIndex(newPageIndex);

        if (newPageIndex == oldFilteredPageIndex && filteredProcessingQueue.size() != oldFilteredSize) {
            createPage(newPageIndex);
        }
    }

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
        this.fullProcessingQueue = preProcessAndFilterData(data);

        if (fullProcessingQueue.isEmpty()) {
            if (data.isEmpty()) {
                // Case 1: No data was ever imported.
                logger.info("No items require review because no data was imported. Closing matcher.");
                Platform.runLater(() -> {
                    coreProvider.createDialog(BaseDialog.DialogType.INFO,
                            "No data was imported to process.",
                            root).showAndWait();
                });
                Platform.runLater(this::close);
                return; // Exit here.
            } else {
                // Case 2: All items were perfect matches.
                // The dialog was shown in preProcess. Proceed to show completion page.
                logger.info("No items require review. Proceeding to completion page for sync options.");
            }
        }

        updatePagination(true);

        if (!this.isShowing()) {
            this.showAndWait();
        } else {
            logger.warn("Matcher stage was already visible? This shouldn't happen.");
            this.requestFocus();
        }
    }

    /**
     * Re-builds the filtered list and resets the pagination control
     */
    private void updatePagination(boolean restorePageIndex) {
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

        if (restorePageIndex) {
            if (oldPage < newPageCount) {
                pagination.setCurrentPageIndex(oldPage);
            } else {
                pagination.setCurrentPageIndex(Math.max(0, newPageCount - 1));
            }
        }
    }

    @Override
    public void showAndWait() {
        super.showAndWait();
    }

    /**
     * Pre-filters the data list to remove items that are already "perfect matches".
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
                        "All " + finalPerfectMatchCount + " imported items were perfect matches and have been skipped. You can now sync if needed.",
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
     * Handles the "Save, Sync & Close" button click.
     */
    private void handleSaveAndClose() {
        logger.info("User initiated 'Save & Close'.");
        finalConfirmationBox.setVisible(false);

        long pendingCount = fullProcessingQueue.stream()
                .filter(t -> t.getStatus() == ImportMatchTask.Status.PENDING)
                .count();

        if (pendingCount > 0) {
            finalConfirmationLabel.setText(String.format(
                    "You still have %d pending item(s). If you save now, they will be skipped. Save and close anyway?",
                    pendingCount));
            finalConfirmationBox.setVisible(true);
            btnConfirmFinal.setOnAction(e -> {
                finalConfirmationBox.setVisible(false);
                performSaveAndSyncAndClose();
            });
        } else {
            performSaveAndSyncAndClose();
        }
    }

    /**
     * Performs the actual save, then sync, and closes the window on completion.
     */
    private void performSaveAndSyncAndClose() {
        logger.info("Calling saveAll service...");

        Runnable onSaveFinishedCallback = () -> {
            logger.info("Database save complete. Starting sheet sync.");
            performExportAndClose();
        };

        uiPersistenceService.saveAll(
                this.getScene().getWindow(),
                onSaveFinishedCallback
        );
    }

    /**
     * Performs the export/sync logic and closes the window.
     * This runs *after* the save is successful.
     */
    private void performExportAndClose() {
        logger.info("Starting 'Export & Sync' as part of save process.");

        String localSheetId;
        try {
            localSheetId = (String) settingsStore.getSetting(Settings.PersistenceSettings.SHEETS_ID).getSettingValue();
            if (localSheetId == null || localSheetId.isEmpty()) {
                throw new IllegalStateException("SHEETS_ID setting is missing or empty.");
            }
        } catch (Exception e) {
            logger.error("Failed to get SHEETS_ID from settings!", e);
            coreProvider.createDialog(BaseDialog.DialogType.ERROR, "DB save was successful, but could not get Sheet ID from settings. Cannot sync.", root).showAndWait();
            if (onFinishedCallback != null) {
                onFinishedCallback.run();
            }
            Platform.runLater(() -> {
                isCancelling = true;
                this.close();
            });
            return;
        }

        List<SheetsServiceManager.ExportData> exportDataList = fullProcessingQueue.stream()
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

        // We check this *after* getting the sheet ID, but before the task.
        if (exportDataList.isEmpty()) {
            logger.info("No completed items to export. Skipping sheet sync.");
            if (onFinishedCallback != null) {
                onFinishedCallback.run();
            }
            Platform.runLater(() -> {
                isCancelling = true;
                this.close();
            });
            return;
        }

        uiTaskExecutor.execute(
                this.getScene().getWindow(),
                "Syncing to Sheet...",
                "Successfully saved and synced to sheet!",
                (updater) -> {
                    updater.updateStatus("Step 1/2: Updating sheet with new names and UUIDs...");
                    sheetsServiceManager.exportMatchedData(localSheetId, exportDataList, settingsStore);

                    updater.updateStatus("Step 2/2: Syncing all DB characters back to sheet...");
                    List<Character> allCharacters = new ArrayList<>(characterStore.getAllCharacters());
                    sheetsServiceManager.appendMissingCharactersToSheet(localSheetId, allCharacters, settingsStore);

                    return "unused";
                },
                (result) -> {
                    logger.info("Export and Sync complete.");
                    if (onFinishedCallback != null) {
                        onFinishedCallback.run();
                    }
                    Platform.runLater(() -> {
                        isCancelling = true;
                        this.close();
                    });
                },
                (error) -> {
                    logger.error("Export and Sync failed!", error);
                    Platform.runLater(() -> {
                        coreProvider.createDialog(BaseDialog.DialogType.ERROR,
                                "Database save was SUCCESSFUL, but sheet sync failed: " + error.getMessage(),
                                root).showAndWait();

                        if (onFinishedCallback != null) {
                            onFinishedCallback.run();
                        }
                        isCancelling = true;
                        this.close();
                    });
                }
        );
    }


    /**
     * Handles the "Cancel" button click.
     */
    private void handleCancel() {
        logger.info("User initiated 'Cancel & Undo All'.");
        finalConfirmationBox.setVisible(false);

        finalConfirmationLabel.setText("Are you sure you want to cancel? All new players and characters created in this session will be undone.");
        finalConfirmationBox.setVisible(true);
        btnConfirmFinal.setOnAction(e -> {
            finalConfirmationBox.setVisible(false);
            performCancel();
        });
    }

    /**
     * Performs the actual cancel logic (undo all) and closes.
     */
    private void performCancel() {
        logger.warn("User confirmed cancel. Undoing all creations...");

        for (ImportMatchTask task : fullProcessingQueue) {
            if (task.wasCharacterCreated() && task.getResolvedCharacter() != null) {
                logger.debug("Undoing character creation for: {}", task.getResolvedCharacter().getName());
                characterStore.removeCharacter(task.getResolvedCharacter());
            }
            if (task.wasPlayerCreated() && task.getResolvedPlayer() != null) {
                logger.debug("Undoing player creation for: {}", task.getResolvedPlayer().getName());
                playerStore.removePlayer(task.getResolvedPlayer());
            }
        }

        logger.info("Undo complete. Closing window.");
        isCancelling = true;
        this.close(); // Close without saving
    }

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
                showChoiceUI(true);
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
