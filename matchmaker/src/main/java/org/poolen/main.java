package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.LoginApplication;


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

public class main {
    static PlayerStore playerStore = PlayerStore.getInstance();
    static CharacterStore characterStore = CharacterStore.getInstance();
    static SettingsStore settingsStore = SettingsStore.getInstance();

    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        //addPlayers();
        initSettings();

        Map<UUID, Player> attendingPlayers = new HashMap<>();


        for(Player player : playerStore.getAllPlayers()) {
            attendingPlayers.put(player.getUuid(), player);
        }

        Application.launch(LoginApplication.class, args);
    }

    private static List<Group> createGroups(List<House> suggestedGroups) {

        List<Player> dms = playerStore.getDungeonMasters();
        List<Group> groups = new ArrayList<>();

        for(int i = 0; i < dms.size(); i++) {
            groups.add(new Group(dms.get(i), List.of(suggestedGroups.get(i)), LocalDate.now()));
        }


        return groups;
    }

    private static void addPlayers() {

        House[] houses = House.values();

        Player DM_1 = new Player("DM_1", true);
        Player DM_2 = new Player("DM_2", true);
        Player DM_3 = new Player("DM_3", true);
        Player DM_4 = new Player("DM_4", true);
        Player DM_5 = new Player("DM_5", true);

        playerStore.addPlayer(DM_1);
        playerStore.addPlayer(DM_2);
        playerStore.addPlayer(DM_3);
        playerStore.addPlayer(DM_4);
        playerStore.addPlayer(DM_5);

        for(int i = 0; i < 20; i++) {
            Random random = new Random();
            Character character1 = new Character("1", houses[random.nextInt(houses.length)]);
            Character character2 = new Character("2", houses[random.nextInt(houses.length)]);
            Player player = new Player(character1.getHouse().toString() +"/" + character2.getHouse(), false);
            player.addCharacter(character1);
            player.addCharacter(character2);
            character1.setPlayer(player);
            character1.setMain(true);
            character2.setPlayer(player);
            character2.setMain(true);
            characterStore.addCharacter(character1);
            characterStore.addCharacter(character2);
            playerStore.addPlayer(player);

        }

        Character character1 = new Character("1", House.AVENTURINE);
        Character character2 = new Character("2", House.AMBER);
        Player player = new Player("BLACKLISTER", false);
        player.addCharacter(character1);
        player.addCharacter(character2);
        character1.setPlayer(player);
        character1.setMain(true);
        character2.setPlayer(player);
        character2.setMain(true);
        characterStore.addCharacter(character1);
        characterStore.addCharacter(character2);
        playerStore.addPlayer(player);

        Character character3 = new Character("1", House.AVENTURINE);
        Character character4 = new Character("2", House.AMBER);
        Player player2 = new Player("BLACKLISTEE", false);
        player2.addCharacter(character3);
        player2.addCharacter(character4);
        character4.setPlayer(player2);
        character4.setMain(true);
        character3.setPlayer(player2);
        character3.setMain(true);
        characterStore.addCharacter(character4);
        characterStore.addCharacter(character3);
        playerStore.addPlayer(player2);

        player.blacklist(player2);

        Character character5 = new Character("1", House.AVENTURINE);
        Character character6 = new Character("2", House.AMBER);
        Player player3 = new Player("BUDDY", false);
        player3.addCharacter(character5);
        player3.addCharacter(character6);
        character5.setPlayer(player3);
        character5.setMain(true);
        character6.setPlayer(player3);
        character6.setMain(true);
        characterStore.addCharacter(character5);
        characterStore.addCharacter(character6);
        playerStore.addPlayer(player3);

        player.getBuddylist().put(player3.getUuid(), player3);

        player.getPlayerLog().put(player3.getUuid(), LocalDate.of(2025, Month.AUGUST, 25));
        player3.getPlayerLog().put(player.getUuid(), LocalDate.of(2025, Month.AUGUST, 25));
    }

    private static void initSettings() {
        // MATCHMAKING
        settingsStore.getSettingsMap().put(Settings.HOUSE_BONUS, 500.0);
        settingsStore.getSettingsMap().put(Settings.HOUSE_SECOND_CHOICE_MULTIPLIER, 0.5);
        settingsStore.getSettingsMap().put(Settings.HOUSE_THIRD_CHOICE_MULTIPLIER, 0.25);
        settingsStore.getSettingsMap().put(Settings.HOUSE_FOURTH_CHOICE_MULTIPLIER, 0.1);


        settingsStore.getSettingsMap().put(Settings.MAIN_CHARACTER_MULTIPLIER, 2.0);

        settingsStore.getSettingsMap().put(Settings.BLACKLIST_BONUS, -5.0);
        settingsStore.getSettingsMap().put(Settings.BUDDY_BONUS, 3.0);


        settingsStore.getSettingsMap().put(Settings.RECENCY_GRUDGE, 4.0);
        settingsStore.getSettingsMap().put(Settings.MAX_REUNION_BONUS, 10.0);

  /*      List<House> amberPriorities;
        settingsStore.getSettingsMap().put(Settings.AMBER_PRIORITIES, ); */
    }
}
