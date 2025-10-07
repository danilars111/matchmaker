package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.util.services.ApplicationScriptService;
import org.poolen.util.PropertiesManager;

public class SetupStage extends Stage {

    private final ApplicationScriptService scriptService;

    public SetupStage(ApplicationScriptService scriptService, String errorMessage) {
        this.scriptService = scriptService;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("First-Time Setup");

        VBox layout = new VBox(15); // A little less spacing
        layout.setPadding(new Insets(25));
        layout.setAlignment(Pos.CENTER);

        // --- NEW ERROR MESSAGE DISPLAY ---
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Label errorLabel = new Label(errorMessage);
            errorLabel.setStyle("-fx-text-fill: #D8000C; -fx-font-weight: bold; -fx-background-color: #FFD2D2; -fx-padding: 10; -fx-border-radius: 5; -fx-background-radius: 5;");
            errorLabel.setWrapText(true);
            errorLabel.setMaxWidth(350);
            layout.getChildren().add(errorLabel);
        }

        Label headerLabel = new Label("Database Configuration");
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER);

        TextField dbAddressField = new TextField();
        dbAddressField.setPromptText("e.g., your-db-host.com:12345/dbname");
        TextField userField = new TextField(); // Changed to TextField for easier editing
        userField.setPromptText("Database Username");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Database Password");

        grid.add(new Label("DB Address:"), 0, 0);
        grid.add(dbAddressField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(userField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passField, 1, 2);

        Button saveButton = new Button("Save and Restart");
        saveButton.setOnAction(e -> handleSave(
                dbAddressField.getText(),
                userField.getText(),
                passField.getText()
        ));

        Button h2Button = new Button("Use Temporary Test Database and Restart");
        h2Button.setOnAction(e -> handleH2());

        layout.getChildren().addAll(headerLabel, grid, saveButton, h2Button);
        setScene(new Scene(layout));
    }

    private void handleSave(String dbAddress, String username, String password) {
        if (dbAddress.isEmpty() || username.isEmpty()) {
            new ErrorDialog("Address and Username cannot be empty.", getScene().getRoot()).showAndWait();
            return;
        }
        PropertiesManager.saveDatabaseCredentials(dbAddress, username, password);
        restartApplication();
    }

    private void handleH2() {
        // We don't need to save anything for H2, just restart with the flag.
        restartApplicationWithH2();
    }

    private void restartApplication() {
        scriptService.restart(
                () -> new ErrorDialog("Cannot restart from IDE.", getScene().getRoot()).showAndWait(),
                (err) -> new ErrorDialog("Restart failed: " + err.getMessage(), getScene().getRoot()).showAndWait()
        );
    }

    private void restartApplicationWithH2() {
        scriptService.restartWithH2(
                () -> new ErrorDialog("Cannot restart from IDE.", getScene().getRoot()).showAndWait(),
                (err) -> new ErrorDialog("Restart failed: " + err.getMessage(), getScene().getRoot()).showAndWait()
        );
    }
}

