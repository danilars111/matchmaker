package org.poolen.frontend.gui.components.overlays;

import javafx.animation.PauseTransition;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reusable UI component that displays a loading overlay on top of a given scene.
 */
public class LoadingOverlay {

    private static final Logger logger = LoggerFactory.getLogger(LoadingOverlay.class);

    private final StackPane overlayPane;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;
    private final Path checkMark;
    private Parent originalRoot;

    // Our fabulous and now much more flexible home for details!
    private final VBox detailsBox;
    private final Label detailsLabel; // It now has its own field!
    private final TextArea detailsText;
    private final Button copyButton;

    public LoadingOverlay() {
        // --- Create the visual components ---
        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setTextAlignment(TextAlignment.CENTER);
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        checkMark = new Path();
        checkMark.getElements().addAll(new MoveTo(10, 25), new LineTo(20, 35), new LineTo(40, 15));
        checkMark.setStroke(Color.web("#34A853"));
        checkMark.setStrokeWidth(5);
        checkMark.setVisible(false);

        StackPane iconPane = new StackPane(progressIndicator, checkMark);
        iconPane.setPrefSize(50, 50);
        iconPane.setAlignment(Pos.CENTER);

        // --- Our Beautiful and Flexible New Details Box ---
        detailsBox = new VBox(5);
        detailsBox.setAlignment(Pos.CENTER);
        detailsLabel = new Label(); // The label is now created without a hardcoded message!
        detailsLabel.setStyle("-fx-font-size: 12px;");
        detailsText = new TextArea();
        detailsText.setEditable(false);
        detailsText.setWrapText(true);
        detailsText.setPrefHeight(80);
        copyButton = new Button("Copy to Clipboard");
        copyButton.setOnAction(e -> {
            logger.info("User copied details to clipboard.");
            Clipboard.getSystemClipboard().setContent(new ClipboardContent(){{putString(detailsText.getText());}});
            copyButton.setText("Copied!");
        });
        detailsBox.getChildren().addAll(detailsLabel, detailsText, copyButton);
        detailsBox.setVisible(false);

        VBox loadingBox = new VBox(20, statusLabel, iconPane, detailsBox);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefSize(350, 300);
        loadingBox.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        loadingBox.setStyle("-fx-background-color: #F2F3F4; -fx-padding: 30; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.5, 0.0, 0.0);");

        overlayPane = new StackPane(loadingBox);
        overlayPane.setAlignment(Pos.CENTER);
        overlayPane.setStyle("-fx-background-color: rgba(245, 245, 245, 0.50);");
    }

    public void show(Scene scene, String initialMessage) {
        if (scene == null) {
            logger.warn("Attempted to show loading overlay on a null scene. Aborting.");
            return;
        }
        logger.info("Displaying loading overlay with message: '{}'", initialMessage);

        progressIndicator.setVisible(true);
        checkMark.setVisible(false);
        detailsBox.setVisible(false);
        copyButton.setText("Copy to Clipboard");
        statusLabel.setText(initialMessage);

        this.originalRoot = scene.getRoot();
        StackPane newRoot = new StackPane(originalRoot, overlayPane);
        scene.setRoot(newRoot);
        logger.debug("Overlay is now visible and has replaced the original scene root.");
    }

    public void hide(Scene scene) {
        if (scene == null || originalRoot == null) {
            logger.warn("Attempted to hide loading overlay, but scene or originalRoot was null. Cannot restore original scene root.");
            return;
        }

        if (scene.getRoot() instanceof StackPane) {
            StackPane currentRoot = (StackPane) scene.getRoot();
            if (currentRoot.getChildren().contains(originalRoot)) {
                currentRoot.getChildren().clear();
                scene.setRoot(originalRoot);
                logger.info("Loading overlay has been hidden and the original scene root restored.");
            } else {
                logger.warn("Attempted to hide overlay, but the current root did not contain the original root. This may indicate an unexpected UI state.");
            }
        } else {
            logger.warn("Attempted to hide overlay, but the current scene root is not the expected StackPane. Cannot restore original scene root.");
        }
    }

    public void showSuccessAndThenHide(Scene scene, String successMessage) {
        if (scene == null) {
            logger.warn("Attempted to show success message on a null scene. Aborting.");
            return;
        }
        logger.info("Displaying success message: '{}', overlay will hide shortly.", successMessage);

        statusLabel.setText(successMessage);
        progressIndicator.setVisible(false);
        detailsBox.setVisible(false);
        checkMark.setVisible(true);

        PauseTransition delay = new PauseTransition(Duration.millis(500));
        delay.setOnFinished(event -> hide(scene));
        delay.play();
    }

    /**
     * A lovely new method to show our details box with a custom label!
     * @param label The text for the label above the text area.
     * @param details The text to display in the main text area.
     */
    public void showDetails(String label, String details) {
        logger.info("Displaying details box with label: '{}'", label);
        detailsLabel.setText(label);
        detailsText.setText(details);
        detailsBox.setVisible(true);
    }

    public StringProperty statusProperty() {
        return statusLabel.textProperty();
    }
}
