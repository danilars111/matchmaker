package org.poolen.frontend.gui.components.stages;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.dialogs.InfoDialog;
import org.poolen.web.google.SheetsServiceManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * A dialog window for exporting generated groups, either as Markdown text
 * or by writing them directly to a Google Sheet.
 */
public class ExportGroupsStage extends Stage {

    private final List<Group> groups;
    private final String spreadsheetId;
    private final VBox loadingBox;
    private final Button writeToSheetButton;
    private final Button closeButton;
    private final TextArea markdownArea;

    public ExportGroupsStage(List<Group> groups, String spreadsheetId, Window owner) {
        this.groups = groups;
        this.spreadsheetId = spreadsheetId;

        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setTitle("Export Groups");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // --- Markdown Text Area ---
        this.markdownArea = new TextArea(generateMarkdown(groups));
        markdownArea.setWrapText(true);
        markdownArea.setEditable(false);
        markdownArea.setStyle("-fx-font-family: 'monospaced';");
        root.setCenter(markdownArea);

        // --- Loading Indicator (initially hidden) ---
        loadingBox = new VBox(10, new Label("Writing to Google Sheets..."), new ProgressIndicator());
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setVisible(false);


        // --- Buttons ---
        writeToSheetButton = new Button("Write to Sheets");
        writeToSheetButton.setStyle("-fx-background-color: #34A853; -fx-text-fill: white; -fx-font-weight: bold;");
        writeToSheetButton.setOnAction(e -> handleWriteToSheet());

        Button copyButton = new Button("Copy to Clipboard");
        Label copyStatusLabel = new Label(); // Our new feedback label!

        copyButton.setOnAction(e -> {
            try {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(markdownArea.getText());
                clipboard.setContent(content);
                copyStatusLabel.setText("Copied!");
                copyStatusLabel.setStyle("-fx-text-fill: green;");
            } catch (Exception ex) {
                copyStatusLabel.setText("Failed to copy.");
                copyStatusLabel.setStyle("-fx-text-fill: red;");
            }
            // Make the label fade away after a couple of seconds for a tidy look.
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> copyStatusLabel.setText(""));
            pause.play();
        });

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBox = new HBox(10, copyButton, copyStatusLabel, spacer, writeToSheetButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(buttonBox, new Insets(15, 0, 0, 0));
        root.setBottom(buttonBox);

        Scene scene = new Scene(root, 600, 450);
        setScene(scene);
    }

    private void handleWriteToSheet() {
        // Show loading state and disable buttons
        BorderPane rootPane = (BorderPane) getScene().getRoot();
        rootPane.setCenter(loadingBox);
        loadingBox.setVisible(true);
        writeToSheetButton.setDisable(true);
        closeButton.setDisable(true);

        for(Group group : groups) {
            for(Player player : group.getParty().values()) {
                player.updatePlayerLog(group);
                player.setLastSeen(LocalDate.now());
            }
        }

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // First, save the current state of all players (persistence).
                SheetsServiceManager.saveData(spreadsheetId);
                // Then, append the new groups to the announcement sheet.
                SheetsServiceManager.appendGroupsToSheet(spreadsheetId, groups);
                return null;
            }

            @Override
            protected void succeeded() {
                new InfoDialog("Groups have been written to the sheet.", getScene().getRoot()).showAndWait();
                close();
            }

            @Override
            protected void failed() {
                new ErrorDialog("Failed to write to sheet: " + getException().getMessage(), getScene().getRoot()).showAndWait();
                // Restore the original view
                rootPane.setCenter(new TextArea(generateMarkdown(groups))); // Recreate to be safe
                loadingBox.setVisible(false);
                writeToSheetButton.setDisable(false);
                closeButton.setDisable(false);
            }
        };

        new Thread(exportTask).start();
    }

    private String generateMarkdown(List<Group> groups) {
        if (groups == null || groups.isEmpty()) {
            return "No groups to export.";
        }

        StringBuilder markdownBuilder = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM, yyyy");
        LocalDate eventDate = groups.get(0).getDate();
        markdownBuilder.append("# ").append(eventDate.format(formatter)).append("\n\n");

        List<Group> sortedGroups = groups.stream()
                .sorted(Comparator.comparing(g -> g.getDungeonMaster() != null ? g.getDungeonMaster().getName() : "", String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (Group group : sortedGroups) {
            markdownBuilder.append(group.toMarkdown()).append("\n---\n\n");
        }
        return markdownBuilder.toString();
    }
}

