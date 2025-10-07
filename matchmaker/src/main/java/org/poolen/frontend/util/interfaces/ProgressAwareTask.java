package org.poolen.frontend.util.interfaces;

/**
 * A functional interface for a long-running task that is aware of the UI
 * and can provide progress updates.
 *
 * @param <T> The result type of the task.
 */
@FunctionalInterface
public interface ProgressAwareTask<T> {
    /**
     * Executes the long-running operation.
     * @param updater An object that can be used to update the UI overlay.
     * @return The result of the operation.
     * @throws Exception if the operation fails.
     */
    T execute(UiUpdater updater) throws Exception;
}
