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
import org.poolen.web.google.GoogleAuthManager;
import org.poolen.web.google.SheetsServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    private static final Logger logger = LoggerFactory.getLogger(ExportGroupsStage.class);

    private final SettingsStore settingsStore;
    private List<Group> groups;
    private final VBox loadingBox; // We create this in the constructor now, darling!
    private final Button writeToSheetButton;
    private final Button postToDiscordButton;
    private final Button closeButton;
    private final TextArea markdownArea;
    private final SheetsServiceManager sheetsServiceManager;
    private final GoogleAuthManager authManager;
    private Window owner; // This will be set in init()
    private final CoreProvider coreProvider;
    private final HBox buttonBox;
    private final Label copyStatusLabel;

    public ExportGroupsStage(CoreProvider coreProvider, SheetsServiceManager sheetsServiceManager,
                             SettingStoreProvider storeProvider, GoogleAuthManager authManager) {
        this.settingsStore = storeProvider.getSettingsStore();
        this.sheetsServiceManager = sheetsServiceManager;
        this.coreProvider = coreProvider;
        this.authManager = authManager;

        // We create them all *once* in the constructor so they are never null!
        logger.debug("ExportGroupsStage constructor: Initialising UI components.");

        // --- Markdown Text Area ---
        this.markdownArea = new TextArea();
        markdownArea.setWrapText(true);
        markdownArea.setEditable(false);
        markdownArea.setStyle("-fx-font-family: 'monospaced';");

        // --- Loading Indicator (initially hidden) ---
        // We'll add the label in the runTask method!
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
        copyStatusLabel = new Label(); // We need to see this later!

        copyButton.setOnAction(e -> {
            try {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(markdownArea.getText());
                clipboard.setContent(content);
                copyStatusLabel.setText("Copied!");
                copyStatusLabel.setStyle("-fx-text-fill: green;");
                logger.info("User copied generated markdown to clipboard.");
            } catch (Exception ex) {
                copyStatusLabel.setText("Failed to copy.");
                copyStatusLabel.setStyle("-fx-text-fill: red;");
                logger.error("Failed to copy markdown to clipboard.", ex);
            }
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> copyStatusLabel.setText(""));
            pause.play();
        });

        closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buttonBox = new HBox(10, copyButton, copyStatusLabel, spacer, postToDiscordButton, writeToSheetButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(buttonBox, new Insets(15, 0, 0, 0));
        // --- All components are now ready! ---
    }

    public void init(List<Group> groups, Window owner) {
        this.groups = groups;
        this.owner = owner;
        // We only call these *one time* setup methods if the owner hasn't been set yet!
        if (getOwner() == null) {
            logger.debug("First-time init. Setting modality and owner.");
            initModality(Modality.WINDOW_MODAL);
            initOwner(owner);
        } else {
            logger.debug("Re-running init. Modality and owner are already set.");
        }
    }
    public void start() {
        if(groups == null || owner == null) {
            String errorMsg = String.format("%s has not been initialized correctly.", this.getClass().getSimpleName());
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        logger.info("Initialising and showing ExportGroupsStage for {} groups.", groups.size());

        // We only build the UI *once*, the very first time!
        if (getScene() == null) {
            logger.debug("Scene is null. Assembling ExportGroupsStage UI for the first time.");
            setTitle("Export Groups");

            // We just *use* our components, we don't create them here!
            BorderPane root = new BorderPane();
            root.setPadding(new Insets(15));
            root.setCenter(markdownArea);
            root.setBottom(buttonBox);

            Scene scene = new Scene(root, 700, 450);
            setScene(scene);
            logger.debug("ExportGroupsStage UI constructed and scene is set.");
        }
        writeToSheetButton.setDisable(authManager.loadAndValidateStoredCredential() == null);

        // This makes sure that if we re-open, the text is new
        // and we're not stuck on the "loading" screen!
        logger.debug("Refreshing content and resetting view for stage.");
        markdownArea.setText(generateMarkdown(groups));
        copyStatusLabel.setText(""); // Reset the "Copied!" label
        restoreOriginalView();
    }

    private void handlePostToDiscord() {
        logger.info("User initiated 'Post to Discord' action.");
        String webhookUrl = (String) settingsStore.getSetting(DISCORD_WEB_HOOK).getSettingValue();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            logger.warn("Discord post aborted: Webhook URL is not configured in settings.");
            coreProvider.createDialog(DialogType.ERROR,"Please set a Discord Webhook URL in the Settings tab first, darling.", getScene().getRoot()).showAndWait();
            return;
        }

        String markdown = markdownArea.getText();
        logger.debug("Webhook URL is present. Starting background task to post markdown.");
        runTask("Posting to Discord...", () -> {
            DiscordWebhookManager.sendAnnouncement(webhookUrl, markdown);
            return "Announcement successfully posted to Discord!";
        });
    }


    private void handleWriteToSheet() {
        logger.info("User initiated 'Write to Sheets' action.");
        String spreadsheetId = (String) settingsStore.getSetting(SHEETS_ID).getSettingValue();
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            logger.warn("Write to Sheets aborted: Spreadsheet ID is not configured in settings.");
            coreProvider.createDialog(DialogType.ERROR, "Please set a Google Sheets ID in the Settings tab first.", getScene().getRoot()).showAndWait();
            return;
        }

        updatePlayerLogs();

        logger.debug("Spreadsheet ID is present. Starting background task to write groups.");
        runTask("Writing to Google Sheets...", () -> {
            sheetsServiceManager.appendGroupsToSheet(spreadsheetId, groups);
            return "Groups have been written to the sheet.";
        });
    }

    private void runTask(String loadingMessage, TaskOperation operation) {
        logger.info("Starting background task: '{}'", loadingMessage);
        BorderPane rootPane = (BorderPane) getScene().getRoot();
        // The loadingBox now needs a Label.
        Label loadingLabel = new Label(loadingMessage);
        loadingBox.getChildren().setAll(loadingLabel, new ProgressIndicator()); // This is safer!
        rootPane.setCenter(loadingBox);
        loadingBox.setVisible(true);
        setAllButtonsDisabled(true);

        Task<String> exportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                logger.debug("Executing background operation on thread: {}", Thread.currentThread().getName());
                return operation.execute();
            }

            @Override
            protected void succeeded() {
                logger.info("Background task succeeded. Result: '{}'", getValue());
                coreProvider.createDialog(DialogType.INFO, getValue(), getScene().getRoot()).showAndWait();
                restoreOriginalView();
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                logger.error("Background task failed with an exception.", ex);
                coreProvider.createDialog(DialogType.ERROR,"Operation failed: " + ex.getMessage(), getScene().getRoot()).showAndWait();
                restoreOriginalView();
            }
        };
        new Thread(exportTask).start();
    }

    private void restoreOriginalView() {
        // We need to check if the scene is null, just in case!
        if (getScene() == null || getScene().getRoot() == null) {
            logger.debug("restoreOriginalView called, but scene/root is null. Skipping.");
            return;
        }
        logger.debug("Restoring original view after task completion.");
        BorderPane rootPane = (BorderPane) getScene().getRoot();
        rootPane.setCenter(markdownArea);
        loadingBox.setVisible(false);
        setAllButtonsDisabled(false);
    }

    private void setAllButtonsDisabled(boolean disabled) {
        postToDiscordButton.setDisable(disabled);
        closeButton.setDisable(disabled);
    }

    private void updatePlayerLogs() {
        logger.info("Updating player logs for {} groups before export.", groups.size());
        groups.forEach(group ->
                group.getParty().values().forEach(player ->
                        player.updatePlayerLog(group)
                )
        );
        logger.debug("Player log updates complete.");
    }

    private String generateMarkdown(List<Group> groups) {
        if (groups == null || groups.isEmpty()) {
            logger.warn("generateMarkdown called with no groups. Returning placeholder text.");
            return "No groups to export.";
        }
        logger.debug("Generating markdown for {} groups.", groups.size());

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
        logger.debug("Markdown generation complete.");
        return markdownBuilder.toString();
    }

    @FunctionalInterface
    private interface TaskOperation {
        String execute() throws Exception;
    }
}
