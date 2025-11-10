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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * A Spring service for executing long-running tasks on a background thread
 * while displaying a modal loading overlay on the UI thread.
 */
@Service
public class UiTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(UiTaskExecutor.class);

    /**
     * The primary execution logic.
     */
    public <T> void execute(Window owner, String initialMessage, String successMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        if (owner == null || owner.getScene() == null) {
            logger.error("Cannot execute task: Owner window or scene is null.");
            onError.accept(new IllegalStateException("Task owner or scene is null."));
            return;
        }

        logger.info("Executing new background task. Initial message: '{}'", initialMessage);
        final Scene scene = owner.getScene();
        final LoadingOverlay overlay = new LoadingOverlay();

        final UiUpdater updater = new UiUpdater() {
            @Override
            public void updateStatus(String message) {
                Platform.runLater(() -> overlay.statusProperty().set(message));
            }

            @Override
            public void showDetails(String label, String details, Runnable onCancelAction) {
                Platform.runLater(() -> overlay.showDetails(label, details, onCancelAction));
            }
        };

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                logger.debug("Background task started on thread: {}", Thread.currentThread().getName());
                return work.execute(updater);
            }

            @Override
            protected void succeeded() {
                T result = getValue();
                logger.info("Background task completed successfully.");
                String finalMessage = (successMessage != null) ? successMessage : (result != null ? result.toString() : "Success!");
                overlay.showSuccessAndThenHide(scene, finalMessage);
                PauseTransition successCallbackDelay = new PauseTransition(Duration.millis(600));
                successCallbackDelay.setOnFinished(event -> onSuccess.accept(result));
                successCallbackDelay.play();
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                logger.error("Background task failed with an exception.", ex);
                overlay.hide(scene);
                onError.accept(ex);
            }
        };

        try {
            overlay.show(scene, initialMessage);
            Thread taskThread = new Thread(task);
            taskThread.setDaemon(true);
            taskThread.start();
        } catch (Exception e) {
            logger.error("Failed to set up and start the UI task.", e);
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
                    logger.error("An unexpected error occurred in task with default handler:", error);
                    new ErrorDialog("An unexpected error occurred: " + error.getMessage(), owner.getScene().getRoot()).showAndWait();
                }
        );
    }
}
