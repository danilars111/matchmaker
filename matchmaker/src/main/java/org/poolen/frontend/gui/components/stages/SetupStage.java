package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.util.services.ApplicationScriptService;
import org.poolen.util.LoggingManager;
import org.poolen.util.PropertiesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SetupStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(SetupStage.class);

    private final ApplicationScriptService scriptService;
    private final Map<LoggingManager.LogbackLogger, ComboBox<LoggingManager.LogLevel>> loggerComboBoxMap = new HashMap<>();

    // Fields for database tab
    private TextField dbAddressField;
    private TextField userField;
    private PasswordField passField;

    public SetupStage(ApplicationScriptService scriptService, String errorMessage) {
        this.scriptService = scriptService;

        logger.info("Initialising SetupStage.");
        if (errorMessage != null && !errorMessage.isEmpty()) {
            logger.warn("SetupStage opened with an error message: {}", errorMessage);
        }

        initModality(Modality.APPLICATION_MODAL);
        setTitle("First-Time Setup");
        setResizable(false); // Can't resize our pretty window!

        TabPane tabPane = new TabPane();
        Tab databaseTab = new Tab("Database", createDatabasePane(errorMessage));
        Tab loggingTab = new Tab("Advanced", createLoggingPane());

        // We don't want the user to close our pretty tabs!
        databaseTab.setClosable(false);
        loggingTab.setClosable(false);

        tabPane.getTabs().addAll(databaseTab, loggingTab);

        Button saveButton = new Button("Save and Restart");
        saveButton.setOnAction(e -> handleSave());

        Button h2Button = new Button("H2 Test Mode");
        h2Button.setOnAction(e -> handleH2());

        HBox buttonBox = new HBox(10, h2Button, saveButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(15, tabPane, buttonBox);
        VBox.setVgrow(tabPane, Priority.ALWAYS); // This makes the tab pane grow to fill vertical space
        mainLayout.setPadding(new Insets(0,0,20,0));
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setStyle("-fx-background-color: #F4F4F4;"); // A soft background colour

        setScene(new Scene(mainLayout, 300 , 300));
        logger.debug("SetupStage UI constructed and scene set.");
    }

    private Node createDatabasePane(String errorMessage) {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            Label errorLabel = new Label(errorMessage);
            errorLabel.setStyle("-fx-text-fill: #D8000C; -fx-font-weight: bold; -fx-background-color: #FFD2D2; -fx-padding: 10; -fx-border-radius: 5; -fx-background-radius: 5;");
            errorLabel.setWrapText(true);
            layout.getChildren().add(errorLabel);
        }

        Label headerLabel = new Label("Database Configuration");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER);

        dbAddressField = new TextField();
        dbAddressField.setPromptText("e.g., your-db-host.com:12345/dbname");
        userField = new TextField();
        userField.setPromptText("Database Username");
        passField = new PasswordField();
        passField.setPromptText("Database Password");

        grid.add(new Label("DB Address:"), 0, 0);
        grid.add(dbAddressField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(userField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passField, 1, 2);

        layout.getChildren().addAll(headerLabel, grid);
        return layout;
    }

    private Node createLoggingPane() {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Label headerLabel = new Label("Logging Levels");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER);

        int rowIndex = 0;
        for (LoggingManager.LogbackLogger loggerEnum : LoggingManager.LogbackLogger.values()) {
            // A helper to make the enum name look much prettier for our UI
            String loggerDisplayName = capitalize(loggerEnum.name());
            Label loggerLabel = new Label(loggerDisplayName + " Level:");

            ComboBox<LoggingManager.LogLevel> levelComboBox = new ComboBox<>();
            levelComboBox.getItems().setAll(LoggingManager.LogLevel.values());
            levelComboBox.setMinWidth(120);

            // Here's the clever bit! We ask the LoggingManager what the current setting is.
            LoggingManager.getLoggerLevel(loggerEnum)
                    .ifPresentOrElse(
                            levelComboBox::setValue,
                            // If it's not set, we'll just default to INFO, which is very sensible.
                            () -> levelComboBox.setValue(LoggingManager.LogLevel.INFO)
                    );

            grid.add(loggerLabel, 0, rowIndex);
            grid.add(levelComboBox, 1, rowIndex);
            loggerComboBoxMap.put(loggerEnum, levelComboBox);
            rowIndex++;
        }

        layout.getChildren().addAll(headerLabel, grid);
        return layout;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private void handleSave() {
        logger.info("User clicked 'Save and Restart'.");
        String dbAddress = dbAddressField.getText();
        String username = userField.getText();
        String password = passField.getText();

        if (dbAddress.isEmpty() || username.isEmpty()) {
            logger.warn("Save validation failed: DB Address or Username is empty.");
            new ErrorDialog("Address and Username cannot be empty.", getScene().getRoot()).showAndWait();
            return;
        }
        logger.info("Saving new database credentials to properties file.");
        PropertiesManager.saveDatabaseCredentials(dbAddress, username, password);

        // Now for our new trick! We save all the logging levels.
        logger.info("Saving updated logging levels.");
        loggerComboBoxMap.forEach((logbackLogger, comboBox) -> {
            LoggingManager.LogLevel selectedLevel = comboBox.getValue();
            if (selectedLevel != null) {
                logger.debug("Setting logger '{}' to level '{}'", logbackLogger.name(), selectedLevel);
                LoggingManager.setLoggerLevel(logbackLogger, selectedLevel);
            }
        });

        restartApplication();
    }

    private void handleH2() {
        logger.info("User selected 'H2 Test Mode'. Application will restart with H2 flag.");
        // We don't need to save anything for H2, just restart with the flag.
        restartApplicationWithH2();
    }

    private void restartApplication() {
        logger.info("Attempting to restart the application.");
        scriptService.restart(
                () -> {
                    logger.warn("Application restart cannot be performed from an IDE environment.");
                    new ErrorDialog("Cannot restart from IDE.", getScene().getRoot()).showAndWait();
                },
                (err) -> {
                    logger.error("Application restart failed.", err);
                    new ErrorDialog("Restart failed: " + err.getMessage(), getScene().getRoot()).showAndWait();
                }
        );
    }

    private void restartApplicationWithH2() {
        logger.info("Attempting to restart the application in H2 mode.");
        scriptService.restartWithH2(
                () -> {
                    logger.warn("Application restart (with H2) cannot be performed from an IDE environment.");
                    new ErrorDialog("Cannot restart from IDE.", getScene().getRoot()).showAndWait();
                },
                (err) -> {
                    logger.error("Application restart (with H2) failed.", err);
                    new ErrorDialog("Restart failed: " + err.getMessage(), getScene().getRoot()).showAndWait();
                }
        );
    }
}
