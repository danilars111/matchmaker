package org.poolen.frontend.util.services;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A utility service to generate and save dummy data for testing purposes.
 * It is now a Spring-managed service that uses factories to create and persist data.
 */
@Service
public class TestDataGenerator {

    private final PlayerFactory playerFactory;
    private final CharacterFactory characterFactory;
    private final List<String> names;
    private final Random random = new Random();

    @Autowired
    public TestDataGenerator(PlayerFactory playerFactory, CharacterFactory characterFactory) {
        this.playerFactory = playerFactory;
        this.characterFactory = characterFactory;
        this.names = loadNames();
    }

    /**
     * Generates a specified number of unique players, each with two characters,
     * and saves them directly to the database via the factories.
     *
     * @param playerCount The number of unique players to generate.
     */
    public void generate(int playerCount) {
        if (names.isEmpty()) {
            System.err.println("Cannot generate test data: name list is empty.");
            return;
        }

        if (playerCount > names.size()) {
            System.err.println("Cannot generate " + playerCount + " unique players, only " + names.size() + " names available.");
            playerCount = names.size();
        }

        List<String> shuffledPlayerNames = new ArrayList<>(names);
        Collections.shuffle(shuffledPlayerNames);

        int dmCount = (int) Math.ceil(playerCount / 6.0);

        for (int i = 0; i < playerCount; i++) {
            boolean isDm = i < dmCount;
            Player player = playerFactory.create(shuffledPlayerNames.get(i), isDm);

            int mainCharacterIndex = random.nextInt(2);
            for (int j = 0; j < 2; j++) {
                boolean isMain = (j == mainCharacterIndex);
                String characterName = names.get(random.nextInt(names.size()));
                House randomHouse = House.values()[random.nextInt(House.values().length)];
                characterFactory.create(player, characterName, randomHouse, isMain);
            }
        }
        System.out.println("Successfully generated and saved " + playerCount + " players with characters.");
    }

    /**
     * Loads the list of names from the resource file.
     *
     * @return A list of names.
     */
    private List<String> loadNames() {
        try (InputStream is = getClass().getResourceAsStream("/test/names.txt")) {
            if (is == null) {
                System.err.println("Could not find names.txt in resources/test");
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}

