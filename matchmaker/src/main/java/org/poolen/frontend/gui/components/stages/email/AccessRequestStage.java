package org.poolen.frontend.gui.components.stages.email;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A specific implementation of {@link BaseEmailFormStage} for users
 * to request access or elevated permissions.
 */
public class AccessRequestStage extends BaseEmailFormStage {

    private TextArea reasonArea;

    public AccessRequestStage(ConfigurableApplicationContext context) {
        super(context);
    }

    @Override
    protected String getWindowTitle() {
        return "Request Access";
    }

    @Override
    protected double[] getWindowSize() {
        return new double[]{400, 300}; // width, height
    }

    @Override
    protected Node createFormContent() {
        Label header = new Label("Request Access");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label info = new Label("Please explain why you need access.");

        Label reasonLabel = new Label("Reason for request:");
        reasonArea = new TextArea();
        reasonArea.setWrapText(true);
        reasonArea.setPrefHeight(100);
        reasonArea.setPromptText("e.g., 'I am a new admin and need...'");

        VBox contentBox = new VBox(10, header, info, reasonLabel, reasonArea);
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
        String reason = reasonArea.getText();
        if (reason == null || reason.isBlank()) {
            // A little validation!
            throw new Exception("Please provide a reason for your request.");
        }

        String subject = "Access Request - Matchmaker App";
        String body = "A user has submitted an access request.\n\n" +
                "--- REASON ---\n" + reason + "\n";

        // No attachment for this one
        return new EmailDetails(subject, body, null);
    }
}
