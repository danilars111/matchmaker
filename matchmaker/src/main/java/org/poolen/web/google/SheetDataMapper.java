package org.poolen.web.google;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.CharacterFactory;
import org.poolen.backend.db.factories.PlayerFactory;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A utility class responsible for mapping application data (Players, Characters)
 * to and from the format required by the Google Sheets API (List<List<Object>>).
 */
public class SheetDataMapper {

    // Define the names for our sheets
    public static final String PLAYERS_SHEET_NAME = "Players";
    public static final String CHARACTERS_SHEET_NAME = "Characters";

    // Define the headers for our columns to ensure consistency
    public static final List<Object> PLAYERS_HEADER = Arrays.asList("UUID", "Name", "IsDungeonMaster", "Blacklist");
    public static final List<Object> CHARACTERS_HEADER = Arrays.asList("UUID", "PlayerUUID", "Name", "House", "IsMain", "IsRetired");

    private static PlayerStore playerStore = PlayerStore.getInstance();
    private static CharacterStore characterStore = CharacterStore.getInstance();

    /**
     * Converts all player and character data from the application's stores into a format
     * suitable for writing to Google Sheets.
     *
     * @return A map where the key is the sheet name and the value is the data for that sheet.
     */
    public static Map<String, List<List<Object>>> mapDataToSheets() {
        Map<String, List<List<Object>>> allSheetData = new HashMap<>();

        // --- Map Players ---
        List<List<Object>> playerData = new ArrayList<>();
        playerData.add(PLAYERS_HEADER); // Add the header row first
        for (Player player : playerStore.getAllPlayers()) {
            // Convert the blacklist map to a comma-separated string of UUIDs
            String blacklistString = player.getBlacklist().keySet().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));

            playerData.add(Arrays.asList(
                    player.getUuid().toString(),
                    player.getName(),
                    player.isDungeonMaster(),
                    blacklistString
            ));
        }
        allSheetData.put(PLAYERS_SHEET_NAME, playerData);


        // --- Map Characters ---
        List<List<Object>> characterData = new ArrayList<>();
        characterData.add(CHARACTERS_HEADER); // Add the header row first
        for (Character character : characterStore.getAllCharacters()) {
            characterData.add(Arrays.asList(
                    character.getUuid().toString(),
                    character.getPlayer() != null ? character.getPlayer().getUuid().toString() : "",
                    character.getName(),
                    character.getHouse().name(),
                    character.isMain(),
                    character.isRetired()
            ));
        }
        allSheetData.put(CHARACTERS_SHEET_NAME, characterData);

        return allSheetData;
    }

    /**
     * Takes raw data from Google Sheets and uses it to populate the application's data stores.
     *
     * @param sheetData A map where the key is the sheet name and the value is the raw data.
     */
    public static void mapSheetsToData(Map<String, List<List<Object>>> sheetData) {
        PlayerFactory playerFactory = PlayerFactory.getInstance();
        CharacterFactory characterFactory = CharacterFactory.getInstance();


        // Clear existing data before loading
        playerStore.clear();
        characterStore.clear();

        // --- Load Players ---
        List<List<Object>> playerData = sheetData.get(PLAYERS_SHEET_NAME);
        if (playerData != null && playerData.size() > 1) {
            for (int i = 1; i < playerData.size(); i++) { // Skip header row
                List<Object> row = playerData.get(i);
                try {
                    playerFactory.create(
                            UUID.fromString(getString(row, 0)),
                            getString(row, 1),
                            getBoolean(row, 2)
                    );
                } catch (Exception e) {
                    System.err.println("Skipping invalid player row: " + row);
                }
            }
        }

        // --- Load Characters ---
        List<List<Object>> characterData = sheetData.get(CHARACTERS_SHEET_NAME);
        if (characterData != null && characterData.size() > 1) {
            // We sort the rows to ensure non-main characters are created first,
            // to avoid issues with the factory's "only one main" rule.
            List<List<Object>> sortedCharacterData = characterData.stream()
                    .skip(1) // Skip header
                    .sorted(Comparator.comparing(row -> getBoolean(row, 4))) // false (non-main) comes before true (main)
                    .collect(Collectors.toList());

            for (List<Object> row : sortedCharacterData) {
                try {
                    Player owner = playerStore.getPlayerByUuid(UUID.fromString(getString(row, 1)));
                    if (owner != null) {
                        Character character = characterFactory.create(
                                UUID.fromString(getString(row, 0)),
                                owner,
                                getString(row, 2),
                                House.valueOf(getString(row, 3)),
                                getBoolean(row, 4)
                        );
                        // The factory assumes new characters are not retired, so we set it manually after creation.
                        boolean isRetired = getBoolean(row, 5);
                        if (character.isRetired() != isRetired) {
                            character.setRetired(isRetired);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Skipping invalid character row: " + row + ". Reason: " + e.getMessage());
                }
            }
        }

        // --- Re-link Blacklists (must be done after all players are loaded) ---
        if (playerData != null && playerData.size() > 1) {
            for (int i = 1; i < playerData.size(); i++) {
                List<Object> row = playerData.get(i);
                Player player = playerStore.getPlayerByUuid(UUID.fromString(getString(row, 0)));
                String blacklistString = getString(row, 3);
                if (player != null && !blacklistString.isEmpty()) {
                    String[] blacklistedUuids = blacklistString.split(",");
                    for (String uuidStr : blacklistedUuids) {
                        Player blacklistedPlayer = playerStore.getPlayerByUuid(UUID.fromString(uuidStr.trim()));
                        if (blacklistedPlayer != null) {
                            player.blacklist(blacklistedPlayer);
                        }
                    }
                }
            }
        }
    }

    // Helper method to safely get a string from a row cell
    private static String getString(List<Object> row, int index) {
        return (row.size() > index && row.get(index) != null) ? row.get(index).toString() : "";
    }

    // Helper method to safely get a boolean from a row cell
    private static boolean getBoolean(List<Object> row, int index) {
        return (row.size() > index && row.get(index) != null) ? Boolean.parseBoolean(row.get(index).toString()) : false;
    }
}

