package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        // We need to fetch data from both sheets.
        List<String> ranges = List.of(SheetDataMapper.PLAYERS_SHEET_NAME, SheetDataMapper.CHARACTERS_SHEET_NAME);
        BatchGetValuesResponse response = sheetsService.spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .execute();

        Map<String, List<List<Object>>> allSheetData = new java.util.HashMap<>();
        for (ValueRange valueRange : response.getValueRanges()) {
            // The range string is returned in the format "'SheetName'!A1:Z1000", so we need to parse out the sheet name.
            String sheetName = valueRange.getRange().split("!")[0].replace("'", "");
            allSheetData.put(sheetName, valueRange.getValues());
        }

        SheetDataMapper.mapSheetsToData(allSheetData);
    }

    /**
     * Saves all data from the application's stores to the specified Google Sheet.
     * It will create the "Players" and "Characters" sheets if they do not exist.
     *
     * @param spreadsheetId The unique ID of the Google Sheet to save to.
     * @throws IOException If there's a problem communicating with Google's servers.
     */
    public static void saveData(String spreadsheetId) throws IOException {
        if (sheetsService == null) {
            throw new IllegalStateException("Connection to Google Sheets has not been established. Call connect() first.");
        }

        // Ensure our required sheets exist before we try to write to them.
        ensureSheetsExist(spreadsheetId);

        Map<String, List<List<Object>>> allSheetData = SheetDataMapper.mapDataToSheets();

        List<ValueRange> data = new ArrayList<>();
        for (Map.Entry<String, List<List<Object>>> entry : allSheetData.entrySet()) {
            ValueRange valueRange = new ValueRange();
            valueRange.setRange(entry.getKey());
            valueRange.setValues(entry.getValue());
            data.add(valueRange);
        }

        BatchUpdateValuesRequest body = new BatchUpdateValuesRequest();
        body.setValueInputOption("USER_ENTERED");
        body.setData(data);

        sheetsService.spreadsheets().values().batchUpdate(spreadsheetId, body).execute();
    }

    /**
     * Checks if the "Players" and "Characters" sheets exist in the given spreadsheet,
     * and creates them if they don't.
     *
     * @param spreadsheetId The ID of the spreadsheet to check.
     * @throws IOException If there's a problem communicating with the API.
     */
    private static void ensureSheetsExist(String spreadsheetId) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheets = spreadsheet.getSheets();

        Optional<Sheet> playersSheet = sheets.stream().filter(s -> s.getProperties().getTitle().equals(SheetDataMapper.PLAYERS_SHEET_NAME)).findFirst();
        Optional<Sheet> charactersSheet = sheets.stream().filter(s -> s.getProperties().getTitle().equals(SheetDataMapper.CHARACTERS_SHEET_NAME)).findFirst();

        List<Request> requests = new ArrayList<>();
        if (playersSheet.isEmpty()) {
            requests.add(new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(SheetDataMapper.PLAYERS_SHEET_NAME))));
        }
        if (charactersSheet.isEmpty()) {
            requests.add(new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(SheetDataMapper.CHARACTERS_SHEET_NAME))));
        }

        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
        }
    }
}

