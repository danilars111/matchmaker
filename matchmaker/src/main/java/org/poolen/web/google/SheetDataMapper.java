package org.poolen.web.google;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISettings;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.SettingsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class responsible for mapping application data (Players, Characters, Settings)
 * to and from the format required by the Google Sheets API.
 */
@Service
@Lazy
public class SheetDataMapper {

    // --- Headers ---
    public static final List<Object> GROUPS_HEADER = Arrays.asList("DM", "Players", "Adventure Description/Recap", "Datum Irl", "Recap Writer", "Kommentar", "Deadline");

    private final PlayerStore playerStore;
    private final CharacterStore characterStore;
    private final SettingsStore settingsStore;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Player.class, new PlayerSerializer())
            .registerTypeAdapter(Player.class, new PlayerDeserializer())
            .registerTypeAdapter(Setting.class, new SettingDeserializer())
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getDeclaringClass() == Character.class && f.getName().equals("player");
                }
                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();
    @Autowired
    public SheetDataMapper(CharacterStore characterStore, PlayerStore playerStore, SettingsStore settingsStore) {
        this.characterStore = characterStore;
        this.playerStore = playerStore;
        this.settingsStore = settingsStore;
    }

    // --- Custom (De)serializers ---

    private static class PlayerSerializer implements JsonSerializer<Player> {
        @Override
        public JsonElement serialize(Player src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", src.getUuid().toString());
            obj.addProperty("name", src.getName());
            obj.addProperty("isDungeonMaster", src.isDungeonMaster());
            if (src.getLastSeen() != null) {
                obj.addProperty("lastSeen", DATE_FORMATTER.format(src.getLastSeen()));
            }
            obj.add("characters", context.serialize(src.getCharacters()));
            JsonObject playerLogJson = new JsonObject();
            if (src.getPlayerLog() != null) {
                for(Map.Entry<UUID, LocalDate> entry : src.getPlayerLog().entrySet()){
                    playerLogJson.addProperty(entry.getKey().toString(), DATE_FORMATTER.format(entry.getValue()));
                }
            }
            obj.add("playerLog", playerLogJson);
/*            obj.add("buddylist", serializePlayerMap(src.getBuddylist()));
            obj.add("blacklist", serializePlayerMap(src.getBlacklist()));
            obj.add("DmBlacklist", serializePlayerMap(src.getDmBlacklist()));*/
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

    private static class PlayerDeserializer implements JsonDeserializer<Player> {
        @Override
        public Player deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
            String name = obj.get("name").getAsString();
            boolean isDm = obj.get("isDungeonMaster").getAsBoolean();
            Player player = new Player(uuid, name, isDm);

            // ... (rest of the code for lastSeen, playerLog, buddylist, etc. remains the same)

            if (obj.has("lastSeen")) {
                player.setLastSeen(LocalDate.parse(obj.get("lastSeen").getAsString(), DATE_FORMATTER));
            }

            // 👇 **THIS IS THE BIT THAT CHANGES!** 👇
            if (obj.has("characters")) {
                JsonArray charsArray = obj.getAsJsonArray("characters");

                // Changed from ArrayList to HashSet!
                Set<Character> characters = new HashSet<>();

                for (JsonElement charElement : charsArray) {
                    characters.add(context.deserialize(charElement, Character.class));
                }

                // Assuming your Player class now uses setCharacters(Set<Character> characters)
                player.setCharacters(characters);
            }
            // 👆 **END OF THE CHANGE** 👆

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

        private Set<UUID> deserializePlayerMap(JsonObject mapObj) {
            Map<UUID, Player> map = new HashMap<>();
            if (mapObj != null) {
                for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
                    JsonObject stubObj = entry.getValue().getAsJsonObject();
                    UUID playerUuid = UUID.fromString(stubObj.get("uuid").getAsString());
                    map.put(playerUuid, new Player(playerUuid, "stub", false));
                }
            }
            Set<UUID> set = new HashSet();
            for(UUID uuid : map.keySet()) {
                set.add(uuid);
            }
            return set;
        }
    }

    private static class SettingDeserializer implements JsonDeserializer<Setting> {
        @Override
        public Setting deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String nameStr = obj.get("name").getAsString();
            String description = obj.get("description").getAsString();
            JsonElement valueElement = obj.get("settingValue");

            ISettings settingEnum = findSettingEnum(nameStr);
            if (settingEnum == null) {
                return null;
            }

            Object value = null;
            if (settingEnum instanceof Settings.MatchmakerBonusSettings || settingEnum instanceof Settings.MatchmakerMultiplierSettings) {
                value = valueElement.getAsDouble();
            } else if (settingEnum instanceof Settings.PersistenceSettings) {
                // Handle both String and Integer for PersistenceSettings
                if (settingEnum == Settings.PersistenceSettings.RECAP_DEADLINE) {
                    value = valueElement.getAsInt();
                } else {
                    value = valueElement.getAsString();
                }
            } else if (settingEnum instanceof Settings.MatchmakerPrioritySettings) {
                Type listType = new TypeToken<ArrayList<House>>() {}.getType();
                value = context.deserialize(valueElement, listType);
            }

            return new Setting<>(settingEnum, description, value);
        }

        private ISettings findSettingEnum(String name) {
            for (Class<? extends ISettings> category : List.of(Settings.MatchmakerBonusSettings.class, Settings.MatchmakerMultiplierSettings.class, Settings.MatchmakerPrioritySettings.class, Settings.PersistenceSettings.class)) {
                for (ISettings setting : category.getEnumConstants()) {
                    if (setting.toString().equals(name)) {
                        return setting;
                    }
                }
            }
            return null;
        }
    }




    /**
     * Converts all application data (players and settings) into a format for Google Sheets.
     */
    public Map<String, List<List<Object>>> mapDataToSheets() {
        Map<String, List<List<Object>>> allSheetData = new HashMap<>();
        String playerDataSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.PLAYER_DATA_SHEET_NAME).getSettingValue();
        String settingsDataSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.SETTINGS_DATA_SHEET_NAME).getSettingValue();

        // --- Players ---
        List<List<Object>> playerData = new ArrayList<>();
        for (Player player : playerStore.getAllPlayers()) {
            String json = gson.toJson(player);
            String base64Data = Base64.getEncoder().encodeToString(json.getBytes());
            playerData.add(Collections.singletonList(base64Data));
        }
        allSheetData.put(playerDataSheetName, playerData);

        // --- Settings ---
        List<List<Object>> settingsData = new ArrayList<>();
        List<Setting> settingsList = new ArrayList<>(settingsStore.getSettingsMap().values());
        String json = gson.toJson(settingsList);
        String base64Data = Base64.getEncoder().encodeToString(json.getBytes());
        settingsData.add(Collections.singletonList(base64Data));
        allSheetData.put(settingsDataSheetName, settingsData);

        return allSheetData;
    }

    /**
     * Takes raw data from Google Sheets and populates the application's data stores.
     */
    public void mapSheetsToData(Map<String, List<List<Object>>> sheetData) {
        String playerDataSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.PLAYER_DATA_SHEET_NAME).getSettingValue();
        String settingsDataSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.SETTINGS_DATA_SHEET_NAME).getSettingValue();

        // --- Load Settings First ---
        List<List<Object>> settingsData = sheetData.get(settingsDataSheetName);
        if (settingsData != null && !settingsData.isEmpty() && !settingsData.get(0).isEmpty()) {
            try {
                String base64Data = settingsData.get(0).get(0).toString();
                String json = new String(Base64.getDecoder().decode(base64Data));

                Type listType = new TypeToken<ArrayList<Setting>>() {}.getType();
                List<Setting> loadedSettings = gson.fromJson(json, listType);

                for (Setting<?> setting : loadedSettings) {
                    if (setting != null) {
                        settingsStore.updateSetting(setting.getName(), setting.getSettingValue());
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not load settings: " + e.getMessage());
            }
        }

        // --- Load Players ---
        playerStore.clear();
        characterStore.clear();
        List<List<Object>> playerData = sheetData.get(playerDataSheetName);
        if (playerData == null || playerData.isEmpty()) {
            System.out.println("No player data found in sheet");
            return;
        }



        for (List<Object> row : playerData) {
            if (row.isEmpty() || row.get(0) == null) continue;
            try {
                String base64Data = row.get(0).toString();
                String json = new String(Base64.getDecoder().decode(base64Data));
                Player player = gson.fromJson(json, Player.class);
                if (player != null) playerStore.addPlayer(player);
            } catch (Exception e) {
                System.err.println("Skipping invalid player row. Reason: " + e.getMessage());
            }
        }

        for (Player player : playerStore.getAllPlayers()) {
            if (player.getCharacters() != null) {
                for (Character character : player.getCharacters()) {
                    character.setPlayer(player);
                    characterStore.addCharacter(character);
                }
            }
/*            relinkPlayerMap(player.getBlacklist());
            relinkPlayerMap(player.getBuddylist());
            relinkPlayerMap(player.getDmBlacklist());*/
        }
    }

/*
    private Set relinkPlayerMap(Map<UUID, Player> mapToRelink) {
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
        Set set = new HashSet<>();
        for(UUID uuid : correctedMap.keySet()) {
            set.add(uuid);
        }
        return set;
    }
*/

    public List<List<Object>> mapGroupsToSheet(List<Group> groupsToLog) {
        List<List<Object>> groupData = new ArrayList<>();
        // Get the deadline from our new setting!
        int deadlineInWeeks = (Integer) settingsStore.getSetting(Settings.PersistenceSettings.RECAP_DEADLINE).getSettingValue();

        for (Group group : groupsToLog) {
            List<Object> row = new ArrayList<>();
            LocalDate sessionDate = group.getDate();
            if (sessionDate == null) sessionDate = LocalDate.now();

            // Use the setting to calculate the deadline
            LocalDate deadlineDate = sessionDate.plusWeeks(deadlineInWeeks);

            row.add(group.getDungeonMaster() != null ? group.getDungeonMaster().getName() : "N/A");
            String playerNames = group.getParty().values().stream()
                    .map(Player::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            row.add(playerNames);
            row.add("");
            row.add(sessionDate.format(DATE_FORMATTER));
            row.add("");
            row.add("");
            row.add(deadlineDate.format(DATE_FORMATTER));
            groupData.add(row);
        }
        return groupData;
    }
}

