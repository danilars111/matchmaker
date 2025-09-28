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
import org.poolen.backend.db.store.Store;
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

    private final SettingsStore settingsStore;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Autowired
    public SheetDataMapper(Store store) {
        this.settingsStore = store.getSettingsStore();
    }

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

