package org.poolen.frontend.gui.components.stages.email;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.frontend.util.services.UiTaskExecutor;
import org.poolen.web.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.function.Consumer;

/**
 * A generic, reusable modal stage for sending an email.
 *
 * This abstract class handles the boilerplate UI (Send/Close buttons, status label),
 * the background task execution for sending the mail, and success/error handling.
 *
 * Subclasses must implement methods to provide the specific UI content
 * and the logic for building the email details.
 */
public abstract class BaseEmailFormStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(BaseEmailFormStage.class);

    // The recipient email is now final and shared by all subclasses
    protected static final String RECIPIENT_EMAIL = "daniellarsson054@gmail.com";

    protected final ConfigurableApplicationContext springContext;
    protected final UiTaskExecutor uiTaskExecutor;

    private final Button sendButton;
    private final Button closeButton;
    private final Label statusLabel;
    protected final BorderPane root;

    /**
     * A simple record to hold the email details.
     * This is constructed on the FX thread and used on the background thread.
     */
    protected record EmailDetails(String subject, String body, String attachmentPath) {}

    /**
     * Creates the base email form stage.
     *
     * @param springContext  The Spring application context to get beans from (like EmailService).
     */
    public BaseEmailFormStage(ConfigurableApplicationContext springContext) {
        this.springContext = springContext;
        this.uiTaskExecutor = springContext.getBean(UiTaskExecutor.class);

        initModality(Modality.APPLICATION_MODAL);
        setTitle(getWindowTitle());

        root = new BorderPane();
        root.setPadding(new Insets(15));

        // --- 1. Content (Provided by Subclass) ---
        // We call the abstract method to get the specific form fields.
        root.setCenter(createFormContent());

        // --- 2. Buttons (Handled by this Base Class) ---
        statusLabel = new Label();
        statusLabel.setPadding(new Insets(5, 0, 0, 0));

        sendButton = new Button("Send");
        sendButton.setOnAction(evt -> handleSend());

        closeButton = new Button("Close");
        closeButton.setOnAction(evt -> close());

        HBox buttonBox = new HBox(10, statusLabel, sendButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        root.setBottom(buttonBox);

        // --- 3. Scene Setup ---
        // We call another abstract method for the size.
        double[] dimensions = getWindowSize();
        Scene scene = new Scene(root, dimensions[0], dimensions[1]);
        setScene(scene);
    }

    /**
     * Handles the send button click.
     * This orchestrates the process of getting email details,
     * running the background task, and handling the result.
     */
    private void handleSend() {
        sendButton.setDisable(true);
        closeButton.setDisable(true);
        statusLabel.setText(""); // Overlay will show status

        // --- IMPORTANT ---
        // Get all data from the UI *before* starting the background thread.
        // The subclass is responsible for this logic.
        final EmailDetails details;
        try {
            // This method might throw an exception if validation fails (e.g., empty field)
            details = buildEmailDetails();
        } catch (Exception e) {
            logger.warn("Failed to build email details: {}", e.getMessage());
            statusLabel.setText("Error: " + e.getMessage());
            sendButton.setDisable(false);
            closeButton.setDisable(false);
            return;
        }

        // --- Setup Callbacks ---
        Consumer<Void> onSuccess = (result) -> {
            statusLabel.setText(getSuccessMessage());
            closeButton.setDisable(false);
            // We might want to keep 'send' disabled after a success.
        };

        Consumer<Throwable> onError = (error) -> {
            logger.error("Failed to send email: {}", error.getMessage(), error);
            statusLabel.setText("Error: Could not send. " + error.getMessage());
            closeButton.setDisable(false);
            sendButton.setDisable(false); // Allow user to try again
        };

        // --- Execute Background Task ---
        uiTaskExecutor.execute(
                this, // Owner window
                getTaskTitle(), // "Sending..."
                getSuccessMessage(), // "Sent!"
                (updater) -> {
                    // This logic is now beautifully generic!
                    // It runs on a background thread.
                    EmailService emailService = springContext.getBean(EmailService.class);

                    if (details.attachmentPath() != null && !details.attachmentPath().isBlank()) {
                        logger.info("Sending email with attachment to {}", RECIPIENT_EMAIL);
                        emailService.sendMessageWithAttachment(RECIPIENT_EMAIL, details.subject(), details.body(), details.attachmentPath());
                    } else {
                        logger.info("Sending simple email to {}", RECIPIENT_EMAIL);
                        emailService.sendSimpleMessage(RECIPIENT_EMAIL, details.subject(), details.body());
                    }
                    return null; // Return Void
                },
                onSuccess,
                onError
        );
    }


    // --- Abstract Methods (for subclasses to implement) ---

    /**
     * @return The title to display on the window's title bar.
     */
    protected abstract String getWindowTitle();

    /**
     * @return A (width, height) tuple for the window size.
     */
    protected abstract double[] getWindowSize();

    /**
     * Creates the specific JavaFX content node (e.g., a VBox) that contains
     * the form fields (TextArea, TextField, etc.) for this email form.
     * This is called by the base constructor.
     *
     * @return A {@link Node} to be placed in the center of the window.
     */
    protected abstract Node createFormContent();

    /**
     * @return The text to display on the loading overlay while the task is running.
     */
    protected abstract String getTaskTitle();

    /**
     * @return The text to display in the status label upon successful sending.
     */
    protected abstract String getSuccessMessage();

    /**
     * Gathers all necessary data from the UI fields and constructs the email details.
     * This method is called on the JavaFX Application Thread *before* the background
     * task starts. Subclasses should perform any UI reading or data validation here.
     *
     * @return An {@link EmailDetails} record containing the subject, body, and attachment path.
     * @throws Exception if validation fails (e.g., a required field is empty).
     */
    protected abstract EmailDetails buildEmailDetails() throws Exception;
}
