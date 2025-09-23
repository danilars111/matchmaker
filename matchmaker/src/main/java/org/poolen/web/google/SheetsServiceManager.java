package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.entities.Group;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages all interactions with the Google Sheets API, including authentication,
 * reading data, and writing data. This is the main entry point for the rest of the
 * application to interact with Google Sheets.
 */
public class SheetsServiceManager {

    private static Sheets sheetsService;

    /**
     * Establishes a connection to the Google Sheets API. This must be called
     * successfully before any other methods can be used.
     *
     * @throws GeneralSecurityException If there's an issue with the security credentials.
     * @throws IOException If there's a problem communicating with Google's servers.
     */
    public static void connect() throws GeneralSecurityException, IOException {
        Credential credential = GoogleAuthManager.getCredentials();
        sheetsService = new Sheets.Builder(
                GoogleAuthManager.getHttpTransport(),
                GoogleAuthManager.getJsonFactory(),
                credential)
                .setApplicationName("Matchmaker")
                .build();
    }

    /**
     * Loads all data from the specified Google Sheet and populates the application's stores.
     *
     * @param spreadsheetId The unique ID of the Google Sheet to load from.
     * @throws IOException If there's a problem communicating with Google's servers.
     */
    public static void loadData(String spreadsheetId) throws IOException {
        if (sheetsService == null) {
            throw new IllegalStateException("Connection to Google Sheets has not been established. Call connect() first.");
        }

        String range = SheetDataMapper.PLAYERS_SHEET_NAME;
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        Map<String, List<List<Object>>> allSheetData = new java.util.HashMap<>();
        allSheetData.put(SheetDataMapper.PLAYERS_SHEET_NAME, response.getValues());

        SheetDataMapper.mapSheetsToData(allSheetData);
    }

