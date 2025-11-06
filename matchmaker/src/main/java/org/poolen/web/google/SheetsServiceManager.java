package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.db.store.Store;
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
 */
@Service
public class SheetsServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(SheetsServiceManager.class);
    private static Sheets sheetsService;
    private final SettingsStore settingsStore;

    private final SheetDataMapper sheetDataMapper;

    public SheetsServiceManager(SheetDataMapper sheetDataMapper, Store store) {
        this.sheetDataMapper = sheetDataMapper;
        this.settingsStore = store.getSettingsStore();
        logger.info("SheetsServiceManager initialised.");
    }

    /**
     * Connects to Google Sheets by initiating a full user authentication flow.
     * @param urlDisplayer A callback to provide the authentication URL to the UI.
     * @throws IOException if credentials cannot be read or stored.
     */
    public void connect(Consumer<String> urlDisplayer) throws IOException {
        logger.info("Initiating new Google Sheets connection with user auth flow.");
        Credential credential = GoogleAuthManager.getCredentials(urlDisplayer);
        buildSheetsService(credential);
        logger.info("Successfully connected and built Sheets service.");
    }

    /**
     * Connects to Google Sheets using already stored credentials.
     * @throws IOException if stored credentials cannot be loaded.
     */
    public void connectWithStoredCredentials() throws IOException {
        logger.info("Connecting to Google Sheets using stored credentials.");
        Credential credential = GoogleAuthManager.loadStoredCredential();
        buildSheetsService(credential);
        logger.info("Successfully connected and built Sheets service with stored credentials.");
    }

    /**
     * A private helper to build the Sheets service once we have credentials.
     * @param credential The authenticated user credential.
     */
    private void buildSheetsService(Credential credential) {
        logger.info("Building Google Sheets service.");
        sheetsService = new Sheets.Builder(
                GoogleAuthManager.getHttpTransport(),
                GoogleAuthManager.getJsonFactory(),
                credential)
                .setApplicationName("Matchmaker")
                .build();
    }

    /**
     * Appends the provided groups to the recap sheet and adds formatting.
     */
    public void appendGroupsToSheet(String spreadsheetId, List<Group> groups) throws IOException {
        if (sheetsService == null) {
            logger.error("Cannot append groups: Sheets service is not initialised. Not connected to Google Sheets.");
            throw new IllegalStateException("Not connected to Google Sheets.");
        }

        String recapSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.RECAP_SHEET_NAME).getSettingValue();
        logger.info("Attempting to append {} groups to spreadsheet '{}', sheet '{}'.", groups.size(), spreadsheetId, recapSheetName);

        ensureSheetAndHeaderExist(spreadsheetId, recapSheetName, SheetDataMapper.GROUPS_HEADER);

        List<List<Object>> valuesToAppend = sheetDataMapper.mapGroupsToSheet(groups);
        if (valuesToAppend.isEmpty()) {
            logger.info("No groups to append. Aborting.");
            return;
        }

        ValueRange body = new ValueRange().setValues(valuesToAppend);
        AppendValuesResponse appendResponse = sheetsService.spreadsheets().values()
                .append(spreadsheetId, recapSheetName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .setIncludeValuesInResponse(true)
                .execute();

        logger.info("Successfully appended {} rows of data. Updated range: {}", valuesToAppend.size(), appendResponse.getUpdates().getUpdatedRange());
        reformatGroupSheet(spreadsheetId, recapSheetName, appendResponse);
    }

    private static void reformatGroupSheet(String spreadsheetId, String sheetName, AppendValuesResponse appendResponse) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute();
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

        ValueRange fullSheetData = sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
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
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
            logger.info("Successfully applied all formatting requests.");
        }
    }


    private static void ensureSheetExists(String spreadsheetId, String sheetName) throws IOException {
        logger.info("Ensuring sheet '{}' exists in spreadsheet '{}'.", sheetName, spreadsheetId);
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        boolean sheetExists = spreadsheet.getSheets().stream()
                .anyMatch(s -> s.getProperties().getTitle().equals(sheetName));

        if (!sheetExists) {
            logger.info("Sheet '{}' does not exist. Creating it now.", sheetName);
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName));

            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
            logger.info("Sheet '{}' created successfully.", sheetName);
        } else {
            logger.debug("Sheet '{}' already exists.", sheetName);
        }
    }

    private static void ensureSheetAndHeaderExist(String spreadsheetId, String sheetName, List<Object> header) throws IOException {
        logger.info("Ensuring sheet '{}' exists and has the correct header.", sheetName);
        ensureSheetExists(spreadsheetId, sheetName);

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            logger.info("Sheet '{}' is empty. Writing header row.", sheetName);
            ValueRange headerBody = new ValueRange().setValues(Collections.singletonList(header));
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, sheetName, headerBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
            logger.info("Header row written successfully to '{}'.", sheetName);
        } else {
            logger.debug("Sheet '{}' already has content. Assuming header exists.", sheetName);
        }
    }
}
