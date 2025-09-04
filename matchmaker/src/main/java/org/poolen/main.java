package org.poolen;

import com.google.ortools.Loader;
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

        addPlayers();

        Map<UUID, Player> attendingPlayers = new HashMap<>();

        for(Player player : playerStore.getAllPlayers()) {
            attendingPlayers.put(player.getUuid(), player);
        }
        Player DM_1 = new Player("DM_1", true);
        Player DM_2 = new Player("DM_2", true);
        Player DM_3 = new Player("DM_3", true);
        Player DM_4 = new Player("DM_4", true);
        Player DM_5 = new Player("DM_5", true);
        Player DM_6 = new Player("DM_6", true);
        Player DM_7 = new Player("DM_7", true);
        Player DM_8 = new Player("DM_8", true); /*
        Player DM_9 = new Player("DM_9", true);
        Player DM_10 = new Player("DM_10", true);*/

        playerStore.addPlayer(DM_1);
        playerStore.addPlayer(DM_2);
        playerStore.addPlayer(DM_3);
        playerStore.addPlayer(DM_4);
        playerStore.addPlayer(DM_5);/*
        playerStore.addPlayer(DM_6);
        playerStore.addPlayer(DM_7);
        playerStore.addPlayer(DM_8);
        playerStore.addPlayer(DM_9);
        playerStore.addPlayer(DM_10);*/

        GroupSuggester groupSuggester = new GroupSuggester(playerStore.getAllPlayers());

        List<House> groups = groupSuggester.suggestGroupThemes();

        System.out.println("\nSuggested Groups based on player turnout: ");
        System.out.println(groups);


        Matchmaker matchmaker = new Matchmaker(createGroups(groups), attendingPlayers);

        List<Group> completedGroups = matchmaker.match();

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

        for(int i = 0; i < 50; i++) {
            Random random = new Random();
            Character character1 = new Character("1", houses[random.nextInt(houses.length)]);
            Character character2 = new Character("2", houses[random.nextInt(houses.length)]);
            Player player = new Player(character1.getHouse().toString() +"/" + character2.getHouse(), false);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);

        }
    }
}
