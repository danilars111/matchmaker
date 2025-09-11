package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.stages.ManagementStage;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MainFrame extends Application {

    private Map<UUID, Player> attendingPlayers = new HashMap<>();
    private List<House> houseThemes;
    private ListView<String> playerListView;
    private VBox groupsDisplay;
    private Label playerLabel;
    private Label groupLabel;

    public MainFrame() {}

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("D&D Matchmaker Deluxe");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Initialize the member variables
        playerListView = new ListView<>();
        groupsDisplay = new VBox(10);
        playerLabel = new Label("Attending Adventurers");
        groupLabel = new Label();

        // --- Left Side: Player List ---
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.getChildren().addAll(playerLabel, playerListView);
        root.setLeft(leftPanel);

        // --- Center: Controls ---
        VBox centerPanel = new VBox(20);
        centerPanel.setAlignment(Pos.CENTER);
        centerPanel.setPadding(new Insets(10, 50, 10, 50));

        Button rematchButton = new Button("✨ Rematch Groups ✨");
        rematchButton.setStyle("-fx-font-size: 16px; -fx-background-color: #9370DB; -fx-text-fill: white; -fx-font-weight: bold;");

        Button generatePlayersButton = new Button("🎲 Generate New Players 🎲");
        generatePlayersButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4682B4; -fx-text-fill: white;");

        Button addPlayerButton = new Button("👤 Add New Player 👤");
        addPlayerButton.setStyle("-fx-font-size: 14px; -fx-background-color: #3CB371; -fx-text-fill: white;");

        centerPanel.getChildren().addAll(groupLabel, rematchButton, generatePlayersButton, addPlayerButton);
        root.setCenter(centerPanel);

        // --- Right Side: Group Display ---
        HBox rightPanel = new HBox(10);
        rightPanel.setPadding(new Insets(10));
        rightPanel.getChildren().addAll(groupsDisplay);

        TitledPane groupsPane = new TitledPane("Generated Groups", rightPanel);
        groupsPane.setCollapsible(false);
        root.setRight(groupsPane);

        // --- Event Handlers (now much simpler!) ---
        rematchButton.setOnAction(event -> updateUI());

        generatePlayersButton.setOnAction(event -> {
            this.attendingPlayers = createMockData();
            updateUI();
        });

        addPlayerButton.setOnAction(event -> {
            ManagementStage managementStage = new ManagementStage(this.attendingPlayers);
            managementStage.show();
        });

        Scene scene = new Scene(root, 1000, 600);
/*        primaryStage.setScene(scene);
        primaryStage.show();*/
        ManagementStage managementStage = new ManagementStage(createMockData());
        managementStage.show();

    }

    /**
     * This is now the one true method for updating the entire user interface.
     * It handles everything from player lists to suggestions and final group generation.
     */
    public void updateUI() {
 /*       long dmCount = attendingPlayers.values().stream().filter(Player::isDungeonMaster).count();
        long playerCount = attendingPlayers.size() - dmCount;
        playerLabel.setText("Attending Adventurers (" + playerCount + " Players, " + dmCount + " DMs)");

        GroupSuggester groupSuggester = new GroupSuggester(attendingPlayers.values());
        List<House> houseThemes = groupSuggester.suggestGroupThemes();
        setHouseThemes(houseThemes);

        groupLabel.setText("Suggested groups: \n" + houseThemes);

        // Update the player list view
        playerListView.getItems().clear();
        attendingPlayers.values().stream()
                .sorted((p1, p2) -> {
                    if (p1.isDungeonMaster() && !p2.isDungeonMaster()) return -1;
                    if (!p1.isDungeonMaster() && p2.isDungeonMaster()) return 1;
                    return p1.getName().compareTo(p2.getName());
                })
                .forEach(p -> playerListView.getItems().add(p.getName() + (p.isDungeonMaster() ? " (DM)" : "")));

        // Run the matchmaking logic
        List<Player> dms = attendingPlayers.values().stream().filter(Player::isDungeonMaster).toList();
        if(dms.isEmpty()){
            groupsDisplay.getChildren().clear();
            groupsDisplay.getChildren().add(new Label("No DMs available to form groups!"));
            return;
        }

        // In a real app you might use your GroupSuggester here.
        // For now, we'll assign houses cyclically for simplicity.
        List<Group> initialGroups = new ArrayList<>();
        for(int i = 0; i < dms.size(); i++) {
            initialGroups.add(new Group(dms.get(i), houseThemes.get(i), LocalDate.now()));
        }

        HybridMatchmaker matchmaker = new HybridMatchmaker(initialGroups, attendingPlayers);
        List<Group> finalGroups = matchmaker.match();

        // Display the final results
        groupsDisplay.getChildren().clear();
        for (Group group : finalGroups) {
            VBox groupBox = new VBox(5);
            Label dmLabel = new Label("DM: " + group.getDungeonMaster().getName());
            dmLabel.setStyle("-fx-font-weight: bold;");

            ListView<String> groupMembers = new ListView<>();
            group.getParty().values().stream()
                    .map(Player::getName)
                    .sorted()
                    .forEach(name -> groupMembers.getItems().add(name));
            groupMembers.setPrefHeight(120);

            TitledPane groupPane = new TitledPane("House " + group.getHouse(), groupBox);
            groupPane.setCollapsible(false);
            groupBox.getChildren().addAll(dmLabel, groupMembers);
            groupsDisplay.getChildren().add(groupPane);
        }*/
    }

    // Mock data for demonstration
    private Map<UUID, Player> createMockData() {
        House[] houses = House.values();
        attendingPlayers.clear();

        for(int i = 0; i < 20; i++) {
            Random random = new Random();
            Character character1 = new Character("1", houses[random.nextInt(houses.length)]);
            Character character2 = new Character("2", houses[random.nextInt(houses.length)]);
            Player player = new Player(character1.getHouse().toString() +"/" + character2.getHouse(), false);
            player.addCharacter(character1);
            player.addCharacter(character2);
            attendingPlayers.put(player.getUuid(), player);
        }

        Character character1 = new Character("1", House.AVENTURINE);
        Character character2 = new Character("2", House.AMBER);
        Player player = new Player("BLACKLISTER", false);
        player.addCharacter(character1);
        player.addCharacter(character2);
        attendingPlayers.put(player.getUuid(), player);

        Character character3 = new Character("1", House.AVENTURINE);
        Character character4 = new Character("2", House.AMBER);
        Player player2 = new Player("BLACKLISTEE", false);
        player2.addCharacter(character3);
        player2.addCharacter(character4);
        attendingPlayers.put(player2.getUuid(), player2);

        player.blacklist(player2);

        Character character5 = new Character("1", House.AVENTURINE);
        Character character6 = new Character("2", House.AMBER);
        Player player3 = new Player("BUDDY", false);
        player3.addCharacter(character5);
        player3.addCharacter(character6);
        attendingPlayers.put(player3.getUuid(), player3);

        player.getBuddylist().put(player3.getUuid(), player3);
        //player3.getBuddylist().put(player2.getUuid(), player2);

        player.getPlayerLog().put(player3.getUuid(), Date.from(LocalDate.of(2025, Month.AUGUST, 25).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        player3.getPlayerLog().put(player.getUuid(), Date.from(LocalDate.of(2025, Month.AUGUST, 25).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        // Create DMs
        for (int i = 0; i < 4; i++) {
            Player dm = new Player("DM-" + (i+1), true);
            attendingPlayers.put(dm.getUuid(), dm);
        }
        return attendingPlayers;
    }

    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }

    public void setAttendingPlayers(Map<UUID, Player> attendingPlayers) {
        this.attendingPlayers = attendingPlayers;
    }

    public List<House> getHouseThemes() {
        return this.houseThemes;
    }

    public void setHouseThemes(List<House> houseThemes) {
        this.houseThemes = houseThemes;
    }
}
