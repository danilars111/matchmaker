package org.poolen.frontend.util.interfaces;

import java.util.function.Consumer;

/**
 * A functional interface for a task that can be executed asynchronously
 * and can report progress updates as string messages.
 *
 * @param <T> The return type of the task.
 */
@FunctionalInterface
public interface ProgressAwareTask<T> {
    /**
     * Executes the task.
     * @param progressUpdater A consumer that can be called to update the UI with a status message.
     * @return The result of the task.
     * @throws Exception if the task fails.
     */
    T execute(Consumer<String> progressUpdater) throws Exception;
}
