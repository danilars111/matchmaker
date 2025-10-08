package org.poolen.frontend.gui.components.stages;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
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
import org.poolen.backend.db.interfaces.store.SettingStoreProvider;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.frontend.gui.components.dialogs.BaseDialog.DialogType;
import org.poolen.frontend.util.interfaces.providers.CoreProvider;
import org.poolen.web.discord.DiscordWebhookManager;
import org.poolen.web.google.SheetsServiceManager;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import static org.poolen.backend.db.constants.Settings.PersistenceSettings.DISCORD_WEB_HOOK;
import static org.poolen.backend.db.constants.Settings.PersistenceSettings.SHEETS_ID;

/**
 * A dialog window for exporting generated groups, either as Markdown text
 * or by writing them directly to a Google Sheet.
 */
public class ExportGroupsStage extends Stage {

    private final SettingsStore settingsStore;
    private List<Group> groups;
    private VBox loadingBox;
    private Button writeToSheetButton;
    private Button postToDiscordButton;
    private Button closeButton;
    private TextArea markdownArea;
    private final SheetsServiceManager sheetsServiceManager;
    private Window owner;
    private final CoreProvider coreProvider;

    public ExportGroupsStage(CoreProvider coreProvider, SheetsServiceManager sheetsServiceManager, SettingStoreProvider storeProvider) {
        this.settingsStore = storeProvider.getSettingsStore();
        this.sheetsServiceManager = sheetsServiceManager;
        this.coreProvider = coreProvider;
    }
    public void init(List<Group> groups, Window owner) {
        this.groups = groups;
        this.owner = owner;
    }
    public void start() {
        if(groups == null || owner == null) {
            throw new IllegalStateException("%s has not been initialized".formatted(this.getClass().getSimpleName()));
        }

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
        loadingBox = new VBox(10, new ProgressIndicator());
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setVisible(false);


        // --- Buttons ---
        writeToSheetButton = new Button("Write to Sheets");
        writeToSheetButton.setStyle("-fx-background-color: #34A853; -fx-text-fill: white; -fx-font-weight: bold;");
        writeToSheetButton.setOnAction(e -> handleWriteToSheet());

        postToDiscordButton = new Button("Post to Discord");
        postToDiscordButton.setStyle("-fx-background-color: #5865F2; -fx-text-fill: white; -fx-font-weight: bold;");
        postToDiscordButton.setOnAction(e -> handlePostToDiscord());

        Button copyButton = new Button("Copy to Clipboard");
        Label copyStatusLabel = new Label();

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
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> copyStatusLabel.setText(""));
            pause.play();
        });

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBox = new HBox(10, copyButton, copyStatusLabel, spacer, postToDiscordButton, writeToSheetButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(buttonBox, new Insets(15, 0, 0, 0));
        root.setBottom(buttonBox);

        Scene scene = new Scene(root, 700, 450);
        setScene(scene);
    }

    private void handlePostToDiscord() {
        String webhookUrl = (String) settingsStore.getSetting(DISCORD_WEB_HOOK).getSettingValue();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            coreProvider.createDialog(DialogType.ERROR,"Please set a Discord Webhook URL in the Settings tab first, darling.", getScene().getRoot()).showAndWait();
            return;
        }

        String markdown = markdownArea.getText();

        runTask("Posting to Discord...", () -> {
            DiscordWebhookManager.sendAnnouncement(webhookUrl, markdown);
            return "Announcement successfully posted to Discord!";
        });
    }


    private void handleWriteToSheet() {
        String spreadsheetId = (String) settingsStore.getSetting(SHEETS_ID).getSettingValue();

        updatePlayerLogs();

        runTask("Writing to Google Sheets...", () -> {
            //sheetsServiceManager.saveData(spreadsheetId);
            sheetsServiceManager.appendGroupsToSheet(spreadsheetId, groups);
            return "Groups have been written to the sheet.";
        });
    }

    private void runTask(String loadingMessage, TaskOperation operation) {
        BorderPane rootPane = (BorderPane) getScene().getRoot();
        // The loadingBox now needs a Label.
        Label loadingLabel = new Label(loadingMessage);
        loadingBox.getChildren().setAll(loadingLabel, new ProgressIndicator());
        rootPane.setCenter(loadingBox);
        loadingBox.setVisible(true);
        setAllButtonsDisabled(true);

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return operation.execute();
            }

            @Override
            protected void succeeded() {
                coreProvider.createDialog(DialogType.INFO, getValue(), getScene().getRoot()).showAndWait();
                restoreOriginalView();
            }

            @Override
            protected void failed() {
                coreProvider.createDialog(DialogType.ERROR,"Operation failed: " + getException().getMessage(), getScene().getRoot()).showAndWait();
                getException().printStackTrace();
                restoreOriginalView();
            }
        };
        new Thread(exportTask).start();
    }

    private void restoreOriginalView() {
        BorderPane rootPane = (BorderPane) getScene().getRoot();
        rootPane.setCenter(markdownArea);
        loadingBox.setVisible(false);
        setAllButtonsDisabled(false);
    }

    private void setAllButtonsDisabled(boolean disabled) {
        writeToSheetButton.setDisable(disabled);
        postToDiscordButton.setDisable(disabled);
        closeButton.setDisable(disabled);
    }

    private void updatePlayerLogs() {
        groups.forEach(group ->
                group.getParty().values().forEach(player ->
                        player.updatePlayerLog(group)
                )
        );
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
            markdownBuilder.append(group.toMarkdown()).append("\n\n");
        }
        return markdownBuilder.toString();
    }

    @FunctionalInterface
    private interface TaskOperation {
        String execute() throws Exception;
    }
}

