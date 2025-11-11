package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.util.FuzzyStringMatcher; // Correctly imported!
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
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
 * Manages all interactions with the Google Sheets API, including authentication,
 * reading data, and writing data.
 *
 * This bean is 'session' scoped, meaning a new instance is created for each
 * user session. This is crucial for holding user-specific state like the
 * 'sheetsService' instance.
 */
@Service
// @Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS) // You are right, my love! This isn't needed for this app!
public class SheetsServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(SheetsServiceManager.class);

    // This is now an instance field, tied to a specific user's session.
    private Sheets sheetsService;

    private final SettingsStore settingsStore;
    private final SheetDataMapper sheetDataMapper;
    private final GoogleAuthManager googleAuthManager; // Injected!

    public SheetsServiceManager(SheetDataMapper sheetDataMapper, Store store, GoogleAuthManager googleAuthManager) {
        this.sheetDataMapper = sheetDataMapper;
        this.settingsStore = store.getSettingsStore();
        this.googleAuthManager = googleAuthManager; // Store the injected auth manager
        logger.info("SheetsServiceManager instance created (session-scoped).");
    }

    /**
     * Connects to Google Sheets by initiating a full user authentication flow.
     * @param urlDisplayer A callback to provide the authentication URL to the UI.
     * @throws IOException if credentials cannot be read or stored.
     */
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

    /**
     * Connects to Google Sheets using already stored credentials.
     * @throws IOException if stored credentials cannot be loaded or are invalid.
     */
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

    /**
     * A private helper to build the Sheets service once we have credentials.
     * @param credential The authenticated user credential.
     */
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

    /**
     * Appends the provided groups to the recap sheet and adds formatting.
     */
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

    // No longer static!
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

        Color lightGray = new Color().setRed(0.9f).setGreen(0.9f).setBlue(0.9f);
        Color lightRed = new Color().setRed(1f).setGreen(0.8f).setBlue(0.8f);
        Color darkerRed = new Color().setRed(0.95f).setGreen(0.75f).setBlue(0.75f);
        Color black = new Color().setRed(0f).setGreen(0f).setBlue(0f);
        Color white = new Color().setRed(1f).setGreen(1f).setBlue(1f);

        CellFormat grayFormat = new CellFormat().setBackgroundColor(lightGray);
        CellFormat whiteFormat = new CellFormat().setBackgroundColor(white);
        CellFormat lightRedFormat = new CellFormat().setBackgroundColor(lightRed);
        CellFormat darkerRedFormat = new CellFormat().setBackgroundColor(darkerRed);
        Border solidBorder = new Border().setStyle("SOLID").setColor(black);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();

        for (int i = 1; i < totalRows; i++) {
            GridRange rowRange = new GridRange().setSheetId(sheetId).setStartRowIndex(i).setEndRowIndex(i + 1);
            CellFormat format = (i % 2 == 0) ? grayFormat : whiteFormat;

            List<Object> row = allValues.get(i);
            if (row.size() > 6) {
                try {
                    LocalDate deadline = LocalDate.parse(row.get(6).toString(), formatter);
                    boolean recapIsEmpty = row.size() <= 2 || row.get(2) == null || row.get(2).toString().trim().isEmpty();
                    boolean isOverdue = deadline.isBefore(today);

                    logger.trace("Processing row {}. Deadline: {}. Recap empty: {}. Overdue: {}", i, deadline, recapIsEmpty, isOverdue);
                    if (isOverdue && recapIsEmpty) {
                        format = (i % 2 == 0) ? darkerRedFormat : lightRedFormat;
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse deadline for row {}. Skipping overdue check.", i, e);
                }
            }
            requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(rowRange)
                    .setCell(new CellData().setUserEnteredFormat(format))
                    .setFields("userEnteredFormat.backgroundColor")));
        }

        String updatedRange = appendResponse.getUpdates().getUpdatedRange();
        if (updatedRange != null) {
            Matcher matcher = Pattern.compile("(\\d+):").matcher(updatedRange.split("!")[1]);
            if (matcher.find()) {
                int startRowIndex = Integer.parseInt(matcher.group(1)) - 1;
                GridRange topBorderRange = new GridRange().setSheetId(sheetId).setStartRowIndex(startRowIndex).setEndRowIndex(startRowIndex + 1);
                requests.add(new Request().setUpdateBorders(new UpdateBordersRequest().setRange(topBorderRange).setTop(solidBorder)));
            }
        }

        GridRange allCellsRange = new GridRange().setSheetId(sheetId).setStartRowIndex(1).setEndRowIndex(totalRows).setStartColumnIndex(0).setEndColumnIndex(totalCols);
        requests.add(new Request().setRepeatCell(new RepeatCellRequest().setRange(allCellsRange).setCell(new CellData().setUserEnteredFormat(new CellFormat().setBorders(new Borders().setRight(solidBorder)))).setFields("userEnteredFormat.borders.right")));

        if (!requests.isEmpty()) {
            logger.debug("Applying {} formatting requests to sheet '{}'.", requests.size(), sheetName);
            BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest().setRequests(requests);
            // Use the instance field
            this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
            logger.info("Successfully applied all formatting requests.");
        }
    }

    // No longer static!
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

    // No longer static!
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
     * This is a modern, concise way to create a simple data-holding class.
     *
     * We now include optional UUIDs for both Player and Character.
     */
    public record PlayerData(String playerUuid, String charUuid, String player, String character, String sourceTab) {}

    /**
     * Imports Player and Character data from a specific tab using fuzzy header matching.
     * It will also look for optional Player UUID and Character UUID columns.
     *
     * @param spreadsheetId The ID of the Google Sheet.
     * @param tabName The name of the tab to import from (e.g., "Tab1").
     * @return A list of PlayerData records.
     * @throws IOException If the Sheets API call fails.
     */
    public List<PlayerData> importPlayerData(String spreadsheetId, String tabName) throws IOException {
        if (this.sheetsService == null) {
            logger.error("Cannot import data: Sheets service is not initialised.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        logger.info("Starting player data import from spreadsheet '{}', tab '{}'", spreadsheetId, tabName);

        // 1. Read the header row (first row) to find our columns
        String headerRange = tabName + "!1:1";
        ValueRange headerResponse = this.sheetsService.spreadsheets().values().get(spreadsheetId, headerRange).execute();
        List<Object> headerRow = (headerResponse.getValues() != null && !headerResponse.getValues().isEmpty())
                ? headerResponse.getValues().get(0) : null;

        if (headerRow == null || headerRow.isEmpty()) {
            logger.warn("Could not read header row from tab '{}'. Skipping.", tabName);
            return Collections.emptyList();
        }

        // 2. Find the column indices using fuzzy matching
        int playerColIndex = -1;
        int charColIndex = -1;
        int playerUuidColIndex = -1; // Our new addition!
        int charUuidColIndex = -1;   // And the second one!
        // A threshold of 1 is good for headers - it allows one typo or a plural 's' etc.
        final int HEADER_THRESHOLD = 1;

        for (int i = 0; i < headerRow.size(); i++) {
            if (headerRow.get(i) == null) continue; // Skip empty header cells

            String header = headerRow.get(i).toString().trim();

            // Using our lovely, clean utility class!
            if (FuzzyStringMatcher.areStringsSimilar(header, "Player", HEADER_THRESHOLD)) {
                playerColIndex = i;
                logger.info("Found 'Player' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character Name", HEADER_THRESHOLD)) {
                charColIndex = i;
                logger.info("Found 'Character Name' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            // And our new checks for the UUIDs!
            if (FuzzyStringMatcher.areStringsSimilar(header, "Player UUID", HEADER_THRESHOLD)) {
                playerUuidColIndex = i;
                logger.info("Found 'Player UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character UUID", HEADER_THRESHOLD)) {
                charUuidColIndex = i;
                logger.info("Found 'Character UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
        }

        // Log if we didn't find the optional UUIDs, but don't stop.
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
        if (playerColIndex == -1 || charColIndex == -1) {
            logger.warn("Could not find both 'Player' (found={}) and 'Character Name' (found={}) columns in tab '{}'. Skipping.",
                    playerColIndex != -1, charColIndex != -1, tabName);
            return Collections.emptyList();
        }

        // 4. Read the rest of the data (from row 2 onwards)
        // We just read from A2 to Z, which is almost always enough columns.
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

            // Helper to safely get string data from a specific column index
            // This now safely gets the UUIDs, or "" if the column wasn't found (index is -1)
            String playerUuid = getStringFromRow(row, playerUuidColIndex);
            String charUuid = getStringFromRow(row, charUuidColIndex);
            String player = getStringFromRow(row, playerColIndex);
            String character = getStringFromRow(row, charColIndex);

            // --- THIS IS YOUR LOVELY CHANGE! ---
            // Only add if the player has some value (character and UUIDs are optional)
            if (!player.isEmpty()) {
                // Add the new PlayerData record with both UUIDs
                importedData.add(new PlayerData(playerUuid, charUuid, player, character, tabName));
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
        // We add "index < 0" to safely handle cases where the column wasn't found
        if (index < 0 || row == null || row.size() <= index || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    public void disconnectSheetService() {
        logger.info("Disconnecting from Google Sheets. Clearing in-memory service.");
        this.sheetsService = null;
    }
}
