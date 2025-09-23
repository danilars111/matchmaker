package org.poolen.frontend.gui;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.web.google.SheetsServiceManager;

/**
 * The main entry point for the application. This class handles the initial
 * Google authentication and data loading before showing the main application window.
 */
public class LoginApplication extends Application {

    private static final String SPREADSHEET_ID = "1YDOjqklvoJOfdV1nvA8IqyPpjqGrCMbP24VCLfC_OrU";

    private Stage primaryStage;
    private VBox root;
    private Label statusLabel;
    private Button loginButton;
    private ProgressIndicator loadingIndicator;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("D&D Matchmaker Deluxe - Login");

        root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #F0F8FF;");

        statusLabel = new Label("Connecting to Google...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);

        loginButton = new Button("Login with Google");
        loginButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4285F4; -fx-text-fill: white; -fx-font-weight: bold;");
        loginButton.setVisible(false); // Initially hidden
        loginButton.setOnAction(e -> attemptLoginAndLoad());

        root.getChildren().addAll(statusLabel, loadingIndicator);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start the automatic login attempt
        attemptLoginAndLoad();
    }

    private void attemptLoginAndLoad() {
        // Show loading state
        statusLabel.setText("Connecting to Google and loading data...");
        if (!root.getChildren().contains(loadingIndicator)) {
            root.getChildren().add(1, loadingIndicator);
        }
        loginButton.setVisible(false);

        Task<Void> loginTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // This all happens on a background thread
                SheetsServiceManager.connect();
                SheetsServiceManager.loadData(SPREADSHEET_ID);
                return null;
            }

            @Override
            protected void succeeded() {
                // This happens on the JavaFX thread if the task was successful
                showManagementStage();
            }

            @Override
            protected void failed() {
                // This happens on the JavaFX thread if the task failed
                Throwable error = getException();
                System.err.println("Failed to connect or load data: " + error.getMessage());
                error.printStackTrace();

                statusLabel.setText("Could not connect. Please log in.");
                root.getChildren().remove(loadingIndicator);
                loginButton.setVisible(true);

                // Show a more user-friendly error dialog
                new ErrorDialog("Failed to connect: " + error.getMessage(), root).showAndWait();
            }
        };

        new Thread(loginTask).start();
    }

    private void showManagementStage() {
        // We're logged in and data is loaded, so show the management stage directly
        primaryStage.close(); // Close the login window
        ManagementStage managementStage = new ManagementStage();
        managementStage.show();
    }
}

