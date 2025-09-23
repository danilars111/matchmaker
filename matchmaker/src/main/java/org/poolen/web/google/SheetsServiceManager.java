package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
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

        // We only need to fetch data from our single PlayerData sheet now.
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

        // Ensure our required sheet exists before we try to write to it.
        ensureSheetExists(spreadsheetId);

        Map<String, List<List<Object>>> allSheetData = SheetDataMapper.mapDataToSheets();

        // Since we only have one sheet, we can simplify this.
        List<List<Object>> values = allSheetData.get(SheetDataMapper.PLAYERS_SHEET_NAME);
        if (values == null) return; // Nothing to save.

        // Before writing, we should clear the old data to prevent stale rows.
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
     * Checks if the "PlayerData" sheet exists in the given spreadsheet,
     * and creates it if it doesn't.
     *
     * @param spreadsheetId The ID of the spreadsheet to check.
     * @throws IOException If there's a problem communicating with the API.
     */
    private static void ensureSheetExists(String spreadsheetId) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheets = spreadsheet.getSheets();

        boolean sheetExists = sheets.stream()
                .anyMatch(s -> s.getProperties().getTitle().equals(SheetDataMapper.PLAYERS_SHEET_NAME));

        if (!sheetExists) {
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(SheetDataMapper.PLAYERS_SHEET_NAME));

            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
        }
    }
}

