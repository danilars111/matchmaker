package org.poolen.frontend.gui.components.tabs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.web.google.SheetsServiceManager;

/**
 * A tab dedicated to handling data persistence via Google Sheets.
 */
public class PersistenceTab extends Tab {

    private final TextField spreadsheetIdField;
    private final Button connectButton;
    private final Button saveButton;
    private final Button loadButton;
    private final Button logoutButton;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;
    private final Runnable onDataChanged;
    private Runnable onLogoutRequestHandler;


    public PersistenceTab(Runnable onDataChanged) {
        super("Persistence");
        this.onDataChanged = onDataChanged;

        // --- UI Components ---
        spreadsheetIdField = new TextField();
        spreadsheetIdField.setPromptText("Enter Google Sheet ID here...");

        connectButton = new Button("Connect");
        saveButton = new Button("Save Data");
        loadButton = new Button("Load Data");
        logoutButton = new Button("Logout");

        statusLabel = new Label("Please connect to Google.");
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);

        // --- Layout ---
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        Label idLabel = new Label("Spreadsheet ID:");
        grid.add(idLabel, 0, 0);
        grid.add(spreadsheetIdField, 1, 0);
        GridPane.setHgrow(spreadsheetIdField, Priority.ALWAYS);

        HBox buttonBox = new HBox(10, connectButton, saveButton, loadButton, logoutButton);
        buttonBox.setAlignment(Pos.CENTER);

        HBox statusBox = new HBox(10, statusLabel, progressIndicator);
        statusBox.setAlignment(Pos.CENTER);

        root.getChildren().addAll(grid, buttonBox, statusBox);
        this.setContent(root);

        // --- Initial State ---
        saveButton.setDisable(true);
        loadButton.setDisable(true);

        // --- Styling ---
        connectButton.setStyle("-fx-background-color: #4285F4; -fx-text-fill: white;");
        saveButton.setStyle("-fx-background-color: #34A853; -fx-text-fill: white;");
        loadButton.setStyle("-fx-background-color: #FBBC05; -fx-text-fill: black;");
        logoutButton.setStyle("-fx-background-color: #EA4335; -fx-text-fill: white;");

        // --- Event Wiring ---
        connectButton.setOnAction(e -> handleConnect());
        saveButton.setOnAction(e -> handleSave());
        loadButton.setOnAction(e -> handleLoad());
        logoutButton.setOnAction(e -> {
            if (onLogoutRequestHandler != null) {
                onLogoutRequestHandler.run();
            }
        });
    }

    private void handleConnect() {
        runTask(() -> {
            SheetsServiceManager.connect();
            return "Successfully connected to Google!";
        }, "Connecting...");
    }

    private void handleSave() {
        String spreadsheetId = spreadsheetIdField.getText();
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            new ErrorDialog("Please enter a valid Spreadsheet ID.", this.getTabPane()).showAndWait();
            return;
        }
        runTask(() -> {
            SheetsServiceManager.saveData(spreadsheetId);
            return "Data successfully saved to Google Sheet!";
        }, "Saving data...");
    }

    private void handleLoad() {
        String spreadsheetId = spreadsheetIdField.getText();
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            new ErrorDialog("Please enter a valid Spreadsheet ID.", this.getTabPane()).showAndWait();
            return;
        }
        runTask(() -> {
            SheetsServiceManager.loadData(spreadsheetId);
            return "Data successfully loaded from Google Sheet!";
        }, "Loading data...");
    }

    /**
     * A helper method to run a background task and update the UI accordingly.
     * @param operation The operation to run (should return a success message).
     * @param statusMessage The message to display while the task is running.
     */
    private void runTask(TaskOperation operation, String statusMessage) {
        progressIndicator.setVisible(true);
        statusLabel.setText(statusMessage);
        setButtonsDisabled(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return operation.execute();
            }

            @Override
            protected void succeeded() {
                progressIndicator.setVisible(false);
                statusLabel.setText("Status: Ready");
                setButtonsDisabled(false);
                connectButton.setDisable(true);
                new InfoDialog(getValue(), PersistenceTab.this.getTabPane()).showAndWait();
                // Notify the rest of the application that data has changed!
                onDataChanged.run();
            }

            @Override
            protected void failed() {
                progressIndicator.setVisible(false);
                statusLabel.setText("Status: Error");
                setButtonsDisabled(false);
                Throwable error = getException();
                new ErrorDialog("Operation failed: " + error.getMessage(), PersistenceTab.this.getTabPane()).showAndWait();
                error.printStackTrace();
            }
        };

        new Thread(task).start();
    }

    private void setButtonsDisabled(boolean disabled) {
        connectButton.setDisable(disabled);
        saveButton.setDisable(disabled);
        loadButton.setDisable(disabled);
        logoutButton.setDisable(disabled);
    }

    public void setOnLogoutRequestHandler(Runnable handler) {
        this.onLogoutRequestHandler = handler;
    }


    // A functional interface for our background tasks.
    @FunctionalInterface
    private interface TaskOperation {
        String execute() throws Exception;
    }
}

