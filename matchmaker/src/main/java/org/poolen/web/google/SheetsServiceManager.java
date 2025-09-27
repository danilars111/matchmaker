package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.store.SettingsStore;
import org.springframework.stereotype.Service;

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
 * reading data, and writing data.
 */
@Service
public class SheetsServiceManager {

    private static Sheets sheetsService;
    private final SettingsStore settingsStore;

    private final SheetDataMapper sheetDataMapper;

    public SheetsServiceManager(SheetDataMapper sheetDataMapper, SettingsStore settingsStore) {
        this.sheetDataMapper = sheetDataMapper;
        this.settingsStore = settingsStore;
    }

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
     * Loads all data (players and settings) from the specified Google Sheet.
     */
    public  void loadData(String spreadsheetId) throws IOException {
        if (sheetsService == null) throw new IllegalStateException("Not connected to Google Sheets.");

        String playerDataSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.PLAYER_DATA_SHEET_NAME).getSettingValue();
        String settingsDataSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.SETTINGS_DATA_SHEET_NAME).getSettingValue();

        List<String> ranges = List.of(playerDataSheet, settingsDataSheet);
        BatchGetValuesResponse response = sheetsService.spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .execute();

        Map<String, List<List<Object>>> allSheetData = new java.util.HashMap<>();
        for (ValueRange valueRange : response.getValueRanges()) {
            String sheetName = valueRange.getRange().split("!")[0].replace("'", "");
            allSheetData.put(sheetName, valueRange.getValues());
        }

        sheetDataMapper.mapSheetsToData(allSheetData);
    }

    /**
     * Saves all data (players and settings) to the specified Google Sheet.
     */
    public void saveData(String spreadsheetId) throws IOException {
        if (sheetsService == null) throw new IllegalStateException("Not connected to Google Sheets.");

        String playerDataSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.PLAYER_DATA_SHEET_NAME).getSettingValue();
        String settingsDataSheet = (String) settingsStore.getSetting(Settings.PersistenceSettings.SETTINGS_DATA_SHEET_NAME).getSettingValue();

        ensureSheetExists(spreadsheetId, playerDataSheet);
        ensureSheetExists(spreadsheetId, settingsDataSheet);

        Map<String, List<List<Object>>> allSheetData = sheetDataMapper.mapDataToSheets();

        List<String> rangesToClear = new ArrayList<>();
        List<ValueRange> dataToWrite = new ArrayList<>();

        for (Map.Entry<String, List<List<Object>>> entry : allSheetData.entrySet()) {
            rangesToClear.add(entry.getKey());
            dataToWrite.add(new ValueRange().setRange(entry.getKey()).setValues(entry.getValue()));
        }

        BatchClearValuesRequest clearBody = new BatchClearValuesRequest().setRanges(rangesToClear);
        sheetsService.spreadsheets().values().batchClear(spreadsheetId, clearBody).execute();

        BatchUpdateValuesRequest updateBody = new BatchUpdateValuesRequest()
                .setData(dataToWrite)
                .setValueInputOption("USER_ENTERED");
        sheetsService.spreadsheets().values().batchUpdate(spreadsheetId, updateBody).execute();
    }

    /**
     * Appends the provided groups to the recap sheet and adds formatting.
     */
    public void appendGroupsToSheet(String spreadsheetId, List<Group> groups) throws IOException {
        if (sheetsService == null) throw new IllegalStateException("Not connected to Google Sheets.");

        String recapSheetName = (String) settingsStore.getSetting(Settings.PersistenceSettings.RECAP_SHEET_NAME).getSettingValue();

        ensureSheetAndHeaderExist(spreadsheetId, recapSheetName, SheetDataMapper.GROUPS_HEADER);

        List<List<Object>> valuesToAppend = sheetDataMapper.mapGroupsToSheet(groups);
        if (valuesToAppend.isEmpty()) return;

        ValueRange body = new ValueRange().setValues(valuesToAppend);
        AppendValuesResponse appendResponse = sheetsService.spreadsheets().values()
                .append(spreadsheetId, recapSheetName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .setIncludeValuesInResponse(true)
                .execute();

        reformatGroupSheet(spreadsheetId, recapSheetName, appendResponse);
    }

    private static void reformatGroupSheet(String spreadsheetId, String sheetName, AppendValuesResponse appendResponse) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).setFields("sheets.properties").execute();
        Integer sheetId = spreadsheet.getSheets().stream()
                .filter(s -> s.getProperties().getTitle().equals(sheetName))
                .map(s -> s.getProperties().getSheetId())
                .findFirst()
                .orElse(null);

        if (sheetId == null) return;

        ValueRange fullSheetData = sheetsService.spreadsheets().values().get(spreadsheetId, sheetName).execute();
        List<List<Object>> allValues = fullSheetData.getValues();
        if (allValues == null || allValues.size() <= 1) return;
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

                    if (deadline.isBefore(today) && recapIsEmpty) {
                        format = (i % 2 == 0) ? darkerRedFormat : lightRedFormat;
                    }
                } catch (Exception e) { /* Ignore */ }
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

