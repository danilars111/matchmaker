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
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.engine.GroupSuggester;
import org.poolen.backend.engine.HybridMatchmaker;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MainFrame extends Application {
    private static Map<UUID, Player> attendingPlayers = new HashMap<>();

    private List<House> houseThemes;

    public MainFrame() {}

    public void run(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {


        primaryStage.setTitle("D&D Matchmaker Deluxe");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // --- Left Side: Player List ---
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        Label playerLabel = new Label("Attending Adventurers");
        ListView<String> playerListView = new ListView<>();
        leftPanel.getChildren().addAll(playerLabel, playerListView);
        root.setLeft(leftPanel);

        // --- Center: Controls ---
        VBox centerPanel = new VBox(20);
        centerPanel.setAlignment(Pos.CENTER);
        centerPanel.setPadding(new Insets(10, 50, 10, 50));
        Label groupLabel = new Label("Suggested Groups: \n");

        Button generateButton = new Button("✨ Generate ✨");
        generateButton.setStyle("-fx-font-size: 16px; -fx-background-color: #4682B4; -fx-text-fill: white;");
        Button generateGroupsButton = new Button("✨ Generate Groups ✨");
        generateGroupsButton.setStyle("-fx-font-size: 16px; -fx-background-color: #9370DB; -fx-text-fill: white;");
        Button generatePlayersButton = new Button("🎲 Generate New Players 🎲");
        generatePlayersButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4682B4; -fx-text-fill: white;");
        centerPanel.getChildren().addAll(groupLabel, generateButton, generateGroupsButton, generatePlayersButton);
        root.setCenter(centerPanel);

        // --- Right Side: Group Display ---
        HBox rightPanel = new HBox(10);
        rightPanel.setPadding(new Insets(10));
        VBox groupsDisplay = new VBox(10);
        rightPanel.getChildren().addAll(groupsDisplay);

        TitledPane groupsPane = new TitledPane("Generated Groups", rightPanel);
        groupsPane.setCollapsible(false);
        root.setRight(groupsPane);

        // --- Event Handler ---
        generateButton.setOnAction(event -> {
            generatePlayersButton.fire();
            generateGroupsButton.fire();
        });


        generateGroupsButton.setOnAction(event -> {
            // 3. Run the matchmaking logic
            List<Player> dms = attendingPlayers.values().stream().filter(Player::isDungeonMaster).toList();
            List<Group> initialGroups = new ArrayList<>();
            for(int i = 0; i < dms.size(); i++) {
                initialGroups.add(new Group(dms.get(i), houseThemes.get(i), new Date()));
            }

            HybridMatchmaker matchmaker = new HybridMatchmaker(initialGroups, attendingPlayers);
            List<Group> finalGroups = matchmaker.match();

            // 4. Display the results
            groupsDisplay.getChildren().clear();
            for (Group group : finalGroups) {
                VBox groupBox = new VBox(5);
                Label dmLabel = new Label("DM: " + group.getDungeonMaster().getName());
                ListView<String> groupMembers = new ListView<>();
                group.getParty().values().forEach(p -> groupMembers.getItems().add(p.getName()));

                TitledPane groupPane = new TitledPane("House " + group.getHouse(), groupBox);
                groupPane.setCollapsible(false);
                groupBox.getChildren().addAll(dmLabel, groupMembers);
                groupsDisplay.getChildren().add(groupPane);
            }
        });

        generatePlayersButton.setOnAction(event -> {
            attendingPlayers = createMockData();
            // 2. Populate the initial player list
            playerListView.getItems().clear();

            attendingPlayers.values().stream()
                    .sorted((p1, p2) -> {
                        // This is our new, smarter sorting logic!
                        // If one is a DM and the other isn't, the DM always comes first.
                        if (p1.isDungeonMaster() && !p2.isDungeonMaster()) {
                            return -1;
                        } else if (!p1.isDungeonMaster() && p2.isDungeonMaster()) {
                            return 1;
                        } else {
                            // If both are DMs or both are players, sort them alphabetically.
                            return p1.getName().compareTo(p2.getName());
                        }
                    })
                    .forEach(p -> playerListView.getItems().add(p.getName() + (p.isDungeonMaster() ? " (DM)" : "")));

            GroupSuggester groupSuggester = new GroupSuggester(attendingPlayers.values());
            List<House> houseThemes = groupSuggester.suggestGroupThemes();
            setHouseThemes(houseThemes);

            groupLabel.setText("Suggested groups: \n" + houseThemes);
        });

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }

    public static void setAttendingPlayers(Map<UUID, Player> attendingPlayers) {
        MainFrame.attendingPlayers = attendingPlayers;
    }

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

    public List<House> getHouseThemes() {
        return this.houseThemes;
    }

    public void setHouseThemes(List<House> houseThemes) {
        this.houseThemes = houseThemes;
    }
}
