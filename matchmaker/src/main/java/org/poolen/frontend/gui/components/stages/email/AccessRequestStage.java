package org.poolen.frontend.gui.components.stages.email;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField; // We need this now, darling!
import javafx.scene.layout.VBox;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.regex.Pattern; // For our naughty little email check!

/**
 * A specific implementation of {@link BaseEmailFormStage} for users
 * to request access or elevated permissions.
 * Updated to collect the user's email and a reason!
 */
public class AccessRequestStage extends BaseEmailFormStage {

    // Our new fields, my love!
    private TextField emailField;
    private TextArea reasonArea;

    // A simple little regex to make sure they're not just typing nonsense!
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
            Pattern.CASE_INSENSITIVE
    );

    public AccessRequestStage(ConfigurableApplicationContext context) {
        super(context);
    }

    @Override
    protected String getWindowTitle() {
        return "Request Access";
    }

    @Override
    protected double[] getWindowSize() {
        return new double[]{700, 400};
    }

    @Override
    protected Node createFormContent() {
        Label header = new Label("Request Access");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label info = new Label("This app require manual approval due to Google's security requirements.");

        // --- Our new Email Field ---
        Label emailLabel = new Label("Your Google Account Email:");
        emailField = new TextField();
        emailField.setPromptText("you@gmail.com");

        // --- Our new Reason Field ---
        Label reasonLabel = new Label("Who are you & why do you need access?");
        reasonArea = new TextArea();
        reasonArea.setWrapText(true);
        reasonArea.setPrefHeight(100);
        // Here's our sensible new prompt, my love!
        reasonArea.setPromptText("e.g., 'My name is ... and I require access because...'");

        VBox contentBox = new VBox(10, header, info, emailLabel, emailField, reasonLabel, reasonArea);
        contentBox.setPadding(new Insets(10, 0, 10, 0));
        return contentBox;
    }

    @Override
    protected String getTaskTitle() {
        return "Sending access request...";
    }

    @Override
    protected String getSuccessMessage() {
        return "Access request sent!";
    }

    @Override
    protected EmailDetails buildEmailDetails() throws Exception {
        String email = emailField.getText();
        String reason = reasonArea.getText();

        // --- Our new validation, sweetie! ---
        if (email == null || email.isBlank()) {
            throw new Exception("Please provide your Google email address.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new Exception("Please provide a valid email address.");
        }
        if (reason == null || reason.isBlank()) {
            throw new Exception("Please provide a reason for your request.");
        }
        // --- End of validation ---

        String subject = "Access Request - Matchmaker App";
        String body = "A user has submitted an access request.\n" +
                "https://console.cloud.google.com/auth/audience?project=rollspelspoolen-matchmaker\n\n" +
                "--- USER EMAIL ---\n" + email + "\n\n" +
                "--- REASON ---\n" + reason + "\n";

        // No attachment for this one
        return new EmailDetails(subject, body, null);
    }
}
