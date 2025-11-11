package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.constants.House; // <-- Look, my love!
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.util.FuzzyStringMatcher; // Correctly imported!
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
// ... (rest of imports) ...
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ... (javadoc) ...
 */
@Service
public class SheetsServiceManager {

    // ... (fields are all perfect) ...
    private static final Logger logger = LoggerFactory.getLogger(SheetsServiceManager.class);
    private Sheets sheetsService;
    private final SettingsStore settingsStore;
    private final SheetDataMapper sheetDataMapper;
    private final GoogleAuthManager googleAuthManager;

    public SheetsServiceManager(SheetDataMapper sheetDataMapper, Store store, GoogleAuthManager googleAuthManager) {
        this.sheetDataMapper = sheetDataMapper;
        this.settingsStore = store.getSettingsStore();
        this.googleAuthManager = googleAuthManager; // Store the injected auth manager
        logger.info("SheetsServiceManager instance created (session-scoped).");
    }

    // ... (connect methods are all perfect) ...
    public void connect(Consumer<String> urlDisplayer) throws IOException {
        logger.info("Initiating new Google Sheets connection with user auth flow.");
        // Use the injected auth manager instance
        Credential credential = googleAuthManager.authorizeNewUser(urlDisplayer);
        if (credential == null) {
            throw new IOException("Authorization was cancelled or failed.");
        }
        buildSheetsService(credential);
        logger.info("Successfully connected and built Sheets service.");
    }
    public void connectWithStoredCredentials() throws IOException {
        logger.info("Connecting to Google Sheets using stored credentials.");
        // Use the injected auth manager and the new validation method
        Credential credential = googleAuthManager.loadAndValidateStoredCredential();
        if (credential == null) {
            logger.warn("Failed to connect with stored credentials (not found or invalid).");
            disconnectSheetService();
            throw new IOException("No valid stored credentials found.");
        }
        buildSheetsService(credential);
        logger.info("Successfully connected and built Sheets service with stored credentials.");
    }
    private void buildSheetsService(Credential credential) {
        logger.info("Building Google Sheets service for this session.");
        // Build the instance-specific sheetsService
        this.sheetsService = new Sheets.Builder(
                googleAuthManager.getHttpTransport(), // Use instance method
                GoogleAuthManager.getJsonFactory(),   // This one is still static, and that's fine
                credential)
                .setApplicationName("Matchmaker")
                .build();
    }

