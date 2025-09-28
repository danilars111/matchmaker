package org.poolen.frontend.util.services;

import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Window;
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
     * Executes a task with a loading overlay and custom success/error handling.
     *
     * @param owner      The owner window, used to find the scene.
     * @param initialMessage The message to display while loading.
     * @param work       The background work to perform, wrapped in a ProgressAwareTask.
     * @param onSuccess  A consumer for the result if the task succeeds.
     * @param onError    A consumer for the exception if the task fails.
     * @param <T>        The type of the result returned by the task.
     */
    public <T> void execute(Window owner, String initialMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        if (owner == null || owner.getScene() == null) {
            System.err.println("Cannot execute task: Owner window or scene is null.");
            // We can also call the onError handler here for consistency.
            onError.accept(new IllegalStateException("Task owner or scene is null."));
            return;
        }

        final Scene scene = owner.getScene();
        final LoadingOverlay overlay = new LoadingOverlay();

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                // The work is done here, on the background thread.
                // The lambda passed in as 'work' is executed.
                return work.execute(this::updateMessage);
            }

            @Override
            protected void succeeded() {
                T result = getValue();
                // We must unbind the property before we can set it manually!
                overlay.statusProperty().unbind();
                // Instead of just hiding, we now call our chic new method!
                // We use result.toString() as a default success message.
                overlay.showSuccessAndThenHide(scene, result != null ? result.toString() : "Success!");
                // We still call the original onSuccess logic right away.
                onSuccess.accept(result);
            }

            @Override
            protected void failed() {
                // It's good practice to unbind here too.
                overlay.statusProperty().unbind();
                overlay.hide(scene); // On failure, just hide it immediately.
                Throwable error = getException();
                onError.accept(error); // Pass the error to the custom handler.
            }
        };

        try {
            // Show the overlay first to set the initial text.
            overlay.show(scene, initialMessage);
            // THEN, bind the overlay's status label to the task's message property for live updates.
            overlay.statusProperty().bind(task.messageProperty());

            // Run the task on a new thread.
            new Thread(task).start();
        } catch (Exception e) {
            // If anything goes wrong during setup, we catch it here!
            System.err.println("Failed to set up and start the UI task.");
            e.printStackTrace();
            // Make sure to clean up the overlay if it was shown.
            overlay.hide(scene);
            // And report the error.
            onError.accept(e);
        }
    }

    /**
     * An overloaded version of execute that uses a default error handler.
     * The default handler shows a standard ErrorDialog.
     */
    public <T> void execute(Window owner, String initialMessage, ProgressAwareTask<T> work, Consumer<T> onSuccess) {
        execute(owner, initialMessage, work, onSuccess,
                // Default error handling logic
                (error) -> {
                    error.printStackTrace();
                    new ErrorDialog("An unexpected error occurred: " + error.getMessage(), owner.getScene().getRoot()).showAndWait();
                }
        );
    }
}

