package org.poolen.frontend.gui.components.stages.email;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * EXAMPLE implementation for a user-submitted Bug Report.
 * This one doesn't have a stack trace or log file, just user input.
 */
public class BugReportStage extends BaseEmailFormStage {

    private TextArea stepsArea;
    private TextArea expectedArea;
    private TextArea actualArea;

    public BugReportStage(ConfigurableApplicationContext context) {
        super(context);
    }

    @Override
    protected String getWindowTitle() {
        return "Submit Bug Report";
    }

    @Override
    protected double[] getWindowSize() {
        return new double[]{450, 450}; // width, height
    }

    @Override
    protected Node createFormContent() {
        Label header = new Label("Report a Bug");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label info = new Label("Please describe the bug in as much detail as possible.");

        Label stepsLabel = new Label("Steps to Reproduce:");
        stepsArea = new TextArea();
        stepsArea.setWrapText(true);
        stepsArea.setPrefHeight(100);

        Label expectedLabel = new Label("What did you expect to happen?");
        expectedArea = new TextArea();
        expectedArea.setWrapText(true);
        expectedArea.setPrefHeight(60);

        Label actualLabel = new Label("What *actually* happened?");
        actualArea = new TextArea();
        actualArea.setWrapText(true);
        actualArea.setPrefHeight(60);

        VBox contentBox = new VBox(10, header, info, stepsLabel, stepsArea, expectedLabel, expectedArea, actualLabel, actualArea);
        contentBox.setPadding(new Insets(10, 0, 10, 0));
        return contentBox;
    }

    @Override
    protected String getTaskTitle() {
        return "Submitting bug report...";
    }

    @Override
    protected String getSuccessMessage() {
        return "Bug report sent. Thank you!";
    }

    @Override
    protected EmailDetails buildEmailDetails() throws Exception {
        String steps = stepsArea.getText();
        if (steps == null || steps.isBlank()) {
            // We can do validation!
            throw new Exception("Please describe the steps to reproduce.");
        }

        String expected = expectedArea.getText();
        String actual = actualArea.getText();

        String subject = "Bug Report - Matchmaker App";
        String body = "A user has submitted a bug report.\n\n" +
                "--- STEPS TO REPRODUCE ---\n" + steps + "\n\n" +
                "--- EXPECTED BEHAVIOUR ---\n" + expected + "\n\n" +
                "--- ACTUAL BEHAVIOUR ---\n" + actual + "\n";

        // No attachment for this one!
        return new EmailDetails(subject, body, null);
    }
}
