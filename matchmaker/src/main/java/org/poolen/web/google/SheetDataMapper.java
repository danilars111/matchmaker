package org.poolen.web.google;

import com.google.gson.*;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class responsible for mapping application data (Players, Characters)
 * to and from the format required by the Google Sheets API. This implementation
 * serializes entire objects to JSON for robustness.
 */
public class SheetDataMapper {

    // --- Persistence Section ---
    public static final String PLAYERS_SHEET_NAME = "PlayerData";
    public static final List<Object> PLAYERS_HEADER = Arrays.asList("Base64Data");

    // --- Group Announcement Section ---
    public static final String GROUPS_SHEET_NAME = "First Semester";
    public static final List<Object> GROUPS_HEADER = Arrays.asList("DM", "Players", "Adventure Description/Recap", "Datum Irl", "Recap Writer", "Kommentar", "Deadline");


    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private static final CharacterStore characterStore = CharacterStore.getInstance();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // A Gson instance configured with custom logic to prevent infinite loops.
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Player.class, new PlayerSerializer())
            .registerTypeAdapter(Player.class, new PlayerDeserializer()) // Our new, smart deserializer!
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // This still handles the Character -> Player back-reference
                    return f.getDeclaringClass() == Character.class && f.getName().equals("player");
                }
                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    /**
     * A custom serializer for the Player class to prevent infinite recursion
     * when saving data with circular references (e.g., blacklists).
     */
    private static class PlayerSerializer implements JsonSerializer<Player> {
        @Override
        public JsonElement serialize(Player src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", src.getUuid().toString());
            obj.addProperty("name", src.getName());
            obj.addProperty("isDungeonMaster", src.isDungeonMaster());
            if (src.getLastSeen() != null) {
                // Serialize LocalDate to a string
                obj.addProperty("lastSeen", DATE_FORMATTER.format(src.getLastSeen()));
            }

            obj.add("characters", context.serialize(src.getCharacters()));

            JsonObject playerLogJson = new JsonObject();
            if (src.getPlayerLog() != null) {
                // The Player class would now have Map<UUID, LocalDate>
                for(Map.Entry<UUID, LocalDate> entry : src.getPlayerLog().entrySet()){
                    playerLogJson.addProperty(entry.getKey().toString(), DATE_FORMATTER.format(entry.getValue()));
                }
            }
            obj.add("playerLog", playerLogJson);

            obj.add("buddylist", serializePlayerMap(src.getBuddylist()));
            obj.add("blacklist", serializePlayerMap(src.getBlacklist()));
            obj.add("DmBlacklist", serializePlayerMap(src.getDmBlacklist()));

            return obj;
        }

        private JsonObject serializePlayerMap(Map<UUID, Player> map) {
            JsonObject mapObj = new JsonObject();
            if (map != null) {
                for (Map.Entry<UUID, Player> entry : map.entrySet()) {
                    JsonObject stub = new JsonObject();
                    stub.addProperty("uuid", entry.getValue().getUuid().toString());
                    mapObj.add(entry.getKey().toString(), stub);
                }
            }
            return mapObj;
        }
    }

    /**
     * A custom deserializer for the Player class to correctly handle relationships and data types.
     */
    private static class PlayerDeserializer implements JsonDeserializer<Player> {
        @Override
        public Player deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
            String name = obj.get("name").getAsString();
            boolean isDm = obj.get("isDungeonMaster").getAsBoolean();
            Player player = new Player(uuid, name, isDm);

            if (obj.has("lastSeen")) {
                // Deserialize the string back to a LocalDate
                player.setLastSeen(LocalDate.parse(obj.get("lastSeen").getAsString(), DATE_FORMATTER));
            }

            if (obj.has("characters")) {
                JsonArray charsArray = obj.getAsJsonArray("characters");
                ArrayList<Character> characters = new ArrayList<>();
                for (JsonElement charElement : charsArray) {
                    characters.add(context.deserialize(charElement, Character.class));
                }
                player.setCharacters(characters);
            }

            if (obj.has("playerLog")) {
                JsonObject logObj = obj.getAsJsonObject("playerLog");
                Map<UUID, LocalDate> playerLog = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : logObj.entrySet()) {
                    playerLog.put(UUID.fromString(entry.getKey()), LocalDate.parse(entry.getValue().getAsString(), DATE_FORMATTER));
                }
                player.setPlayerLog(playerLog);
            }

            if (obj.has("buddylist")) player.setBuddylist(deserializePlayerMap(obj.getAsJsonObject("buddylist")));
            if (obj.has("blacklist")) player.setBlacklist(deserializePlayerMap(obj.getAsJsonObject("blacklist")));
            if (obj.has("DmBlacklist")) player.setDmBlacklist(deserializePlayerMap(obj.getAsJsonObject("DmBlacklist")));

            return player;
        }

        private Map<UUID, Player> deserializePlayerMap(JsonObject mapObj) {
            Map<UUID, Player> map = new HashMap<>();
            if (mapObj != null) {
                for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
                    JsonObject stubObj = entry.getValue().getAsJsonObject();
                    UUID playerUuid = UUID.fromString(stubObj.get("uuid").getAsString());
                    // Create a "stub" player object. The relinkPlayerMap method will fix this later.
                    map.put(playerUuid, new Player(playerUuid, "stub", false));
                }
            }
            return map;
        }
    }


    public static Map<String, List<List<Object>>> mapDataToSheets() {
        Map<String, List<List<Object>>> allSheetData = new HashMap<>();
        List<List<Object>> playerData = new ArrayList<>();

        for (Player player : playerStore.getAllPlayers()) {
            String json = gson.toJson(player);
            String base64Data = Base64.getEncoder().encodeToString(json.getBytes());
            playerData.add(Arrays.asList(base64Data));
        }

        allSheetData.put(PLAYERS_SHEET_NAME, playerData);
        return allSheetData;
    }

    public static void mapSheetsToData(Map<String, List<List<Object>>> sheetData) {
        List<List<Object>> playerData = sheetData.get(PLAYERS_SHEET_NAME);

        if (playerData == null || playerData.isEmpty()) {
            System.out.println("No data found in sheet, keeping existing data.");
            return;
        }

        playerStore.clear();
        characterStore.clear();

        for (List<Object> row : playerData) {
            if (row.isEmpty() || row.get(0) == null) continue;

            try {
                String base64Data = row.get(0).toString();
                String json = new String(Base64.getDecoder().decode(base64Data));
                Player player = gson.fromJson(json, Player.class);
                if (player != null) {
                    playerStore.addPlayer(player);
                }
            } catch (Exception e) {
                System.err.println("Skipping invalid player row: " + row + ". Reason: " + e.getMessage());
            }
        }

        // --- Step 2: Post-processing to re-link all object references ---
        for (Player player : playerStore.getAllPlayers()) {
            if (player.getCharacters() != null) {
                for (Character character : player.getCharacters()) {
                    character.setPlayer(player);
                    characterStore.addCharacter(character);
                }
            }
            relinkPlayerMap(player.getBlacklist());
            relinkPlayerMap(player.getBuddylist());
            relinkPlayerMap(player.getDmBlacklist());
        }
    }

    private static void relinkPlayerMap(Map<UUID, Player> mapToRelink) {
        if (mapToRelink == null || mapToRelink.isEmpty()) return;

        Map<UUID, Player> correctedMap = new HashMap<>();
        for (Player stubPlayer : mapToRelink.values()) {
            Player canonicalPlayer = playerStore.getPlayerByUuid(stubPlayer.getUuid());
            if (canonicalPlayer != null) {
                correctedMap.put(canonicalPlayer.getUuid(), canonicalPlayer);
            }
        }
        mapToRelink.clear();
        mapToRelink.putAll(correctedMap);
    }

    /**
     * Converts a list of generated groups into a human-readable format for appending to a sheet.
     * @param groupsToLog The list of groups to be logged.
     * @return A list of rows ready to be appended to the Google Sheet.
     */
    public static List<List<Object>> mapGroupsToSheet(List<Group> groupsToLog) {
        List<List<Object>> groupData = new ArrayList<>();
        // We no longer add a blank line here! The border will be our separator.

        for (Group group : groupsToLog) {
            List<Object> row = new ArrayList<>();
            LocalDate sessionDate = group.getDate();
            if (sessionDate == null) sessionDate = LocalDate.now(); // Fallback to today's date

            // --- Calculate Deadline ---
            LocalDate deadlineDate = sessionDate.plusMonths(1);

            // --- Assemble Row in Correct Order ---
            // 1. DM
            row.add(group.getDungeonMaster() != null ? group.getDungeonMaster().getName() : "N/A");
            // 2. Players
            String playerNames = group.getParty().values().stream()
                    .map(Player::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            row.add(playerNames);
            // 3. Adventure Description/Recap (Empty)
            row.add("");
            // 4. Datum Irl
            row.add(sessionDate.format(DATE_FORMATTER));
            // 5. Recap Writer (Empty)
            row.add("");
            // 6. Kommentar (Empty)
            row.add("");
            // 7. Deadline
            row.add(deadlineDate.format(DATE_FORMATTER));

            groupData.add(row);
        }
        return groupData;
    }
}

