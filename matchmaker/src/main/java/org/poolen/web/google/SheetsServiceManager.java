package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.db.store.Store;
import org.poolen.backend.util.FuzzyStringMatcher;
// import org.poolen.frontend.gui.components.stages.ImportMatcherStage.ImportMatchTask; // <-- GONE! So much cleaner!
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SheetsServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(SheetsServiceManager.class);
    private static final int HEADER_THRESHOLD = 1;

    private Sheets sheetsService;
    private final SettingsStore settingsStore;
    private final SheetDataMapper sheetDataMapper;
    private final GoogleAuthManager googleAuthManager;

    private final Map<String, Integer> sheetIdCache = new HashMap<>();

    public SheetsServiceManager(SheetDataMapper sheetDataMapper, Store store, GoogleAuthManager googleAuthManager) {
        this.sheetDataMapper = sheetDataMapper;
        this.settingsStore = store.getSettingsStore();
        this.googleAuthManager = googleAuthManager;
        logger.info("SheetsServiceManager instance created.");
    }

    // ... (connect, buildSheetsService, appendGroupsToSheet, reformatGroupSheet, ensureSheetExists, ensureSheetAndHeaderExist remain the same) ...
    public void connect(Consumer<String> urlDisplayer) throws IOException {
        logger.info("Initiating new Google Sheets connection with user auth flow.");
        Credential credential = googleAuthManager.authorizeNewUser(urlDisplayer);
        if (credential == null) {
            throw new IOException("Authorization was cancelled or failed.");
        }
        buildSheetsService(credential);
        logger.info("Successfully connected and built Sheets service.");
    }

    public void connectWithStoredCredentials() throws IOException {
        logger.info("Connecting to Google Sheets using stored credentials.");
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
        this.sheetsService = new Sheets.Builder(
                googleAuthManager.getHttpTransport(),
                GoogleAuthManager.getJsonFactory(),
                credential)
                .setApplicationName("Matchmaker")
                .build();
    }

    public void appendGroupsToSheet(String spreadsheetId, List<Group> groups) throws IOException {
        if (this.sheetsService == null) {
            logger.error("Cannot append groups: Sheets service is not initialised. Not connected to Google Sheets.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        String recapSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.RECAP_SHEET_NAME).getSettingValue();
        logger.info("Attempting to append {} groups to spreadsheet '{}', sheet '{}'.", groups.size(), spreadsheetId, recapSheetName);

        this.ensureSheetAndHeaderExist(spreadsheetId, recapSheetName, SheetDataMapper.GROUPS_HEADER);

        List<List<Object>> valuesToAppend = sheetDataMapper.mapGroupsToSheet(groups);
        if (valuesToAppend.isEmpty()) {
            logger.info("No groups to append. Aborting.");
            return;
        }

        ValueRange body = new ValueRange().setValues(valuesToAppend);
        AppendValuesResponse appendResponse = this.sheetsService.spreadsheets().values()
                .append(spreadsheetId, recapSheetName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .setIncludeValuesInResponse(true)
                .execute();

        logger.info("Successfully appended {} rows of data. Updated range: {}", valuesToAppend.size(), appendResponse.getUpdates().getUpdatedRange());
        this.reformatGroupSheet(spreadsheetId, recapSheetName, appendResponse);
    }

    private void reformatGroupSheet(String spreadsheetId, String sheetName, AppendValuesResponse appendResponse) throws IOException {
        Integer sheetId = getSheetId(spreadsheetId, sheetName);
        if (sheetId == null) {
            logger.warn("Could not find sheet ID for sheet name '{}'. Aborting reformatting.", sheetName);
            return;
        }
        logger.info("Attempting to reformat group sheet '{}' (ID: {}) after appending.", sheetName, sheetId);

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
            this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
            logger.info("Successfully applied all formatting requests.");
        }
    }

    private void ensureSheetExists(String spreadsheetId, String sheetName) throws IOException {
        logger.info("Ensuring sheet '{}' exists in spreadsheet '{}'.", sheetName, spreadsheetId);
        Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(spreadsheetId).execute();
        boolean sheetExists = spreadsheet.getSheets().stream()
                .anyMatch(s -> s.getProperties().getTitle().equals(sheetName));

        if (!sheetExists) {
            logger.info("Sheet '{}' does not exist. Creating it now.", sheetName);
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName));

            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

            this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
            logger.info("Sheet '{}' created successfully.", sheetName);
        } else {
            logger.debug("Sheet '{}' already exists.", sheetName);
        }
    }

    private void ensureSheetAndHeaderExist(String spreadsheetId, String sheetName, List<Object> header) throws IOException {
        logger.info("Ensuring sheet '{}' exists and has the correct header.", sheetName);
        this.ensureSheetExists(spreadsheetId, sheetName);

        ValueRange response = this.sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            logger.info("Sheet '{}' is empty. Writing header row.", sheetName);
            ValueRange headerBody = new ValueRange().setValues(Collections.singletonList(header));
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
     * A record to hold the imported player data.
     */
    public record PlayerData(int row, House house, String playerUuid, String charUuid, String player, String character) {}

    /**
     * A new, clean record to hold the *results* of the matching process for export.
     * This ensures our service layer is totally decoupled from the UI!
     */
    public record ExportData(
            int row,
            House house,
            String finalPlayerName,
            String finalPlayerUuid,
            String finalCharName,
            String finalCharUuid
    ) {}

    /**
     * Imports Player and Character data from a specific tab using fuzzy header matching.
     */
    public List<PlayerData> importPlayerData(String spreadsheetId, String tabName, House house) throws IOException {
        if (this.sheetsService == null) {
            logger.error("Cannot import data: Sheets service is not initialised.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        logger.info("Starting player data import from spreadsheet '{}', tab '{}' (House: {})", spreadsheetId, tabName, house);

        Map<String, Integer> colIndices = findHeaderIndices(spreadsheetId, tabName);

        if (!colIndices.containsKey("playerCol")) {
            logger.warn("Could not find 'Player' column in tab '{}'. Skipping.", tabName);
            return Collections.emptyList();
        }
        if (!colIndices.containsKey("charCol")) {
            logger.info("No 'Character Name' column found in tab '{}'. Will proceed without character data.", tabName);
        }

        String dataRange = tabName + "!A2:Z";
        ValueRange dataResponse = this.sheetsService.spreadsheets().values().get(spreadsheetId, dataRange).execute();
        List<List<Object>> allData = dataResponse.getValues();

        if (allData == null || allData.isEmpty()) {
            logger.info("No data rows found in tab '{}' (after header).", tabName);
            return Collections.emptyList();
        }

        List<PlayerData> importedData = new ArrayList<>();
        for (int i = 0; i < allData.size(); i++) {
            List<Object> row = allData.get(i);
            int rowNum = i + 2;

            String playerUuid = getStringFromRow(row, colIndices.getOrDefault("playerUuidCol", -1));
            String charUuid = getStringFromRow(row, colIndices.getOrDefault("charUuidCol", -1));
            String player = getStringFromRow(row, colIndices.get("playerCol"));
            String character = getStringFromRow(row, colIndices.getOrDefault("charCol", -1));

            if (!player.isEmpty()) {
                importedData.add(new PlayerData(rowNum, house, playerUuid, charUuid, player, character));
            } else {
                logger.trace("Skipping row {} in tab '{}' due to missing data (Player: '{}')",
                        rowNum, tabName, player);
            }
        }

        logger.info("Successfully imported {} player/character pairs from tab '{}'.", importedData.size(), tabName);
        return importedData;
    }

    /**
     * Finds all columns and returns a map of their indices.
     */
    private Map<String, Integer> findHeaderIndices(String spreadsheetId, String tabName) throws IOException {
        String headerRange = tabName + "!1:1";
        ValueRange headerResponse = this.sheetsService.spreadsheets().values().get(spreadsheetId, headerRange).execute();
        List<Object> headerRow = (headerResponse.getValues() != null && !headerResponse.getValues().isEmpty())
                ? headerResponse.getValues().get(0) : null;

        if (headerRow == null || headerRow.isEmpty()) {
            logger.warn("Could not read header row from tab '{}'.", tabName);
            return Collections.emptyMap();
        }

        Map<String, Integer> colIndices = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            if (headerRow.get(i) == null) continue;
            String header = headerRow.get(i).toString().trim();

            if (FuzzyStringMatcher.areStringsSimilar(header, "Player", HEADER_THRESHOLD)) {
                colIndices.put("playerCol", i);
                logger.info("Found 'Player' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character Name", HEADER_THRESHOLD)) {
                colIndices.put("charCol", i);
                logger.info("Found 'Character Name' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Player UUID", HEADER_THRESHOLD)) {
                colIndices.put("playerUuidCol", i);
                logger.info("Found 'Player UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
            if (FuzzyStringMatcher.areStringsSimilar(header, "Character UUID", HEADER_THRESHOLD)) {
                colIndices.put("charUuidCol", i);
                logger.info("Found 'Character UUID' column in tab '{}' at index {} (Header: '{}')", tabName, i, header);
            }
        }
        return colIndices;
    }

    /**
     * Exports the matched data back to the sheet.
     * This now uses our clean ExportData record!
     */
    public void exportMatchedData(String spreadsheetId, List<ExportData> tasks, SettingsStore settingsStore) throws IOException {
        if (this.sheetsService == null) {
            logger.error("Cannot export data: Sheets service is not initialised.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        Map<House, List<ExportData>> tasksByHouse = tasks.stream()
                .collect(Collectors.groupingBy(ExportData::house));

        List<Request> allRequests = new ArrayList<>();

        Map<House, String> houseToTabName = Map.of(
                House.GARNET, (String) settingsStore.getSetting(Settings.PersistenceSettings.GARNET_SHEET_NAME).getSettingValue(),
                House.AMBER, (String) settingsStore.getSetting(Settings.PersistenceSettings.AMBER_SHEET_NAME).getSettingValue(),
                House.AVENTURINE, (String) settingsStore.getSetting(Settings.PersistenceSettings.AVENTURINE_SHEET_NAME).getSettingValue(),
                House.OPAL, (String) settingsStore.getSetting(Settings.PersistenceSettings.OPAL_SHEET_NAME).getSettingValue()
        );

        for (Map.Entry<House, List<ExportData>> entry : tasksByHouse.entrySet()) {
            String tabName = houseToTabName.get(entry.getKey());
            if (tabName == null || tabName.isEmpty()) {
                logger.warn("No tab name found for House {}, skipping export for {} items.", entry.getKey(), entry.getValue().size());
                continue;
            }

            logger.info("Preparing export requests for tab '{}' ({} items)", tabName, entry.getValue().size());

            Map<String, Integer> colIndices = findHeaderIndices(spreadsheetId, tabName);
            int sheetId = getSheetId(spreadsheetId, tabName);

            int playerCol = colIndices.getOrDefault("playerCol", -1);
            int playerUuidCol = colIndices.getOrDefault("playerUuidCol", -1);
            int charCol = colIndices.getOrDefault("charCol", -1);
            int charUuidCol = colIndices.getOrDefault("charUuidCol", -1);

            for (ExportData task : entry.getValue()) {
                int row = task.row() - 1; // API is 0-indexed!

                // 1. Update Player Name
                if (playerCol != -1 && task.finalPlayerName() != null) {
                    allRequests.add(createCellUpdateRequest(sheetId, row, playerCol, task.finalPlayerName()));
                }

                // 2. Update Player UUID
                if (playerUuidCol != -1 && task.finalPlayerUuid() != null) {
                    allRequests.add(createCellUpdateRequest(sheetId, row, playerUuidCol, task.finalPlayerUuid()));
                }

                // 3. Update Character Name
                if (charCol != -1 && task.finalCharName() != null) {
                    allRequests.add(createCellUpdateRequest(sheetId, row, charCol, task.finalCharName()));
                }

                // 4. Update Character UUID
                if (charUuidCol != -1 && task.finalCharUuid() != null) {
                    allRequests.add(createCellUpdateRequest(sheetId, row, charUuidCol, task.finalCharUuid()));
                }
            }
        }

        if (allRequests.isEmpty()) {
            logger.info("No cell updates to export.");
            return;
        }

        logger.info("Executing batch update with {} total cell update requests.", allRequests.size());
        BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest().setRequests(allRequests);
        this.sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
        logger.info("Successfully exported all matched data back to Google Sheets!");
    }

    /**
     * Creates the Request object for a single cell update.
     */
    private Request createCellUpdateRequest(int sheetId, int row, int col, String value) {
        return new Request().setUpdateCells(new UpdateCellsRequest()
                .setRows(Collections.singletonList(new RowData()
                        .setValues(Collections.singletonList(new CellData()
                                .setUserEnteredValue(new ExtendedValue().setStringValue(value))))))
                .setFields("userEnteredValue")
                .setStart(new GridCoordinate().setSheetId(sheetId).setRowIndex(row).setColumnIndex(col))
        );
    }

    /**
     * Gets the numeric Sheet ID for a given tab name.
     */
    private Integer getSheetId(String spreadsheetId, String sheetName) throws IOException {
        String cacheKey = spreadsheetId + "::" + sheetName;
        if (sheetIdCache.containsKey(cacheKey)) {
            return sheetIdCache.get(cacheKey);
        }

        Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute();
        Integer sheetId = spreadsheet.getSheets().stream()
                .filter(s -> s.getProperties().getTitle().equals(sheetName))
                .map(s -> s.getProperties().getSheetId())
                .findFirst()
                .orElse(null);

        if (sheetId != null) {
            sheetIdCache.put(cacheKey, sheetId);
        }
        return sheetId;
    }


    /**
     * Safely get a string from a row list.
     */
    private String getStringFromRow(List<Object> row, int index) {
        if (index < 0 || row == null || row.size() <= index || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    public void disconnectSheetService() {
        this.sheetsService = null;
        this.sheetIdCache.clear();
    }
}
