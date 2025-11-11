package org.poolen.frontend.gui.components.stages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    private List<ImportMatchTask> processingQueue;
    private int currentIndex;
    private Runnable onFinishedCallback;

    private BorderPane root;
    private Label titleLabel;
    private Label progressLabel;
    private VBox importBox;
    private Label importPlayerLabel;
    private Label importCharLabel;
    private Label actionLabel;

    private VBox matchingBox;
    private VBox choiceBox;
    private ListView<MatchWrapper<?>> choiceListView;
    private Button btnUseSelected;
    private Button btnCreateNew;
    private HBox nameChoiceBox;
    private Button btnUseImportedName;
    private Button btnUseExistingName;

    private VBox completionBox;
    private Button btnSave;
    private Button btnExport;
    private Button btnClose;

    private Button btnBack;
    private Button btnSkip;


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

        public void setResolvedPlayer(Player p) { this.resolvedPlayer = p; }
        public void setResolvedCharacter(Character c) { this.resolvedCharacter = c; }
        public void setFinalPlayerName(String name) { this.finalPlayerName = name; }
        public void setFinalCharName(String name) { this.finalCharName = name; }
        public void setStatus(Status s) { this.status = s; }
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
        VBox titleBox = new VBox(5, titleLabel, progressLabel);
        titleBox.setAlignment(Pos.CENTER);
        root.setTop(titleBox);

        VBox centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER_LEFT);
        centerBox.setPadding(new Insets(20, 0, 20, 0));

        importPlayerLabel = new Label();
        importCharLabel = new Label();
        importBox = new VBox(5, new Label("Importing:"), importPlayerLabel, importCharLabel);
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

        matchingBox = new VBox(10, actionLabel, choiceBox, nameChoiceBox);

        btnSave = new Button("Save Changes to DB");
        btnExport = new Button("Export UUIDs & Names to Sheet");
        btnClose = new Button("Close");
        btnSave.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        btnExport.setStyle("-fx-background-color: #4285F4; -fx-text-fill: white;");
        completionBox = new VBox(10, new Label("Matching complete! What's next?"), btnSave, btnExport, btnClose);
        completionBox.setAlignment(Pos.CENTER_LEFT);

        centerBox.getChildren().addAll(importBox, matchingBox, completionBox);
        root.setCenter(centerBox);

        btnBack = new Button("Back");
        btnSkip = new Button("Skip This Item");
        HBox bottomButtonBox = new HBox(10, btnBack, btnSkip);
        bottomButtonBox.setAlignment(Pos.CENTER_RIGHT);
        root.setBottom(bottomButtonBox);

        btnBack.setOnAction(e -> showItem(currentIndex - 1));

        btnSkip.setOnAction(e -> {
            processingQueue.get(currentIndex).setStatus(ImportMatchTask.Status.SKIPPED);
            showItem(currentIndex + 1);
        });

        btnClose.setOnAction(e -> this.close());
        btnSave.setOnAction(e -> handleSave());
        btnExport.setOnAction(e -> handleExport());
        btnUseSelected.disableProperty().bind(choiceListView.getSelectionModel().selectedItemProperty().isNull());

        setScene(new Scene(this.root, 600, 500));
    }

    private void showMatchingUI(boolean show) {
        matchingBox.setVisible(show);
        matchingBox.setManaged(show);
        btnSkip.setVisible(show);
        btnBack.setVisible(show);
    }

    private void showChoiceUI(boolean show) {
        choiceBox.setVisible(show);
        choiceBox.setManaged(show);
    }

    private void showNameChoiceUI(boolean show) {
        nameChoiceBox.setVisible(show);
        nameChoiceBox.setManaged(show);
    }

    private void showCompletionUI(boolean show) {
        completionBox.setVisible(show);
        completionBox.setManaged(show);
    }

    /**
     * Starts the import matching process.
     */
    public void startImport(List<PlayerData> data, Runnable onFinished) {
        logger.info("Starting import matching process for {} items.", data.size());
        this.onFinishedCallback = onFinished;
        this.processingQueue = data.stream().map(ImportMatchTask::new).collect(Collectors.toList());
        this.currentIndex = 0;

        if (!this.isShowing()) {
            this.showAndWait();
        } else {
            logger.warn("Matcher stage was already visible? This shouldn't happen.");
            this.requestFocus();
        }
    }

    @Override
    public void showAndWait() {
        showItem(0); // Show the first item
        super.showAndWait();
    }

    /**
     * Replaces processNextItem()! This is our new "page" loader.
     */
    private void showItem(int index) {
        if (index < 0) {
            return; // Can't go back past the beginning
        }

        showMatchingUI(true);
        showChoiceUI(false);
        showNameChoiceUI(false);
        showCompletionUI(false);
        btnBack.setDisable(index == 0); // Disable "Back" on the first item

        if (index >= processingQueue.size()) {
            logger.info("Import matching finished.");
            showMatchingUI(false);
            importBox.setVisible(false);
            progressLabel.setText(String.format("Processed %d items!", processingQueue.size()));
            showCompletionUI(true);
            return;
        }

        currentIndex = index; // Set our new current index
        ImportMatchTask currentTask = processingQueue.get(currentIndex);

        PlayerData item = currentTask.getOriginalData();
        progressLabel.setText(String.format("Item %d of %d", currentIndex + 1, processingQueue.size()));
        importPlayerLabel.setText(String.format("Player: '%s' (UUID: %s)", item.player(), item.playerUuid().isEmpty() ? "N/A" : item.playerUuid()));
        importCharLabel.setText(String.format("Character: '%s' (UUID: %s) (House: %s)",
                item.character().isEmpty() ? "N/A" : item.character(),
                item.charUuid().isEmpty() ? "N/A" : item.charUuid(),
                item.house()));
        actionLabel.setText("Processing Player...");

        // We reset the task status, just in case they went "Back"
        // to re-do a "Skipped" item
        currentTask.setStatus(ImportMatchTask.Status.PENDING);

        processPlayer(currentTask);
    }

    /**
     * Step 1: Find or create the Player.
     */
    private void processPlayer(ImportMatchTask currentTask) {
        PlayerData item = currentTask.getOriginalData();

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
                        processCharacter(currentTask);
                    }
                }
        );
    }

    /**
     * Step 2: Find or create the Character (we now have a resolved Player)
     */
    private void processCharacter(ImportMatchTask currentTask) {
        actionLabel.setText("Processing Character...");
        PlayerData item = currentTask.getOriginalData();
        Player owner = currentTask.getResolvedPlayer();

        // --- THIS IS THE FIX! ---
        // That naughty "if (item.character().isEmpty())" block is GONE!
        // Now we just... process it normally, just as you wanted!

        if (!item.charUuid().isEmpty()) {
            try {
                Character c = characterStore.getCharacterByUuid(UUID.fromString(item.charUuid()));
                if (c != null) {
                    logger.debug("Found character by UUID: {}", c.getName());
                    currentTask.setResolvedCharacter(c);
                    if (!c.getName().equals(item.character())) {
                        promptNameChoice("Character Name Mismatch",
                                "Found character by UUID, but names differ:",
                                item.character(), c.getName(),
                                (chosenName) -> {
                                    c.setName(chosenName);
                                    c.setPlayer(owner);
                                    currentTask.setFinalCharName(chosenName);
                                    currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                                    showItem(currentIndex + 1);
                                });
                    } else {
                        c.setPlayer(owner);
                        currentTask.setFinalCharName(c.getName());
                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                        showItem(currentIndex + 1);
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
                        if (!match.getName().equals(item.character())) {
                            promptNameChoice("Confirm Character Name",
                                    "You've matched these characters. Which name should be used?",
                                    item.character(), match.getName(),
                                    (chosenName) -> {
                                        match.setName(chosenName);
                                        currentTask.setFinalCharName(chosenName);
                                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                                        showItem(currentIndex + 1);
                                    });
                        } else {
                            currentTask.setFinalCharName(match.getName());
                            currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                            showItem(currentIndex + 1);
                        }
                    } else {
                        logger.debug("User selected 'Create New' for character '{}'", item.character());
                        Character newChar = createNewCharacter(item, owner);
                        currentTask.setResolvedCharacter(newChar);
                        if (newChar != null) {
                            currentTask.setFinalCharName(newChar.getName());
                        }
                        currentTask.setStatus(ImportMatchTask.Status.COMPLETED);
                        showItem(currentIndex + 1);
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

        List<SheetsServiceManager.ExportData> exportDataList = processingQueue.stream()
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
                onChosen.accept(Optional.empty());
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
