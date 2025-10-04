package org.poolen.frontend.util.services;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;
import org.poolen.frontend.gui.components.dialogs.ErrorDialog;
import org.poolen.frontend.gui.components.overlays.LoadingOverlay;
import org.poolen.frontend.util.interfaces.ProgressAwareTask;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * A Spring service for executing long-running tasks on a background thread
 * while displaying a modal loading overlay on the UI thread.
 */
@Service
public class UiTaskExecutor {

    /**
     * The primary execution logic. This version is more flexible to allow for an optional explicit success message.
     */
    private <T> void executeInternal(Window owner, String initialMessage, String successMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        if (owner == null || owner.getScene() == null) {
            System.err.println("Cannot execute task: Owner window or scene is null.");
            onError.accept(new IllegalStateException("Task owner or scene is null."));
            return;
        }

        final Scene scene = owner.getScene();
        final LoadingOverlay overlay = new LoadingOverlay();

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.execute(this::updateMessage);
            }

            @Override
            protected void succeeded() {
                T result = getValue();
                overlay.statusProperty().unbind();

                String finalMessage = (successMessage != null) ? successMessage : (result != null ? result.toString() : "Success!");

                // This method starts an animation that hides the overlay after a delay.
                overlay.showSuccessAndThenHide(scene, finalMessage);

                // We must delay calling the next piece of logic until after that animation is complete.
                // This prevents the next task from immediately showing a *new* overlay and trapping the old one.
                // The delay in LoadingOverlay is 800ms, so we wait just a moment longer to be safe.
                PauseTransition successCallbackDelay = new PauseTransition(Duration.millis(850));
                successCallbackDelay.setOnFinished(event -> onSuccess.accept(result));
                successCallbackDelay.play();
            }

            @Override
            protected void failed() {
                overlay.statusProperty().unbind();
                overlay.hide(scene);
                onError.accept(getException());
            }
        };

        try {
            overlay.show(scene, initialMessage);
            overlay.statusProperty().bind(task.messageProperty());
            new Thread(task).start();
        } catch (Exception e) {
            System.err.println("Failed to set up and start the UI task.");
            e.printStackTrace();
            overlay.hide(scene);
            onError.accept(e);
        }
    }

    /**
     * Executes a task, providing an explicit, friendly success message.
     * This is the preferred method for new code.
     */
    public <T> void execute(Window owner, String initialMessage, String successMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executeInternal(owner, initialMessage, successMessage, work, onSuccess, onError);
    }

    /**
     * Overloaded version for backward compatibility. The success message is derived from the task's result.
     */
    public <T> void execute(Window owner, String initialMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        // We pass 'null' for the success message, telling our internal method to use the task's result instead.
        executeInternal(owner, initialMessage, null, work, onSuccess, onError);
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

    /**
     * An overloaded version for backward compatibility that uses a default error handler.
     */
    public <T> void execute(Window owner, String initialMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess) {
        execute(owner, initialMessage, work, onSuccess,
                (error) -> {
                    error.printStackTrace();
                    new ErrorDialog("An unexpected error occurred: " + error.getMessage(), owner.getScene().getRoot()).showAndWait();
                }
        );
    }
}