    /**
     * Saves all data from the application's stores to the specified Google Sheet.
     * It will create the "PlayerData" sheet if it does not exist.
     *
     * @param spreadsheetId The unique ID of the Google Sheet to save to.
     * @throws IOException If there's a problem communicating with Google's servers.
     */
    public static void saveData(String spreadsheetId) throws IOException {
        if (sheetsService == null) {
            throw new IllegalStateException("Connection to Google Sheets has not been established. Call connect() first.");
        }

        ensureSheetExists(spreadsheetId, SheetDataMapper.PLAYERS_SHEET_NAME);

        Map<String, List<List<Object>>> allSheetData = SheetDataMapper.mapDataToSheets();
        List<List<Object>> values = allSheetData.get(SheetDataMapper.PLAYERS_SHEET_NAME);
        if (values == null) return;

        ClearValuesRequest clearRequest = new ClearValuesRequest();
        sheetsService.spreadsheets().values()
                .clear(spreadsheetId, SheetDataMapper.PLAYERS_SHEET_NAME, clearRequest)
                .execute();

        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, SheetDataMapper.PLAYERS_SHEET_NAME, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    /**
     * Appends the provided groups to the "First Semester" sheet and adds formatting.
     *
     * @param spreadsheetId The ID of the spreadsheet.
     * @param groups The list of groups to append.
     * @throws IOException If there's an issue communicating with the API.
     */
    public static void appendGroupsToSheet(String spreadsheetId, List<Group> groups) throws IOException {
        if (sheetsService == null) {
            throw new IllegalStateException("Connection to Google Sheets has not been established. Call connect() first.");
        }

        String sheetName = SheetDataMapper.GROUPS_SHEET_NAME;
        ensureSheetAndHeaderExist(spreadsheetId, sheetName, SheetDataMapper.GROUPS_HEADER);

        List<List<Object>> valuesToAppend = SheetDataMapper.mapGroupsToSheet(groups);
        if (valuesToAppend.isEmpty()) return;

        ValueRange body = new ValueRange().setValues(valuesToAppend);
        AppendValuesResponse appendResponse = sheetsService.spreadsheets().values()
                .append(spreadsheetId, sheetName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .setIncludeValuesInResponse(true)
                .execute();

        // --- Now, re-format the entire sheet ---
        reformatGroupSheet(spreadsheetId, appendResponse);
    }

    /**
     * Applies all standard formatting (borders, zebra stripes, deadline highlighting) to the entire group sheet.
     *
     * @param spreadsheetId The ID of the spreadsheet.
     * @param appendResponse The response from the append operation, used to find the newly added rows.
     * @throws IOException If there's an issue communicating with the API.
     */
    private static void reformatGroupSheet(String spreadsheetId, AppendValuesResponse appendResponse) throws IOException {
        String sheetName = SheetDataMapper.GROUPS_SHEET_NAME;

        // 1. Get full sheet properties
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute();
        Integer sheetId = spreadsheet.getSheets().stream()
                .filter(s -> s.getProperties().getTitle().equals(sheetName))
                .map(s -> s.getProperties().getSheetId())
                .findFirst()
                .orElse(null);

        if (sheetId == null) return;

        // 2. Get all data to know dimensions and deadlines
        ValueRange fullSheetData = sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        List<List<Object>> allValues = fullSheetData.getValues();
        if (allValues == null || allValues.size() <= 1) return; // Nothing to format beyond the header
        int totalRows = allValues.size();
        int totalCols = SheetDataMapper.GROUPS_HEADER.size();

        List<Request> requests = new ArrayList<>();

        // --- Formatting Definitions ---
        Color lightGray = new Color().setRed(0.9f).setGreen(0.9f).setBlue(0.9f);
        Color lightRed = new Color().setRed(1f).setGreen(0.8f).setBlue(0.8f);
        Color darkerRed = new Color().setRed(0.95f).setGreen(0.75f).setBlue(0.75f); // Our new, darker red!
        Color black = new Color().setRed(0f).setGreen(0f).setBlue(0f);
        Color white = new Color().setRed(1f).setGreen(1f).setBlue(1f);

        CellFormat grayFormat = new CellFormat().setBackgroundColor(lightGray);
        CellFormat whiteFormat = new CellFormat().setBackgroundColor(white);
        CellFormat lightRedFormat = new CellFormat().setBackgroundColor(lightRed);
        CellFormat darkerRedFormat = new CellFormat().setBackgroundColor(darkerRed);
        Border solidBorder = new Border().setStyle("SOLID").setColor(black);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();

        // --- Build formatting requests for the entire sheet ---
        for (int i = 1; i < totalRows; i++) { // Start at 1 to skip header
            GridRange rowRange = new GridRange().setSheetId(sheetId).setStartRowIndex(i).setEndRowIndex(i + 1);

            // Default Zebra Striping
            CellFormat format = (i % 2 == 0) ? grayFormat : whiteFormat; // Even data rows (sheet rows 3, 5, ..) are gray

            // Check for deadline override
            List<Object> row = allValues.get(i);
            // Deadline is column 7 (index 6), Recap is column 3 (index 2)
            if (row.size() > 6) {
                try {
                    LocalDate deadline = LocalDate.parse(row.get(6).toString(), formatter);
                    boolean recapIsEmpty = row.size() <= 2 || row.get(2) == null || row.get(2).toString().trim().isEmpty();

                    if (deadline.isBefore(today) && recapIsEmpty) {
                        // Maintain the zebra stripe, but with shades of red!
                        format = (i % 2 == 0) ? darkerRedFormat : lightRedFormat;
                    }
                } catch (Exception e) { /* Ignore parse errors */ }
            }

            requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(rowRange)
                    .setCell(new CellData().setUserEnteredFormat(format))
                    .setFields("userEnteredFormat.backgroundColor")));
        }

        // --- Top border for the newly added block ---
        String updatedRange = appendResponse.getUpdates().getUpdatedRange();
        if (updatedRange != null) {
            Matcher matcher = Pattern.compile("(\\d+):").matcher(updatedRange.split("!")[1]);
            if (matcher.find()) {
                int startRowIndex = Integer.parseInt(matcher.group(1)) - 1;
                GridRange topBorderRange = new GridRange().setSheetId(sheetId).setStartRowIndex(startRowIndex).setEndRowIndex(startRowIndex + 1);
                requests.add(new Request().setUpdateBorders(new UpdateBordersRequest().setRange(topBorderRange).setTop(solidBorder)));
            }
        }

        // --- Right border for all cells ---
        GridRange allCellsRange = new GridRange().setSheetId(sheetId).setStartRowIndex(1).setEndRowIndex(totalRows).setStartColumnIndex(0).setEndColumnIndex(totalCols);
        requests.add(new Request().setRepeatCell(new RepeatCellRequest().setRange(allCellsRange).setCell(new CellData().setUserEnteredFormat(new CellFormat().setBorders(new Borders().setRight(solidBorder)))).setFields("userEnteredFormat.borders.right")));

        // --- Send all requests ---
        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest().setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
        }
    }


    private static void ensureSheetExists(String spreadsheetId, String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        boolean sheetExists = spreadsheet.getSheets().stream()
                .anyMatch(s -> s.getProperties().getTitle().equals(sheetName));

        if (!sheetExists) {
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName));

            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
        }
    }

    private static void ensureSheetAndHeaderExist(String spreadsheetId, String sheetName, List<Object> header) throws IOException {
        ensureSheetExists(spreadsheetId, sheetName);

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            ValueRange headerBody = new ValueRange().setValues(Collections.singletonList(header));
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, sheetName, headerBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
    }
}

