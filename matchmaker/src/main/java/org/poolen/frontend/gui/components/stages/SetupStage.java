package org.poolen.frontend.gui.components.stages;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.util.PropertiesManager;

/**
 * A dialog window for the initial setup of the database connection.
 * This window is shown on the first run when no properties file is found.
 */
public class SetupStage extends Stage {

    public SetupStage() {
        setTitle("First-Time Setup");
        setResizable(false);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 30; -fx-background-color: #F0F8FF;");

        Label titleLabel = new Label("Database Configuration");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label infoLabel = new Label("Please enter your MySQL database connection details.");
        infoLabel.setWrapText(true);

        // --- Form Fields ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("DB Address:"), 0, 0);
        TextField dbAddressField = new TextField();
        dbAddressField.setPromptText("hostname:port/database?ssl-mode=REQUIRED");
        grid.add(dbAddressField, 1, 0);

        grid.add(new Label("Username:"), 0, 1);
        TextField usernameField = new TextField();
        grid.add(usernameField, 1, 1);

        grid.add(new Label("Password:"), 0, 2);
        PasswordField passwordField = new PasswordField();
        grid.add(passwordField, 1, 2);


        // --- Buttons ---
        Button saveButton = new Button("Save and Exit");
        saveButton.setOnAction(e -> {
            String dbAddress = dbAddressField.getText();
            String username = usernameField.getText();
            String password = passwordField.getText();

            if (dbAddress.isBlank() || username.isBlank()) {
                new ErrorDialog("Address and Username cannot be empty.", root).showAndWait();
                return;
            }

            PropertiesManager.saveDatabaseCredentials(dbAddress, username, password);
            new InfoDialog("Configuration saved! Please restart the application to continue.", root).showAndWait();
            Platform.exit();
        });

        Button h2Button = new Button("Use H2 (Test Mode)");
        h2Button.setOnAction(e -> {
            new InfoDialog("To use the in-memory test database, please restart the application with the --h2 flag.", root).showAndWait();
            Platform.exit();
        });

        HBox buttonBox = new HBox(15, h2Button, saveButton);
        buttonBox.setAlignment(Pos.CENTER);

        root.getChildren().addAll(titleLabel, infoLabel, grid, buttonBox);

        Scene scene = new Scene(root, 450, 350);
        setScene(scene);
    }
}
