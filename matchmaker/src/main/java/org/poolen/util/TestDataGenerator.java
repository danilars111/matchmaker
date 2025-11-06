package org.poolen.frontend.util.services;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(TestDataGenerator.class);

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
        logger.info("Attempting to generate {} test players...", playerCount);
        if (names.isEmpty()) {
            logger.error("Cannot generate test data: name list is empty or failed to load.");
            return;
        }

        if (playerCount > names.size()) {
            logger.warn("Cannot generate {} unique players, only {} names available. Generating {} instead.", playerCount, names.size(), names.size());
            playerCount = names.size();
        }

        List<String> shuffledPlayerNames = new ArrayList<>(names);
        Collections.shuffle(shuffledPlayerNames);

        int dmCount = (int) Math.ceil(playerCount / 6.0);
        logger.debug("Generating {} players with {} DMs.", playerCount, dmCount);

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
        logger.info("Successfully generated and saved {} players with characters.", playerCount);
    }

    /**
     * Loads the list of names from the resource file.
     *
     * @return A list of names.
     */
    private List<String> loadNames() {
        logger.info("Loading names from /test/names.txt...");
        try (InputStream is = getClass().getResourceAsStream("/test/names.txt")) {
            if (is == null) {
                logger.error("Could not find names.txt in resources/test. Test data generation will fail.");
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                List<String> loadedNames = reader.lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
                logger.info("Successfully loaded {} names from resources.", loadedNames.size());
                return loadedNames;
            }
        } catch (IOException e) {
            logger.error("Failed to read names.txt from resources.", e);
            return Collections.emptyList();
        }
    }
}
