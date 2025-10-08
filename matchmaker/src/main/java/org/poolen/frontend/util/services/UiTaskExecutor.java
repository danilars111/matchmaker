package org.poolen.frontend.util.services;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.overlays.LoadingOverlay;
import org.poolen.frontend.util.interfaces.ProgressAwareTask;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * A Spring service for executing long-running tasks on a background thread
 * while displaying a modal loading overlay on the UI thread.
 */
@Service
public class UiTaskExecutor {
    /**
     * The primary execution logic.
     */
    public <T> void execute(Window owner, String initialMessage, String successMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        if (owner == null || owner.getScene() == null) {
            System.err.println("Cannot execute task: Owner window or scene is null.");
            onError.accept(new IllegalStateException("Task owner or scene is null."));
            return;
        }

        final Scene scene = owner.getScene();
        final LoadingOverlay overlay = new LoadingOverlay();

        final UiUpdater updater = new UiUpdater() {
            @Override
            public void updateStatus(String message) {
                Platform.runLater(() -> overlay.statusProperty().set(message));
            }

            @Override
            public void showDetails(String label, String details) {
                Platform.runLater(() -> overlay.showDetails(label, details));
            }
        };

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.execute(updater);
            }

            @Override
            protected void succeeded() {
                T result = getValue();
                String finalMessage = (successMessage != null) ? successMessage : (result != null ? result.toString() : "Success!");
                overlay.showSuccessAndThenHide(scene, finalMessage);
                PauseTransition successCallbackDelay = new PauseTransition(Duration.millis(600));
                successCallbackDelay.setOnFinished(event -> onSuccess.accept(result));
                successCallbackDelay.play();
            }

            @Override
            protected void failed() {
                overlay.hide(scene);
                onError.accept(getException());
            }
        };

        try {
            overlay.show(scene, initialMessage);
            new Thread(task).start();
        } catch (Exception e) {
            System.err.println("Failed to set up and start the UI task.");
            e.printStackTrace();
            overlay.hide(scene);
            onError.accept(e);
        }
    }

    /**
     * An overloaded version that uses a default error handler.
     */
    public <T> void execute(Window owner, String initialMessage, String successMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess) {
        execute(owner, initialMessage, successMessage, work, onSuccess,
                (error) -> {
                    error.printStackTrace();
                    new ErrorDialog("An unexpected error occurred: " + error.getMessage(), owner.getScene().getRoot()).showAndWait();
                }
        );
    }
}