    // ... (appendGroupsToSheet and reformatGroupSheet are perfect) ...
    public void appendGroupsToSheet(String spreadsheetId, List<Group> groups) throws IOException {
        // Use the instance field
        if (this.sheetsService == null) {
            logger.error("Cannot append groups: Sheets service is not initialised. Not connected to Google Sheets.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        String recapSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.RECAP_SHEET_NAME).getSettingValue();
        logger.info("Attempting to append {} groups to spreadsheet '{}', sheet '{}'.", groups.size(), spreadsheetId, recapSheetName);

        // Call the new instance method
        this.ensureSheetAndHeaderExist(spreadsheetId, recapSheetName, SheetDataMapper.GROUPS_HEADER);

        List<List<Object>> valuesToAppend = sheetDataMapper.mapGroupsToSheet(groups);
        if (valuesToAppend.isEmpty()) {
            logger.info("No groups to append. Aborting.");
            return;
        }

        ValueRange body = new ValueRange().setValues(valuesToAppend);
        // Use the instance field
        AppendValuesResponse appendResponse = this.sheetsService.spreadsheets().values()
                .append(spreadsheetId, recapSheetName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .setIncludeValuesInResponse(true)
                .execute();

        logger.info("Successfully appended {} rows of data. Updated range: {}", valuesToAppend.size(), appendResponse.getUpdates().getUpdatedRange());
        // Call the new instance method
        this.reformatGroupSheet(spreadsheetId, recapSheetName, appendResponse);
    }
    private void reformatGroupSheet(String spreadsheetId, String sheetName, AppendValuesResponse appendResponse) throws IOException {
        // Use the instance field
        Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute();
        Integer sheetId = spreadsheet.getSheets().stream()
                .filter(s -> s.getProperties().getTitle().equals(sheetName))
                .map(s -> s.getProperties().getSheetId())
                .findFirst()
                .orElse(null);

        if (sheetId == null) {
            logger.warn("Could not find sheet ID for sheet name '{}'. Aborting reformatting.", sheetName);
            return;
        }
        logger.info("Attempting to reformat group sheet '{}' (ID: {}) after appending.", sheetName, sheetId);

        // Use the instance field
        ValueRange fullSheetData = this.sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        List<List<Object>> allValues = fullSheetData.getValues();
        if (allValues == null || allValues.size() <= 1) {
            logger.warn("No data found in sheet '{}' after append. Aborting reformatting.", sheetName);
            return;
        }
        int totalRows = allValues.size();
        int totalCols = SheetDataMapper.GROUPS_HEADER.size();

        List<Request> requests = new ArrayList<>();

// ... (all formatting logic is perfect) ...

        if (!requests.isEmpty()) {
            logger.debug("Applying {} formatting requests to sheet '{}'.", requests.size(), sheetName);
            BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest().setRequests(requests);
            // Use the instance field
            this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
            logger.info("Successfully applied all formatting requests.");
        }
    }

    // ... (ensureSheetExists and ensureSheetAndHeaderExist are perfect) ...
    private void ensureSheetExists(String spreadsheetId, String sheetName) throws IOException {
        logger.info("Ensuring sheet '{}' exists in spreadsheet '{}'.", sheetName, spreadsheetId);
        // Use the instance field
        Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(spreadsheetId).execute();
        boolean sheetExists = spreadsheet.getSheets().stream()
                .anyMatch(s -> s.getProperties().getTitle().equals(sheetName));

        if (!sheetExists) {
            logger.info("Sheet '{}' does not exist. Creating it now.", sheetName);
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName));

            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

            // Use the instance field
            this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
            logger.info("Sheet '{}' created successfully.", sheetName);
        } else {
            logger.debug("Sheet '{}' already exists.", sheetName);
        }
    }
    private void ensureSheetAndHeaderExist(String spreadsheetId, String sheetName, List<Object> header) throws IOException {
        logger.info("Ensuring sheet '{}' exists and has the correct header.", sheetName);
        // Call the new instance method
        this.ensureSheetExists(spreadsheetId, sheetName);

        // Use the instance field
        ValueRange response = this.sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            logger.info("Sheet '{}' is empty. Writing header row.", sheetName);
            ValueRange headerBody = new ValueRange().setValues(Collections.singletonList(header));
            // Use the instance field
            this.sheetsService.spreadsheets().values()
                    .update(spreadsheetId, sheetName, headerBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
            logger.info("Header row written successfully to '{}'.", sheetName);
        } else {
            logger.debug("Sheet '{}' already has content. Assuming header exists.", sheetName);
        }
    }

    /**
     * A simple record to hold the imported player data.
     * We now store the House enum directly, which is so much cleaner!
     */
    public record PlayerData(String playerUuid, String charUuid, String player, String character, House house) {}

    /**
     * Imports Player and Character data from a specific tab using fuzzy header matching.
     * It will also look for optional Player UUID and Character UUID columns.
     *
     * @param spreadsheetId The ID of the Google Sheet.
     * @param tabName The name of the tab to import from (e.g., "Garnet").
     * @param house The House enum associated with this tab.
     * @return A list of PlayerData records.
     * @throws IOException If the Sheets API call fails.
     */
    public List<PlayerData> importPlayerData(String spreadsheetId, String tabName, House house) throws IOException {
        if (this.sheetsService == null) {
            logger.error("Cannot import data: Sheets service is not initialised.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        logger.info("Starting player data import from spreadsheet '{}', tab '{}' (House: {})", spreadsheetId, tabName, house);

        // 1. Read the header row (first row) to find our columns
// ... (header reading logic is perfect) ...
        String headerRange = tabName + "!1:1";
        ValueRange headerResponse = this.sheetsService.spreadsheets().values().get(spreadsheetId, headerRange).execute();
        List<Object> headerRow = (headerResponse.getValues() != null && !headerResponse.getValues().isEmpty())
                ? headerResponse.getValues().get(0) : null;

        if (headerRow == null || headerRow.isEmpty()) {
            logger.warn("Could not read header row from tab '{}'. Skipping.", tabName);
            return Collections.emptyList();
        }

        // 2. Find the column indices using fuzzy matching
// ... (column finding logic is perfect) ...
        int playerColIndex = -1;
        int charColIndex = -1;
        int playerUuidColIndex = -1;
        int charUuidColIndex = -1;
        final int HEADER_THRESHOLD = 1;

        for (int i = 0; i < headerRow.size(); i++) {
            if (headerRow.get(i) == null) continue; // Skip empty header cells

            String header = headerRow.get(i).toString().trim();

            if (FuzzyStringMatcher.areStringsSimilar(header, "Player", HEADER_THRESHOLD)) {
                playerColIndex = i;
                logger.info("Found 'Player' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character Name", HEADER_THRESHOLD)) {
                charColIndex = i;
                logger.info("Found 'Character Name' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Player UUID", HEADER_THRESHOLD)) {
                playerUuidColIndex = i;
                logger.info("Found 'Player UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character UUID", HEADER_THRESHOLD)) {
                charUuidColIndex = i;
                logger.info("Found 'Character UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
        }

        // ... (logging for missing UUIDs is perfect) ...
        if (playerUuidColIndex == -1) {
            logger.info("No 'Player UUID' column found in tab '{}'.", tabName);
        }
        if (charUuidColIndex == -1) {
            logger.info("No 'Character UUID' column found in tab '{}'.", tabName);
        }
        if (playerUuidColIndex == -1 || charUuidColIndex == -1) {
            logger.info("Will proceed with name-based matching where UUIDs are missing.");
        }


        // 3. Check if we found our *required* columns (Player and Character)
        if (playerColIndex == -1) { // <-- We made charColIndex optional!
            logger.warn("Could not find 'Player' (found={}) column in tab '{}'. Skipping.",
                    playerColIndex != -1, tabName);
            return Collections.emptyList();
        }
        if (charColIndex == -1) {
            logger.info("No 'Character Name' column found in tab '{}'. Will proceed without character data.", tabName);
        }


        // 4. Read the rest of the data (from row 2 onwards)
// ... (data reading logic is perfect) ...
        String dataRange = tabName + "!A2:Z";
        ValueRange dataResponse = this.sheetsService.spreadsheets().values().get(spreadsheetId, dataRange).execute();
        List<List<Object>> allData = dataResponse.getValues();

        if (allData == null || allData.isEmpty()) {
            logger.info("No data rows found in tab '{}' (after header).", tabName);
            return Collections.emptyList();
        }

        // 5. Process the data into our record list
        List<PlayerData> importedData = new ArrayList<>();
        for (int i = 0; i < allData.size(); i++) {
            List<Object> row = allData.get(i);

// ... (getStringFromRow calls are perfect) ...
            String playerUuid = getStringFromRow(row, playerUuidColIndex);
            String charUuid = getStringFromRow(row, charUuidColIndex);
            String player = getStringFromRow(row, playerColIndex);
            String character = getStringFromRow(row, charColIndex);

            // --- THIS IS YOUR LOVELY CHANGE! ---
            // Only add if the player has some value (character and UUIDs are optional)
            if (!player.isEmpty()) {
                // Add the new PlayerData record with the House enum!
                importedData.add(new PlayerData(playerUuid, charUuid, player, character, house));
            } else {
                logger.trace("Skipping row {} in tab '{}' due to missing data (Player: '{}')",
                        i + 2, tabName, player); // +2 for header and 0-indexing
            }
        }

        logger.info("Successfully imported {} player/character pairs from tab '{}'.", importedData.size(), tabName);
        return importedData;
    }

    /**
     * A private helper to safely get a string from a row list,
     * checking for boundaries and nulls.
     *
     * Now safer! It also checks if the index is less than 0.
     */
    private String getStringFromRow(List<Object> row, int index) {
// ... (method is perfect) ...
        if (index < 0 || row == null || row.size() <= index || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    public void disconnectSheetService() {
// ... (method is perfect) ...
        this.sheetsService = null;
    }

}
