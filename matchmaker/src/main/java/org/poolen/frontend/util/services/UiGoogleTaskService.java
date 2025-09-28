package org.poolen.frontend.util.services;

import javafx.stage.Window;
import org.poolen.web.google.SheetsServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Lazy
public class UiGoogleTaskService {
    UiTaskExecutor uiTaskExecutor;
    SheetsServiceManager sheetsServiceManager;

    @Autowired
    public UiGoogleTaskService(UiTaskExecutor uiTaskExecutor, SheetsServiceManager sheetsServiceManager) {
        this.uiTaskExecutor = uiTaskExecutor;
        this.sheetsServiceManager = sheetsServiceManager;
    }

    /**
     * A helper method that can be called from a background thread to connect to Google.
     * This is designed to be used within another UiTaskExecutor task.
     * @param progressUpdater The consumer to update the UI message.
     * @throws Exception if the connection fails.
     */
    public void connectToGoogle(Consumer<String> progressUpdater) throws Exception {
        progressUpdater.accept("Attempting to connect...\nPlease check your browser to sign in.");
        sheetsServiceManager.connect();
    }

    public void connect(Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Attempting to connect...\nPlease check your browser to sign in",
                (progressUpdater) -> {
                    connectToGoogle(progressUpdater); // Use our new helper!
                    return "Successfully connected!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }
}
