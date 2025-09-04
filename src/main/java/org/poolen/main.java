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

        // Amber players
        for(int i = 0; i < 4; i++) {
            Player player = new Player(House.AMBER.name() +"_"+ i, false);
            Character character1 = new Character("1", House.AMBER);
            Character character2 = new Character("2", House.AMBER);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);

        }

        // Opal players
        for(int i = 0; i < 4; i++) {
            Player player = new Player(House.OPAL.name() +"_"+ i, false);
            Character character1 = new Character("1", House.OPAL);
            Character character2 = new Character("2", House.OPAL);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);
        }

        // Aventurine players
        for(int i = 0; i < 3; i++) {
            Player player = new Player(House.AVENTURINE.name() +"_"+ i, false);
            Character character1 = new Character("1", House.AVENTURINE);
            Character character2 = new Character("2", House.AVENTURINE);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);
        }

        // Garnet players
        for(int i = 0; i < 5; i++) {
            Player player = new Player(House.GARNET.name() +"_"+ i, false);
            Character character1 = new Character("1", House.GARNET);
            Character character2 = new Character("2", House.GARNET);
            player.addCharacter(character1);
            player.addCharacter(character2);
            playerStore.addPlayer(player);
        }
    }
}
