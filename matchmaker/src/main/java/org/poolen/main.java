package org.poolen;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
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
        addPlayers();

        Map<UUID, Player> attendingPlayers = new HashMap<>();

        for(Player player : playerStore.getAllPlayers()) {
            attendingPlayers.put(player.getUuid(), player);
        }

        Matchmaker matchmaker = new Matchmaker(createGroups(), attendingPlayers);

        List<Group> completedGroups = matchmaker.Match();

        for(Group group : completedGroups) {
            System.out.println(group.toString() + "\n");
        }

    }

    private static List<Group> createGroups() {
        //DMs
        Player DM_1 = new Player("DM_1", true);
        Player DM_2 = new Player("DM_2", true);
        Player DM_3 = new Player("DM_3", true);
        Player DM_4 = new Player("DM_4", true);

        List<Group> groups = new ArrayList<>();

        groups.add(new Group(DM_1, House.AMBER, new Date()));
        groups.add(new Group(DM_2, House.OPAL, new Date()));
        groups.add(new Group(DM_3, House.AVENTURINE, new Date()));
        groups.add(new Group(DM_4, House.GARNET, new Date()));

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
