package org.poolen.frontend.gui.components.overlays;

import javafx.animation.PauseTransition;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * A reusable UI component that displays a loading overlay on top of a given scene.
 */
public class LoadingOverlay {

    private final StackPane overlayPane;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;
    private final Path checkMark;
    private Parent originalRoot;

    public LoadingOverlay() {
        // --- Create the visual components ---
        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setTextAlignment(TextAlignment.CENTER);
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2F4F4F;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        // A gorgeous little checkmark for our success state!
        checkMark = new Path();
        checkMark.getElements().addAll(new MoveTo(10, 25), new LineTo(20, 35), new LineTo(40, 15));
        checkMark.setStroke(Color.web("#34A853"));
        checkMark.setStrokeWidth(5);
        checkMark.setVisible(false); // Initially hidden

        // A StackPane to hold either the progress indicator or the checkmark
        StackPane iconPane = new StackPane(progressIndicator, checkMark);
        iconPane.setPrefSize(50, 50);
        iconPane.setAlignment(Pos.CENTER);

        VBox loadingBox = new VBox(20, statusLabel, iconPane);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefSize(300, 200);
        loadingBox.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        loadingBox.setStyle("-fx-background-color: #F2F3F4; -fx-padding: 30; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.5, 0.0, 0.0);");

        overlayPane = new StackPane(loadingBox);
        overlayPane.setAlignment(Pos.CENTER);
        overlayPane.setStyle("-fx-background-color: rgba(245, 245, 245, 0.50);");
    }

    public void show(Scene scene, String initialMessage) {
        if (scene == null) return;

        // Reset the state every time it's shown
        progressIndicator.setVisible(true);
        checkMark.setVisible(false);
        statusLabel.setText(initialMessage);

        this.originalRoot = scene.getRoot();
        StackPane newRoot = new StackPane(originalRoot, overlayPane);
        scene.setRoot(newRoot);
    }

    public void hide(Scene scene) {
        if (scene == null || originalRoot == null) return;

        if (scene.getRoot() instanceof StackPane) {
            StackPane currentRoot = (StackPane) scene.getRoot();
            if (currentRoot.getChildren().contains(originalRoot)) {
                currentRoot.getChildren().clear();
                scene.setRoot(originalRoot);
            }
        }
    }

    /**
     * Shows a success state for a brief moment and then hides the overlay.
     * @param scene The scene to hide from.
     * @param successMessage The final message to display.
     */
    public void showSuccessAndThenHide(Scene scene, String successMessage) {
        if (scene == null) return;

        statusLabel.setText(successMessage);
        progressIndicator.setVisible(false);
        checkMark.setVisible(true);

        PauseTransition delay = new PauseTransition(Duration.millis(500));
        delay.setOnFinished(event -> hide(scene));
        delay.play();
    }

    public StringProperty statusProperty() {
        return statusLabel.textProperty();
    }
}

