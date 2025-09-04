package org.poolen;

import com.google.ortools.Loader;
import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.engine.GroupSuggester;
import org.poolen.backend.engine.Matchmaker;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class main {
    static PlayerStore playerStore = PlayerStore.getInstance();

    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        // ---- The Priming Pump ----
        // We're going to create and destroy a dummy solver right away.
        // This forces the native library to initialize itself fully in a clean state.
        try {
            System.out.println("Priming OR-Tools native library...");
            LinearSumAssignment dummySolver = new LinearSumAssignment();
            dummySolver.delete();
            System.out.println("Library primed successfully.");
        } catch (Exception e) {
            System.err.println("Failed to prime the OR-Tools library. Aborting.");
            e.printStackTrace();
            return; // Exit if priming fails
        }
        // -------------------------


        addPlayers();

        Map<UUID, Player> attendingPlayers = new HashMap<>();

        for(Player player : playerStore.getAllPlayers()) {
            attendingPlayers.put(player.getUuid(), player);
        }
        Player DM_1 = new Player("DM_1", true);
        Player DM_2 = new Player("DM_2", true);
        Player DM_3 = new Player("DM_3", true);
        Player DM_4 = new Player("DM_4", true);

        playerStore.addPlayer(DM_1);
        playerStore.addPlayer(DM_2);
        playerStore.addPlayer(DM_3);
        playerStore.addPlayer(DM_4);

        GroupSuggester groupSuggester = new GroupSuggester(playerStore.getAllPlayers());

        List<House> groups = groupSuggester.suggestGroupThemes();

        System.out.println("\nSuggested Groups based on player turnout: ");
        System.out.println(groups);


        Matchmaker matchmaker = new Matchmaker(createGroups(groups), attendingPlayers);

        List<Group> completedGroups = matchmaker.Match();

        for(Group group : completedGroups) {
            System.out.println(group.toString() + "\n");
        }


    }

    private static List<Group> createGroups(List<House> suggestedGroups) {

        List<Player> dms = playerStore.getDungeonMasters();
        List<Group> groups = new ArrayList<>();

        for(int i = 0; i < dms.size(); i++) {
            groups.add(new Group(dms.get(i), suggestedGroups.get(i), new Date()));
        }


        return groups;
    }

    private static void addPlayers() {

        House[] houses = House.values();

        for(int i = 0; i < 16; i++) {
            Random random = new Random();
            Character character1 = new Character("1", houses[random.nextInt(houses.length)]);
            Character character2 = new Character("2", houses[random.nextInt(houses.length)]);
            Player player = new Player(character1.getHouse() +"/" + character2.getHouse(), false);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);

        }
    }
}
