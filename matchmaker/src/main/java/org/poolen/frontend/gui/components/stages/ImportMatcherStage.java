package org.poolen.frontend.gui.components.stages;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.util.FuzzyStringMatcher;
import org.poolen.frontend.gui.components.dialogs.BaseDialog;
import org.poolen.frontend.gui.components.dialogs.ConfirmationDialog;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.web.google.SheetsServiceManager.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private List<PlayerData> importQueue;
    private int currentIndex;

    private BorderPane root;
    private Label titleLabel;
    private Label progressLabel;
    private Label importPlayerLabel;
    private Label importCharLabel;
    private Label actionLabel;
    private Button btnSkip;
    private VBox importBox;
    private VBox choiceBox;
    private ListView<MatchWrapper<?>> choiceListView;
    private Button btnUseSelected;
    private Button btnCreateNew;
    private HBox nameChoiceBox;
    private Button btnUseImportedName;
    private Button btnUseExistingName;

    public ImportMatcherStage(CoreProvider coreProvider, Store store,
                              PlayerFactory playerFactory, CharacterFactory characterFactory) {
        this.coreProvider = coreProvider;
        this.playerStore = store.getPlayerStore();
        this.characterStore = store.getCharacterStore();
        this.playerFactory = playerFactory;
        this.characterFactory = characterFactory;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Import Data Matcher");

        buildUi();
    }

    private void buildUi() {
        this.root = new BorderPane();
        root.setPadding(new Insets(20));

        // --- Title ---
        titleLabel = new Label("Processing Imported Data");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        progressLabel = new Label("Item 0 of 0");
        VBox titleBox = new VBox(5, titleLabel, progressLabel);
        titleBox.setAlignment(Pos.CENTER);
        root.setTop(titleBox);

        // --- Center Content ---
        VBox centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER_LEFT);
        centerBox.setPadding(new Insets(20, 0, 20, 0));

        importPlayerLabel = new Label();
        importCharLabel = new Label();
        importBox = new VBox(5, new Label("Importing:"), importPlayerLabel, importCharLabel);
        importBox.setStyle("-fx-border-color: #4285F4; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #f8f9fa;");

        actionLabel = new Label("Starting process...");
        actionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        // --- Choice List UI (replaces dialog) ---
        choiceListView = new ListView<>();
        choiceListView.setPrefHeight(150);
        btnUseSelected = new Button("Use Selected Match");
        btnCreateNew = new Button("Create New Entry");
        btnUseSelected.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        btnCreateNew.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");
        HBox choiceButtonBox = new HBox(10, btnUseSelected, btnCreateNew);
        choiceButtonBox.setAlignment(Pos.CENTER_LEFT);
        choiceBox = new VBox(10, choiceListView, choiceButtonBox);

        // --- Name Choice UI (replaces dialog) ---
        btnUseImportedName = new Button("Use Imported Name");
        btnUseExistingName = new Button("Use Existing Name");
        btnUseImportedName.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        btnUseExistingName.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        nameChoiceBox = new HBox(10, btnUseImportedName, btnUseExistingName);
        nameChoiceBox.setAlignment(Pos.CENTER_LEFT);

        centerBox.getChildren().addAll(importBox, actionLabel, choiceBox, nameChoiceBox);
        root.setCenter(centerBox);

        // --- Buttons ---
        btnSkip = new Button("Skip This Item");
        HBox buttonBox = new HBox(10, btnSkip);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        root.setBottom(buttonBox);

        btnSkip.setOnAction(e -> processNextItem());
        btnUseSelected.disableProperty().bind(choiceListView.getSelectionModel().selectedItemProperty().isNull());

        setScene(new Scene(this.root, 600, 500));
    }

    private void showChoiceUI(boolean show) {
        choiceBox.setVisible(show);
        choiceBox.setManaged(show);
    }

    private void showNameChoiceUI(boolean show) {
        nameChoiceBox.setVisible(show);
        nameChoiceBox.setManaged(show);
    }

    /**
     * Starts the import matching process.
     * This now shows the stage and blocks until finished!
     */
    public void startImport(List<PlayerData> data) {
        logger.info("Starting import matching process for {} items.", data.size());
        this.importQueue = data;
        this.currentIndex = 0;

        // We are already on the FX thread from the import task's
        // onSuccess, so we can set up the first item and then
        // call showAndWait() directly to block.

        // 1. Set up the first screen
        processNextItem();

        // 2. Show the stage and WAIT
        if (!this.isShowing()) {
            Platform.runLater(() -> this.showAndWait());
        } else {
            logger.warn("Matcher stage was already visible? This shouldn't happen.");
            this.requestFocus();
        }
    }

    /**
     * Processes the next item in the queue.
     */
    private void processNextItem() {
        showChoiceUI(false);
        showNameChoiceUI(false);

        // Check if we're done
        if (currentIndex >= importQueue.size()) {
            logger.info("Import matching finished.");
            // Closing the window unblocks the showAndWait()
            // call in ManagementStage.
            this.close();
            return;
        }

        PlayerData item = importQueue.get(currentIndex);
        currentIndex++;

        progressLabel.setText(String.format("Item %d of %d", currentIndex, importQueue.size()));
        importPlayerLabel.setText(String.format("Player: '%s' (UUID: %s)", item.player(), item.playerUuid().isEmpty() ? "N/A" : item.playerUuid()));
        importCharLabel.setText(String.format("Character: '%s' (UUID: %s) (House: %s)",
                item.character().isEmpty() ? "N/A" : item.character(),
                item.charUuid().isEmpty() ? "N/A" : item.charUuid(),
                item.house()));
        actionLabel.setText("Processing Player...");

        processPlayer(item);
    }

    /**
     * Step 1: Find or create the Player.
     */
    private void processPlayer(PlayerData item) {
        if (!item.playerUuid().isEmpty()) {
            try {
                Player p = playerStore.getPlayerByUuid(UUID.fromString(item.playerUuid()));
                if (p != null) {
                    logger.debug("Found player by UUID: {}", p.getName());
                    if (!p.getName().equals(item.player())) {
                        promptNameChoice("Player Name Mismatch",
                                "Found player by UUID, but names differ:",
                                item.player(), p.getName(),
                                (chosenName) -> {
                                    p.setName(chosenName);
                                    processCharacter(item, p);
                                });
                    } else {
                        processCharacter(item, p);
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
                        if (!match.getName().equals(item.player())) {
                            promptNameChoice("Confirm Player Name",
                                    "You've matched these players. Which name should be used?",
                                    item.player(), match.getName(),
                                    (chosenName) -> {
                                        match.setName(chosenName);
                                        processCharacter(item, match);
                                    });
                        } else {
                            processCharacter(item, match);
                        }
                    } else {
                        logger.debug("User selected 'Create New' for player '{}'", item.player());
                        Player newPlayer = createNewPlayer(item);
                        processCharacter(item, newPlayer);
                    }
                }
        );
    }

    /**
     * Step 2: Find or create the Character (we now have a resolved Player)
     */
    private void processCharacter(PlayerData item, Player owner) {
        actionLabel.setText("Processing Character...");

        if (item.character().isEmpty()) {
            logger.debug("No character name for this item. Skipping to next.");
            processNextItem();
            return;
        }

        if (!item.charUuid().isEmpty()) {
            try {
                Character c = characterStore.getCharacterByUuid(UUID.fromString(item.charUuid()));
                if (c != null) {
                    logger.debug("Found character by UUID: {}", c.getName());
                    if (!c.getName().equals(item.character())) {
                        promptNameChoice("Character Name Mismatch",
                                "Found character by UUID, but names differ:",
                                item.character(), c.getName(),
                                (chosenName) -> {
                                    c.setName(chosenName);
                                    c.setPlayer(owner);
                                    processNextItem();
                                });
                    } else {
                        c.setPlayer(owner);
                        processNextItem();
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
                        if (!match.getName().equals(item.character())) {
                            promptNameChoice("Confirm Character Name",
                                    "You've matched these characters. Which name should be used?",
                                    item.character(), match.getName(),
                                    (chosenName) -> {
                                        match.setName(chosenName);
                                        processNextItem();
                                    });
                        } else {
                            processNextItem();
                        }
                    } else {
                        logger.debug("User selected 'Create New' for character '{}'", item.character());
                        createNewCharacter(item, owner);
                        processNextItem();
                    }
                }
        );
    }

    // --- Helper Methods ---

    /**
     * A helper class to wrap a match and its distance for sorting.
     */
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

    /**
     * Displays the choice UI (ListView and buttons) to the user.
     */
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
