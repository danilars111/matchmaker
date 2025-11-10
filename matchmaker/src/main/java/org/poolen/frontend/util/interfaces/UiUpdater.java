package org.poolen.frontend.util.interfaces;

/**
 * A more sophisticated updater interface that allows a background task
 * to update the status message and show detailed information in the loading overlay.
 */
public interface UiUpdater {
    /**
     * Updates the primary status label of the loading overlay.
     * @param message The new status message.
     */
    void updateStatus(String message);

    /**
     * Shows the details box in the loading overlay with a custom label and text.
     * @param label The text for the label above the text area.
     * @param details The text to display in the main text area.
     */
    void showDetails(String label, String details, Runnable onCancelAction);
}
